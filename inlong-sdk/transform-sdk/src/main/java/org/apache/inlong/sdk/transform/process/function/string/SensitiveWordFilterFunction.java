/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.sdk.transform.process.function.string;

import org.apache.inlong.sdk.transform.decode.SourceData;
import org.apache.inlong.sdk.transform.process.Context;
import org.apache.inlong.sdk.transform.process.function.FunctionConstant;
import org.apache.inlong.sdk.transform.process.function.TransformFunction;
import org.apache.inlong.sdk.transform.process.operator.OperatorTools;
import org.apache.inlong.sdk.transform.process.parser.ValueParser;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * SensitiveWordFilterFunction -> sensitive_word_filter(text, replacement, word1[, word2, ...])
 * description:
 * - Return NULL if 'text' or 'replacement' is NULL
 * - Return 'text' unchanged if no sensitive words are provided
 * - Return the result of replacing all occurrences of sensitive words in 'text' with 'replacement',
 *          using the Aho-Corasick algorithm for efficient multi-pattern matching suitable for long text
 */
@TransformFunction(type = FunctionConstant.STRING_TYPE, names = {
        "sensitive_word_filter"}, parameter = "(String text, String replacement, String word1[, String word2, ...])", descriptions = {
                "- Return NULL if 'text' or 'replacement' is NULL;",
                "- Return 'text' unchanged if no sensitive words are provided;",
                "- Return the result of replacing all occurrences of sensitive words in 'text' with 'replacement', "
                        + "using the Aho-Corasick algorithm for efficient multi-pattern matching suitable for long text."
        }, examples = {
                "sensitive_word_filter('hello world', '***', 'world') = \"hello ***\"",
                "sensitive_word_filter('I love apple and pear', '***', 'apple', 'pear') = \"I love *** and ***\""
        })
public class SensitiveWordFilterFunction implements ValueParser {

    private final ValueParser textParser;
    private final ValueParser replacementParser;
    private final List<ValueParser> wordParsers;

    public SensitiveWordFilterFunction(Function expr) {
        List<Expression> expressions = expr.getParameters().getExpressions();
        textParser = OperatorTools.buildParser(expressions.get(0));
        replacementParser = OperatorTools.buildParser(expressions.get(1));
        wordParsers = new ArrayList<>();
        for (int i = 2; i < expressions.size(); i++) {
            wordParsers.add(OperatorTools.buildParser(expressions.get(i)));
        }
    }

    @Override
    public Object parse(SourceData sourceData, int rowIndex, Context context) {
        Object textObj = textParser.parse(sourceData, rowIndex, context);
        Object replacementObj = replacementParser.parse(sourceData, rowIndex, context);
        if (textObj == null || replacementObj == null) {
            return null;
        }
        String text = OperatorTools.parseString(textObj);
        String replacement = OperatorTools.parseString(replacementObj);
        if (wordParsers.isEmpty()) {
            return text;
        }
        List<String> words = new ArrayList<>();
        for (ValueParser wordParser : wordParsers) {
            Object wordObj = wordParser.parse(sourceData, rowIndex, context);
            if (wordObj != null) {
                String word = OperatorTools.parseString(wordObj);
                if (word != null && !word.isEmpty()) {
                    words.add(word);
                }
            }
        }
        if (words.isEmpty()) {
            return text;
        }
        return new AhoCorasickFilter(words).replace(text, replacement);
    }

    /**
     * Aho-Corasick automaton for efficient multi-pattern string matching.
     * Builds a finite automaton from a set of keyword patterns and scans text in O(n + m + z) time,
     * where n is the text length, m is the total length of all patterns, and z is the number of matches.
     * This approach is particularly efficient for long texts with many keywords.
     */
    static class AhoCorasickFilter {

        /** Trie node: maps character -> child node index */
        private final List<Map<Character, Integer>> children;
        /** Failure (suffix) links for each node */
        private final List<Integer> fail;
        /** Length of longest keyword ending at this node (0 if not a terminal) */
        private final List<Integer> matchLen;

        AhoCorasickFilter(List<String> patterns) {
            children = new ArrayList<>();
            fail = new ArrayList<>();
            matchLen = new ArrayList<>();
            // Add root node
            children.add(new HashMap<>());
            fail.add(0);
            matchLen.add(0);

            for (String pattern : patterns) {
                insert(pattern);
            }
            buildFailLinks();
        }

        private void insert(String word) {
            int cur = 0;
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                int next = children.get(cur).getOrDefault(c, -1);
                if (next == -1) {
                    next = children.size();
                    children.add(new HashMap<>());
                    fail.add(0);
                    matchLen.add(0);
                    children.get(cur).put(c, next);
                }
                cur = next;
            }
            if (matchLen.get(cur) < word.length()) {
                matchLen.set(cur, word.length());
            }
        }

        private void buildFailLinks() {
            Queue<Integer> queue = new LinkedList<>();
            // Initialize fail links for depth-1 nodes (direct children of root)
            for (int child : children.get(0).values()) {
                fail.set(child, 0);
                queue.add(child);
            }
            while (!queue.isEmpty()) {
                int u = queue.poll();
                // Propagate terminal info through suffix links
                if (matchLen.get(u) == 0 && matchLen.get(fail.get(u)) != 0) {
                    matchLen.set(u, matchLen.get(fail.get(u)));
                }
                for (Map.Entry<Character, Integer> entry : children.get(u).entrySet()) {
                    char c = entry.getKey();
                    int v = entry.getValue();
                    // Compute failure link for v: follow fail(u) until we find a node with transition c
                    int f = fail.get(u);
                    while (f != 0 && !children.get(f).containsKey(c)) {
                        f = fail.get(f);
                    }
                    fail.set(v, children.get(f).containsKey(c) && children.get(f).get(c) != v
                            ? children.get(f).get(c)
                            : 0);
                    queue.add(v);
                }
            }
        }

        /**
         * Transitions to the next state from the current state on character c.
         * Follows failure links as needed until a match is found or root is reached.
         */
        private int transition(int cur, char c) {
            while (cur != 0 && !children.get(cur).containsKey(c)) {
                cur = fail.get(cur);
            }
            return children.get(cur).containsKey(c) ? children.get(cur).get(c) : 0;
        }

        /**
         * Replaces all occurrences of sensitive words in text with the replacement string.
         * Uses greedy left-to-right matching: the leftmost match is always selected first,
         * and overlapping matches are skipped.
         */
        String replace(String text, String replacement) {
            // First pass: collect all match end positions with their word lengths using the automaton
            int[] matchLenAtPos = new int[text.length()];
            int cur = 0;
            for (int i = 0; i < text.length(); i++) {
                cur = transition(cur, text.charAt(i));
                matchLenAtPos[i] = matchLen.get(cur);
            }

            // Second pass: greedy left-to-right non-overlapping replacement in O(n)
            StringBuilder result = new StringBuilder();
            int nextStart = 0;
            for (int j = 0; j < text.length(); j++) {
                int len = matchLenAtPos[j];
                int matchStart = j - len + 1;
                if (len > 0 && matchStart >= nextStart) {
                    // Valid non-overlapping match: append text before match, then replacement
                    result.append(text, nextStart, matchStart);
                    result.append(replacement);
                    nextStart = j + 1;
                }
            }
            // Append any remaining text after the last match
            result.append(text, nextStart, text.length());
            return result.toString();
        }
    }
}
