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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * SensitiveWordFilterAcFunction  ->  sensitive_word_filter_ac(text, sensitive_words, replacement)
 * description:
 * - Return NULL if any parameter is null
 * - Return 'text' with each occurrence of every word in 'sensitive_words' replaced by 'replacement',
 *   using the Aho-Corasick multi-pattern string matching algorithm.
 *   'sensitive_words' is a comma-separated list of words to filter.
 * - Suitable for long texts such as articles where many sensitive words need to be detected
 *   in a single pass. For short texts such as comments or nicknames, use
 *   sensitive_word_filter instead.
 */
@TransformFunction(type = FunctionConstant.STRING_TYPE, names = {
        "sensitive_word_filter_ac"}, parameter = "(String text, String sensitive_words, String replacement)", descriptions = {
                "- Return \"\" if any parameter is null;",
                "- Return 'text' with each word in the comma-separated 'sensitive_words' list replaced by "
                        + "'replacement', using Aho-Corasick multi-pattern matching for a single-pass scan. "
                        + "Suitable for long texts such as articles."
        }, examples = {
                "sensitive_word_filter_ac('Hello bad world', 'bad,world', '***') = \"Hello *** ***\"",
                "sensitive_word_filter_ac('abcde', 'ab,bc', '**') = \"**cde\""
        })
public class SensitiveWordFilterAcFunction implements ValueParser {

    private final ValueParser textParser;
    private final ValueParser sensitiveWordsParser;
    private final ValueParser replacementParser;

    public SensitiveWordFilterAcFunction(Function expr) {
        List<Expression> expressions = expr.getParameters().getExpressions();
        textParser = OperatorTools.buildParser(expressions.get(0));
        sensitiveWordsParser = OperatorTools.buildParser(expressions.get(1));
        replacementParser = OperatorTools.buildParser(expressions.get(2));
    }

    @Override
    public Object parse(SourceData sourceData, int rowIndex, Context context) {
        Object textObj = textParser.parse(sourceData, rowIndex, context);
        Object sensitiveWordsObj = sensitiveWordsParser.parse(sourceData, rowIndex, context);
        Object replacementObj = replacementParser.parse(sourceData, rowIndex, context);
        if (textObj == null || sensitiveWordsObj == null || replacementObj == null) {
            return null;
        }
        String text = OperatorTools.parseString(textObj);
        String sensitiveWords = OperatorTools.parseString(sensitiveWordsObj);
        String replacement = OperatorTools.parseString(replacementObj);

        if (sensitiveWords.isEmpty()) {
            return text;
        }

        String[] rawWords = sensitiveWords.split(",");
        List<String> patterns = new ArrayList<>();
        for (String word : rawWords) {
            String trimmed = word.trim();
            if (!trimmed.isEmpty()) {
                patterns.add(trimmed);
            }
        }
        if (patterns.isEmpty()) {
            return text;
        }

        AhoCorasick ac = new AhoCorasick(patterns);
        return ac.replace(text, replacement);
    }

    /**
     * A minimal Aho-Corasick automaton that finds all non-overlapping occurrences of a set of
     * patterns in a text (leftmost-longest match) and replaces them with a given string.
     */
    static final class AhoCorasick {

        /** Each node stores its children, failure link, and the length of the pattern it outputs. */
        private final int[] fail;
        private final int[] output; // length of matched pattern at this node (0 = no match)
        private final Map<Character, Integer>[] children;

        @SuppressWarnings("unchecked")
        AhoCorasick(List<String> patterns) {
            // Build trie
            int maxNodes = 1;
            for (String p : patterns) {
                maxNodes += p.length();
            }
            children = new Map[maxNodes];
            fail = new int[maxNodes];
            output = new int[maxNodes];
            for (int i = 0; i < maxNodes; i++) {
                children[i] = new HashMap<>();
            }

            int nodeCount = 1; // root = 0
            for (String pattern : patterns) {
                int cur = 0;
                for (int i = 0; i < pattern.length(); i++) {
                    char ch = pattern.charAt(i);
                    if (!children[cur].containsKey(ch)) {
                        children[cur].put(ch, nodeCount++);
                    }
                    cur = children[cur].get(ch);
                }
                // Prefer longer patterns at the same node
                if (output[cur] == 0 || pattern.length() > output[cur]) {
                    output[cur] = pattern.length();
                }
            }

            // Build failure links via BFS
            Queue<Integer> queue = new ArrayDeque<>();
            for (int child : children[0].values()) {
                fail[child] = 0;
                queue.add(child);
            }
            while (!queue.isEmpty()) {
                int u = queue.poll();
                // Propagate output through failure links
                if (output[u] == 0 && output[fail[u]] != 0) {
                    output[u] = output[fail[u]];
                }
                for (Map.Entry<Character, Integer> entry : children[u].entrySet()) {
                    char ch = entry.getKey();
                    int v = entry.getValue();
                    int f = fail[u];
                    while (f != 0 && !children[f].containsKey(ch)) {
                        f = fail[f];
                    }
                    fail[v] = children[f].containsKey(ch) && children[f].get(ch) != v
                            ? children[f].get(ch)
                            : 0;
                    queue.add(v);
                }
            }
        }

        /**
         * Replace all non-overlapping sensitive-word occurrences in {@code text} with
         * {@code replacement}, using leftmost-longest matching.
         */
        String replace(String text, String replacement) {
            int n = text.length();
            // matches[i] = length of pattern ending at position i (0 = no match)
            int[] matches = new int[n];
            int cur = 0;
            for (int i = 0; i < n; i++) {
                char ch = text.charAt(i);
                while (cur != 0 && !children[cur].containsKey(ch)) {
                    cur = fail[cur];
                }
                if (children[cur].containsKey(ch)) {
                    cur = children[cur].get(ch);
                }
                if (output[cur] != 0) {
                    matches[i] = output[cur];
                }
            }

            // Build result: scan left-to-right, skip already-covered positions
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (i < n) {
                // Find leftmost match end position at or after i
                int matchEnd = -1;
                int matchLen = 0;
                for (int j = i; j < n; j++) {
                    if (matches[j] != 0 && j - matches[j] + 1 >= i) {
                        matchEnd = j;
                        matchLen = matches[j];
                        break;
                    }
                }
                if (matchEnd == -1) {
                    // No more matches
                    sb.append(text, i, n);
                    break;
                }
                int matchStart = matchEnd - matchLen + 1;
                // Append text before the match
                sb.append(text, i, matchStart);
                // Append replacement
                sb.append(replacement);
                i = matchEnd + 1;
            }
            return sb.toString();
        }

    }
}
