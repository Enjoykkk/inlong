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
 * SensitiveWordFilterFunction -> sensitive_word_filter(text, sensitiveWords[, replacement])
 * description:
 * - Return NULL if 'text' or 'sensitiveWords' is null
 * - Return the result with each character of matched sensitive words replaced by 'replacement'
 * - 'sensitiveWords' is a comma-separated list of words to filter
 * - 'replacement' is a single character and defaults to '*' if not provided
 * - Uses simple string matching for short texts (length <= 200, e.g. nicknames, comments)
 * - Uses Aho-Corasick algorithm for long texts (length > 200, e.g. articles)
 */
@TransformFunction(type = FunctionConstant.STRING_TYPE, names = {
        "sensitive_word_filter"
}, parameter = "(String text, String sensitiveWords, String replacement)", descriptions = {
        "- Return NULL if 'text' or 'sensitiveWords' is null;",
        "- Return the result with each character of matched sensitive words replaced by 'replacement';",
        "- 'sensitiveWords' is a comma-separated list of words to filter;",
        "- 'replacement' is a single character and defaults to '*' if not provided;",
        "- Uses simple string matching for short texts (length <= 200) and "
                + "Aho-Corasick algorithm for long texts (length > 200)."
}, examples = {
        "sensitive_word_filter('Hello World', 'World', '*') = \"Hello *****\"",
        "sensitive_word_filter('bad word here', 'bad,word', '#') = \"### #### here\""
})
public class SensitiveWordFilterFunction implements ValueParser {

    private static final int SHORT_TEXT_THRESHOLD = 200;
    private static final char DEFAULT_REPLACEMENT = '*';

    private ValueParser textParser;
    private ValueParser sensitiveWordsParser;
    private ValueParser replacementParser;

    public SensitiveWordFilterFunction(Function expr) {
        List<Expression> expressions = expr.getParameters().getExpressions();
        textParser = OperatorTools.buildParser(expressions.get(0));
        sensitiveWordsParser = OperatorTools.buildParser(expressions.get(1));
        if (expressions.size() >= 3) {
            replacementParser = OperatorTools.buildParser(expressions.get(2));
        }
    }

    @Override
    public Object parse(SourceData sourceData, int rowIndex, Context context) {
        Object textObj = textParser.parse(sourceData, rowIndex, context);
        Object sensitiveWordsObj = sensitiveWordsParser.parse(sourceData, rowIndex, context);
        if (textObj == null || sensitiveWordsObj == null) {
            return null;
        }
        String text = OperatorTools.parseString(textObj);
        String sensitiveWordsStr = OperatorTools.parseString(sensitiveWordsObj);
        char repChar = DEFAULT_REPLACEMENT;
        if (replacementParser != null) {
            Object repObj = replacementParser.parse(sourceData, rowIndex, context);
            if (repObj != null) {
                String repStr = OperatorTools.parseString(repObj);
                if (!repStr.isEmpty()) {
                    repChar = repStr.charAt(0);
                }
            }
        }
        if (text.isEmpty() || sensitiveWordsStr.isEmpty()) {
            return text;
        }
        List<String> words = new ArrayList<>();
        for (String w : sensitiveWordsStr.split(",")) {
            String trimmed = w.trim();
            if (!trimmed.isEmpty()) {
                words.add(trimmed);
            }
        }
        if (words.isEmpty()) {
            return text;
        }
        if (text.length() <= SHORT_TEXT_THRESHOLD) {
            return filterShortText(text, words, repChar);
        } else {
            return filterLongText(text, words, repChar);
        }
    }

    /**
     * Filters sensitive words from short texts using simple string matching.
     * Time complexity: O(text.length * words.size)
     */
    private static String filterShortText(String text, List<String> words, char repChar) {
        char[] result = text.toCharArray();
        for (String word : words) {
            int index = 0;
            while ((index = text.indexOf(word, index)) != -1) {
                for (int i = index; i < index + word.length(); i++) {
                    result[i] = repChar;
                }
                index += word.length();
            }
        }
        return new String(result);
    }

    /**
     * Filters sensitive words from long texts using the Aho-Corasick algorithm.
     * Time complexity: O(sum(word.length) + text.length)
     */
    private static String filterLongText(String text, List<String> words, char repChar) {
        return new AhoCorasick(words).filter(text, repChar);
    }

    /**
     * Aho-Corasick automaton for efficient multi-pattern string matching.
     * Supports simultaneous search of multiple patterns in a single pass over the text.
     */
    static class AhoCorasick {

        private final List<Map<Character, Integer>> children = new ArrayList<>();
        private final List<Integer> fail = new ArrayList<>();
        // Length of the longest pattern ending at each terminal node (0 if not terminal)
        private final List<Integer> output = new ArrayList<>();
        // Dictionary suffix link: nearest ancestor (via failure chain) that is also a terminal node
        private final List<Integer> dict = new ArrayList<>();

        AhoCorasick(List<String> patterns) {
            // Initialize root node (index 0)
            children.add(new HashMap<>());
            fail.add(0);
            output.add(0);
            dict.add(0);

            // Build trie from all patterns
            for (String pattern : patterns) {
                if (pattern.isEmpty()) {
                    continue;
                }
                int cur = 0;
                for (char c : pattern.toCharArray()) {
                    if (!children.get(cur).containsKey(c)) {
                        int newNode = children.size();
                        children.get(cur).put(c, newNode);
                        children.add(new HashMap<>());
                        fail.add(0);
                        output.add(0);
                        dict.add(0);
                    }
                    cur = children.get(cur).get(c);
                }
                // Store the longest pattern length at each terminal node.
                // Shorter patterns that are suffixes of longer ones are found via dict links,
                // so only the length of the pattern ending directly at this node needs to be stored.
                if (output.get(cur) < pattern.length()) {
                    output.set(cur, pattern.length());
                }
            }

            // Build failure links via BFS
            Queue<Integer> queue = new LinkedList<>();
            for (int child : children.get(0).values()) {
                fail.set(child, 0);
                queue.add(child);
            }
            while (!queue.isEmpty()) {
                int cur = queue.poll();
                for (Map.Entry<Character, Integer> entry : children.get(cur).entrySet()) {
                    char c = entry.getKey();
                    int child = entry.getValue();
                    // Walk up the failure chain to find the longest proper suffix with transition c
                    int f = fail.get(cur);
                    while (f != 0 && !children.get(f).containsKey(c)) {
                        f = fail.get(f);
                    }
                    int failNode = children.get(f).getOrDefault(c, 0);
                    if (failNode == child) {
                        failNode = 0;
                    }
                    fail.set(child, failNode);
                    // Dictionary suffix link points to the nearest terminal via the failure chain
                    if (output.get(failNode) > 0) {
                        dict.set(child, failNode);
                    } else {
                        dict.set(child, dict.get(failNode));
                    }
                    queue.add(child);
                }
            }
        }

        /**
         * Scans the text in a single pass and replaces matched characters with repChar.
         */
        String filter(String text, char repChar) {
            char[] result = text.toCharArray();
            int cur = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                // Follow failure links until a transition on c is found or we reach root
                while (cur != 0 && !children.get(cur).containsKey(c)) {
                    cur = fail.get(cur);
                }
                cur = children.get(cur).getOrDefault(c, 0);
                // Collect all patterns ending at position i via output and dict links
                int state = cur;
                while (state != 0) {
                    int len = output.get(state);
                    if (len > 0) {
                        for (int j = i - len + 1; j <= i; j++) {
                            result[j] = repChar;
                        }
                    }
                    state = dict.get(state);
                }
            }
            return new String(result);
        }
    }
}
