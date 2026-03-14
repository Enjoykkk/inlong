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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * SensitiveWordFilterACFunction  ->  sensitive_word_filter_ac(text, sensitive_words, replacement)
 * description:
 * - Return null if 'text' is null;
 * - Return 'text' unchanged if 'sensitive_words' is null or empty;
 * - Return the result of replacing all sensitive words in 'text' with 'replacement'.
 * - 'sensitive_words' is a comma-separated list of words to be filtered.
 * - This function uses the Aho-Corasick multi-pattern matching algorithm, which scans the text
 *   only once regardless of the number of sensitive words. It is efficient for long text such as
 *   articles or documents where the input may be large.
 * - When multiple patterns overlap, the longest match starting at the earliest position takes
 *   precedence, and overlapping matches are skipped.
 */
@TransformFunction(type = FunctionConstant.STRING_TYPE, names = {
        "sensitive_word_filter_ac"}, parameter = "(String text, String sensitive_words, String replacement)", descriptions = {
                "- Return null if 'text' is null;",
                "- Return 'text' unchanged if 'sensitive_words' is null or empty;",
                "- Return the result of replacing all sensitive words in 'text' with 'replacement'.",
                "- 'sensitive_words' is a comma-separated list of words to be filtered.",
                "- This function uses the Aho-Corasick multi-pattern matching algorithm, suitable for long text "
                        + "such as articles or documents, scanning the text only once for all patterns."
        }, examples = {
                "sensitive_word_filter_ac('Hello bad world', 'bad,evil', '***') = \"Hello *** world\"",
                "sensitive_word_filter_ac('good morning', 'bad,evil', '***') = \"good morning\""
        })
public class SensitiveWordFilterACFunction implements ValueParser {

    private ValueParser textParser;
    private ValueParser sensitiveWordsParser;
    private ValueParser replacementParser;

    /**
     * A node in the Aho-Corasick trie.
     */
    private static class ACNode {

        final Map<Character, ACNode> children = new HashMap<>();
        ACNode fail;
        // Lengths of all patterns that end at this node (including those reachable via fail links)
        final List<Integer> outputs = new ArrayList<>();
    }

    public SensitiveWordFilterACFunction(Function expr) {
        List<Expression> expressions = expr.getParameters().getExpressions();
        textParser = OperatorTools.buildParser(expressions.get(0));
        sensitiveWordsParser = OperatorTools.buildParser(expressions.get(1));
        replacementParser = OperatorTools.buildParser(expressions.get(2));
    }

    @Override
    public Object parse(SourceData sourceData, int rowIndex, Context context) {
        Object textObj = textParser.parse(sourceData, rowIndex, context);
        if (textObj == null) {
            return null;
        }
        String text = OperatorTools.parseString(textObj);

        Object sensitiveWordsObj = sensitiveWordsParser.parse(sourceData, rowIndex, context);
        if (sensitiveWordsObj == null) {
            return text;
        }
        String sensitiveWords = OperatorTools.parseString(sensitiveWordsObj);
        if (sensitiveWords.isEmpty()) {
            return text;
        }

        Object replacementObj = replacementParser.parse(sourceData, rowIndex, context);
        String replacement = replacementObj == null ? "" : OperatorTools.parseString(replacementObj);

        String[] words = sensitiveWords.split(",");
        List<String> patterns = new ArrayList<>();
        for (String word : words) {
            String trimmed = word.trim();
            if (!trimmed.isEmpty()) {
                patterns.add(trimmed);
            }
        }
        if (patterns.isEmpty()) {
            return text;
        }

        ACNode root = buildAutomaton(patterns);
        return replaceMatches(text, root, replacement);
    }

