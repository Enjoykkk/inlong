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

import java.util.List;

/**
 * SensitiveWordFilterFunction  ->  sensitive_word_filter(text, sensitive_words, replacement)
 * description:
 * - Return null if 'text' is null;
 * - Return 'text' unchanged if 'sensitive_words' is null or empty;
 * - Return the result of replacing all sensitive words in 'text' with 'replacement'.
 * - 'sensitive_words' is a comma-separated list of words to be filtered.
 * - This function uses simple sequential string replacement, which is efficient for short text
 *   such as individual comments or nicknames where the input is typically small.
 */
@TransformFunction(type = FunctionConstant.STRING_TYPE, names = {
        "sensitive_word_filter"}, parameter = "(String text, String sensitive_words, String replacement)", descriptions = {
                "- Return null if 'text' is null;",
                "- Return 'text' unchanged if 'sensitive_words' is null or empty;",
                "- Return the result of replacing all sensitive words in 'text' with 'replacement'.",
                "- 'sensitive_words' is a comma-separated list of words to be filtered.",
                "- This function uses simple sequential string replacement, suitable for short text "
                        + "such as individual comments or nicknames."
        }, examples = {
                "sensitive_word_filter('Hello bad world', 'bad,evil', '***') = \"Hello *** world\"",
                "sensitive_word_filter('good morning', 'bad,evil', '***') = \"good morning\""
        })
public class SensitiveWordFilterFunction implements ValueParser {

    private ValueParser textParser;
    private ValueParser sensitiveWordsParser;
    private ValueParser replacementParser;

    public SensitiveWordFilterFunction(Function expr) {
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
        for (String word : words) {
            String trimmedWord = word.trim();
            if (!trimmedWord.isEmpty()) {
                text = text.replace(trimmedWord, replacement);
            }
        }
        return text;
    }
}
