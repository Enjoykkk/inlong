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
 * SensitiveWordFilterFunction -> sensitive_word_filter(text, sensitive_words, replacement[, mode])
 * description:
 * - Return NULL if 'text' or 'sensitive_words' is NULL
 * - Return the result of replacing all sensitive words found in 'text' with 'replacement'
 * - 'sensitive_words' is a comma-separated list of words to filter
 * - Optional 'mode' selects the matching algorithm:
 *     'normal' - direct sequential replacement, recommended for short text (comments, nicknames)
 *     'fast'   - Aho-Corasick multi-pattern algorithm, recommended for long text (articles)
 *   When 'mode' is omitted the function auto-selects: texts shorter than 200 characters use
 *   'normal', longer texts use 'fast'.
 */
@TransformFunction(type = FunctionConstant.STRING_TYPE, names = {
        "sensitive_word_filter"
}, parameter = "(String text, String sensitive_words, String replacement[, String mode])", descriptions = {
        "- Return NULL if 'text' or 'sensitive_words' is NULL;",
        "- Return the result of replacing all occurrences of sensitive words in 'text' with 'replacement';",
        "- 'sensitive_words' is a comma-separated list of words to filter;",
        "- Optional 'mode': 'fast' uses the Aho-Corasick algorithm (recommended for long text such as articles), "
                + "'normal' uses direct sequential replacement (recommended for short text such as comments or "
                + "nicknames). When omitted, 'normal' is used for text shorter than 200 characters and 'fast' "
                + "for longer text."
}, examples = {
        "sensitive_word_filter('Hello badword world', 'badword', '***') = \"Hello *** world\"",
        "sensitive_word_filter('He said badword1 and badword2', 'badword1,badword2', '***') "
                + "= \"He said *** and ***\"",
        "sensitive_word_filter('Short comment with badword', 'badword', '***', 'normal') "
                + "= \"Short comment with ***\"",
        "sensitive_word_filter('Long article text with badword1 and badword2', 'badword1,badword2', '***', 'fast') "
                + "= \"Long article text with *** and ***\""
})
public class SensitiveWordFilterFunction implements ValueParser {

    /** Threshold (in characters) above which 'fast' mode is auto-selected. */
    private static final int AUTO_FAST_THRESHOLD = 200;

    /** Delimiter used to split the sensitive-word list parameter. */
    private static final String WORD_DELIMITER = ",";

    private final ValueParser textParser;
    private final ValueParser sensitiveWordsParser;
    private final ValueParser replacementParser;
    private final ValueParser modeParser;

    public SensitiveWordFilterFunction(Function expr) {
        List<Expression> expressions = expr.getParameters().getExpressions();
        textParser = OperatorTools.buildParser(expressions.get(0));
        sensitiveWordsParser = OperatorTools.buildParser(expressions.get(1));
        replacementParser = OperatorTools.buildParser(expressions.get(2));
        modeParser = (expressions.size() >= 4) ? OperatorTools.buildParser(expressions.get(3)) : null;
    }

    @Override
    public Object parse(SourceData sourceData, int rowIndex, Context context) {
        Object textObj = textParser.parse(sourceData, rowIndex, context);
        Object sensitiveWordsObj = sensitiveWordsParser.parse(sourceData, rowIndex, context);
        if (textObj == null || sensitiveWordsObj == null) {
            return null;
        }

        String text = OperatorTools.parseString(textObj);
        String sensitiveWordsCsv = OperatorTools.parseString(sensitiveWordsObj);
        String replacement = replacementParser == null
                ? ""
                : OperatorTools.parseString(replacementParser.parse(sourceData, rowIndex, context));
        if (replacement == null) {
            replacement = "";
        }

        List<String> words = parseWords(sensitiveWordsCsv);
        if (words.isEmpty()) {
            return text;
        }

        String mode = resolveMode(text, sourceData, rowIndex, context);
        if ("fast".equalsIgnoreCase(mode)) {
            return filterFast(text, words, replacement);
        }
        return filterNormal(text, words, replacement);
    }

    // -------------------------------------------------------------------------
    // Helper: resolve operating mode
    // -------------------------------------------------------------------------

    private String resolveMode(String text, SourceData sourceData, int rowIndex, Context context) {
        if (modeParser != null) {
            Object modeObj = modeParser.parse(sourceData, rowIndex, context);
            if (modeObj != null) {
                return OperatorTools.parseString(modeObj);
            }
        }
        return text.length() >= AUTO_FAST_THRESHOLD ? "fast" : "normal";
    }

    // -------------------------------------------------------------------------
    // Helper: parse the comma-separated sensitive word list
    // -------------------------------------------------------------------------

    private List<String> parseWords(String csv) {
        List<String> words = new ArrayList<>();
        for (String word : csv.split(WORD_DELIMITER, -1)) {
            String trimmed = word.trim();
            if (!trimmed.isEmpty()) {
                words.add(trimmed);
            }
        }
        return words;
    }

    // -------------------------------------------------------------------------
    // 'normal' mode: sequential String.replace() – best for short text
    // -------------------------------------------------------------------------

    private String filterNormal(String text, List<String> words, String replacement) {
        for (String word : words) {
            text = text.replace(word, replacement);
        }
        return text;
    }