    /**
     * Build an Aho-Corasick automaton from the given list of patterns.
     *
     * @param patterns list of non-empty pattern strings
     * @return root node of the automaton
     */
    private ACNode buildAutomaton(List<String> patterns) {
        ACNode root = new ACNode();

        // Phase 1: build trie
        for (String pattern : patterns) {
            ACNode cur = root;
            for (char c : pattern.toCharArray()) {
                cur = cur.children.computeIfAbsent(c, k -> new ACNode());
            }
            cur.outputs.add(pattern.length());
        }

        // Phase 2: BFS to build fail links and propagate outputs
        Queue<ACNode> queue = new LinkedList<>();
        for (ACNode child : root.children.values()) {
            child.fail = root;
            queue.add(child);
        }

        while (!queue.isEmpty()) {
            ACNode cur = queue.poll();
            for (Map.Entry<Character, ACNode> entry : cur.children.entrySet()) {
                char c = entry.getKey();
                ACNode child = entry.getValue();

                // Walk up fail links to find the longest proper suffix with a transition on c
                ACNode f = cur.fail;
                while (f != null && !f.children.containsKey(c)) {
                    f = f.fail;
                }
                child.fail = (f == null) ? root : f.children.getOrDefault(c, root);
                if (child.fail == child) {
                    child.fail = root;
                }

                // Inherit outputs from fail node
                child.outputs.addAll(child.fail.outputs);

                queue.add(child);
            }
        }

        return root;
    }

    /**
     * Scan 'text' using the Aho-Corasick automaton and return the text with all
     * matched sensitive words replaced by 'replacement'. Greedy, non-overlapping,
     * left-to-right replacement is applied; for each position the longest match wins.
     *
     * @param text        input text
     * @param root        root node of the Aho-Corasick automaton
     * @param replacement replacement string
     * @return text with sensitive words replaced
     */
    private String replaceMatches(String text, ACNode root, String replacement) {
        int n = text.length();

        // matchEnd[i] stores the length of the longest pattern ending at position i (0-indexed),
        // or 0 if no pattern ends there.
        int[] matchLen = new int[n];

        ACNode cur = root;
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);

            // Follow fail links until we find a node with transition on c or reach root
            while (cur != root && !cur.children.containsKey(c)) {
                cur = cur.fail;
            }
            if (cur.children.containsKey(c)) {
                cur = cur.children.get(c);
            }

            // Record the longest pattern ending at position i
            for (int len : cur.outputs) {
                if (len > matchLen[i]) {
                    matchLen[i] = len;
                }
            }
        }

        // Greedy left-to-right replacement: prefer leftmost, longest match
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < n) {
            // Find the earliest match starting at or after position i
            int bestStart = -1;
            int bestLen = 0;
            for (int j = i; j < n; j++) {
                if (matchLen[j] > 0) {
                    int start = j - matchLen[j] + 1;
                    if (start >= i) {
                        // Valid non-overlapping match
                        if (bestStart == -1 || start < bestStart
                                || (start == bestStart && matchLen[j] > bestLen)) {
                            bestStart = start;
                            bestLen = matchLen[j];
                        }
                        break; // earliest match found; collect longest at same start
                    }
                }
            }

            if (bestStart == -1) {
                // No more matches; append the rest
                sb.append(text, i, n);
                break;
            }

            // Collect the longest match among all patterns ending in the window [bestStart, ...]
            // (already found via the first-pass loop above, but verify there is no longer match
            // with the same start position at a later end index)
            int bestEnd = bestStart + bestLen - 1;
            for (int j = bestEnd + 1; j < n; j++) {
                if (matchLen[j] > 0) {
                    int start = j - matchLen[j] + 1;
                    if (start == bestStart && matchLen[j] > bestLen) {
                        bestLen = matchLen[j];
                        bestEnd = j;
                    } else if (start > bestStart) {
                        break;
                    }
                }
            }

            // Append text before the match, then the replacement
            sb.append(text, i, bestStart);
            sb.append(replacement);
            i = bestStart + bestLen;
        }

        return sb.toString();
    }

    /**
     * Visible for testing: build the Aho-Corasick automaton and replace matches.
     * This method is a convenience wrapper that accepts an array of pattern strings.
     */
    static String filterWithAC(String text, String[] patterns, String replacement) {
        SensitiveWordFilterACFunction f = new SensitiveWordFilterACFunction();
        List<String> patternList = new ArrayList<>(Arrays.asList(patterns));
        ACNode root = f.buildAutomaton(patternList);
        return f.replaceMatches(text, root, replacement);
    }

    /**
     * Private no-arg constructor used only by {@link #filterWithAC}.
     */
    private SensitiveWordFilterACFunction() {
    }
}
