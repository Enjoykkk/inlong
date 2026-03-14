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
 * - Return NULL if any parameter is null
 * - Return 'text' with each occurrence of every word in 'sensitive_words' replaced by 'replacement'.
 *   'sensitive_words' is a comma-separated list of words to filter.
 * - Suitable for short texts such as comments or nicknames where the number of
 *   sensitive words is small. For long texts with many sensitive words, use
 *   sensitive_word_filter_ac instead.
 */
@TransformFunction(type = FunctionConstant.STRING_TYPE, names = {
        "sensitive_word_filter"}, parameter = "(String text, String sensitive_words, String replacement)", descriptions = {
                "- Return \"\" if any parameter is null;",
                "- Return 'text' with each word in the comma-separated 'sensitive_words' list replaced by "
                        + "'replacement'. Suitable for short texts such as comments or nicknames."
        }, examples = {
                "sensitive_word_filter('Hello bad world', 'bad,world', '***') = \"Hello *** ***\"",
                "sensitive_word_filter('my nickname', 'nick', '**') = \"my **name\""
        })
public class SensitiveWordFilterFunction implements ValueParser {

    private final ValueParser textParser;
    private final ValueParser sensitiveWordsParser;
    private final ValueParser replacementParser;

    public SensitiveWordFilterFunction(Function expr) {
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

        String[] words = sensitiveWords.split(",");
        for (String word : words) {
            String trimmed = word.trim();
            if (!trimmed.isEmpty()) {
                text = text.replace(trimmed, replacement);
            }
        }
        return text;
    }
}