    // -------------------------------------------------------------------------
    // 'fast' mode: Aho-Corasick algorithm – best for long text / many patterns
    // -------------------------------------------------------------------------

    /**
     * Filters sensitive words using the Aho-Corasick multi-pattern algorithm.
     * Time complexity: O(n + m + z) where n = text length, m = total length of all
     * patterns, z = number of matches found.
     */
    private String filterFast(String text, List<String> words, String replacement) {
        AhoCorasick ac = new AhoCorasick(words);
        return ac.replace(text, replacement);
    }

    // =========================================================================
    // Aho-Corasick automaton (inner class, no external dependencies)
    // =========================================================================

    /**
     * Lightweight Aho-Corasick implementation for multi-pattern string matching.
     *
     * <p>Each trie node stores its children in a {@link HashMap} so that the
     * automaton works efficiently for any character set (ASCII, CJK, etc.) without
     * pre-allocating a large fixed-size array per node.
     */
    static class AhoCorasick {

        /** Children map: node index → (char → child node index). */
        private final List<Map<Character, Integer>> children;
        /** Failure links for each node. */
        private final List<Integer> fail;
        /** Length of the longest pattern that ends at each node (0 = none). */
        private final List<Integer> output;

        AhoCorasick(List<String> patterns) {
            children = new ArrayList<>();
            fail = new ArrayList<>();
            output = new ArrayList<>();

            // Root node
            children.add(new HashMap<>());
            fail.add(0);
            output.add(0);

            // --- Phase 1: build the trie ---
            for (String pattern : patterns) {
                int cur = 0;
                for (int i = 0; i < pattern.length(); i++) {
                    char c = pattern.charAt(i);
                    Integer next = children.get(cur).get(c);
                    if (next == null) {
                        next = children.size();
                        children.get(cur).put(c, next);
                        children.add(new HashMap<>());
                        fail.add(0);
                        output.add(0);
                    }
                    cur = next;
                }
                // Record the longest pattern length ending at this node
                if (output.get(cur) < pattern.length()) {
                    output.set(cur, pattern.length());
                }
            }

            // --- Phase 2: compute failure links via BFS ---
            Queue<Integer> queue = new ArrayDeque<>();
            // Initialize depth-1 nodes: their failure link is always the root
            for (int child : children.get(0).values()) {
                fail.set(child, 0);
                queue.add(child);
            }
            while (!queue.isEmpty()) {
                int u = queue.poll();
                // Propagate the dictionary-suffix output through failure links
                if (output.get(u) == 0 && output.get(fail.get(u)) > 0) {
                    output.set(u, output.get(fail.get(u)));
                }
                for (Map.Entry<Character, Integer> entry : children.get(u).entrySet()) {
                    char c = entry.getKey();
                    int v = entry.getValue();
                    // Failure link for v: follow u's failure link until a match for c
                    int f = fail.get(u);
                    while (f != 0 && !children.get(f).containsKey(c)) {
                        f = fail.get(f);
                    }
                    Integer fChild = children.get(f).get(c);
                    fail.set(v, (fChild != null && fChild != v) ? fChild : 0);
                    queue.add(v);
                }
            }
        }

        /**
         * Returns the next state from {@code cur} on character {@code c}.
         * Follows failure links until a matching transition is found or the
         * root is reached.
         */
        private int nextState(int cur, char c) {
            while (cur != 0 && !children.get(cur).containsKey(c)) {
                cur = fail.get(cur);
            }
            Integer child = children.get(cur).get(c);
            return (child != null) ? child : 0;
        }

        /**
         * Scans {@code text} and replaces every matched pattern with {@code replacement}.
         * When overlapping matches exist the leftmost-longest wins (greedy left-to-right).
         */
        String replace(String text, String replacement) {
            List<int[]> matches = new ArrayList<>();
            int cur = 0;
            for (int i = 0; i < text.length(); i++) {
                cur = nextState(cur, text.charAt(i));
                int matchLen = output.get(cur);
                if (matchLen > 0) {
                    // match covers [i - matchLen + 1, i] (inclusive)
                    matches.add(new int[]{i - matchLen + 1, i + 1});
                }
            }

            if (matches.isEmpty()) {
                return text;
            }

            // Deduplicate: for each start position keep the longest match
            Map<Integer, Integer> startToEnd = new HashMap<>();
            for (int[] m : matches) {
                startToEnd.merge(m[0], m[1], Math::max);
            }
            // Sort intervals by start position
            List<int[]> intervals = new ArrayList<>(startToEnd.size());
            for (Map.Entry<Integer, Integer> e : startToEnd.entrySet()) {
                intervals.add(new int[]{e.getKey(), e.getValue()});
            }
            intervals.sort((a, b) -> a[0] - b[0]);

            // Build result string, skipping intervals that overlap a previous one
            StringBuilder sb = new StringBuilder(text.length());
            int textPos = 0;
            for (int[] interval : intervals) {
                int start = interval[0];
                int end = interval[1];
                if (start < textPos) {
                    continue; // overlaps with a previously emitted replacement
                }
                sb.append(text, textPos, start);
                sb.append(replacement);
                textPos = end;
            }
            sb.append(text, textPos, text.length());
            return sb.toString();
        }
    }
}
