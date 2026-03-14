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
import java.util.List;

/**
 * ContainsSensitiveWordFunction -> contains_sensitive_word(text, word1[, word2, ...])
 * description:
 * - Return NULL if 'text' is NULL
 * - Return false if no sensitive words are provided
 * - Return true if 'text' contains any of the specified sensitive words, false otherwise.
 *          Suitable for validating short text such as nicknames and comments.
 */
@TransformFunction(type = FunctionConstant.STRING_TYPE, names = {
        "contains_sensitive_word"}, parameter = "(String text, String word1[, String word2, ...])", descriptions = {
                "- Return NULL if 'text' is NULL;",
                "- Return false if no sensitive words are provided;",
                "- Return true if 'text' contains any of the specified sensitive words, false otherwise. "
                        + "Suitable for validating short text such as nicknames and comments."
        }, examples = {
                "contains_sensitive_word('badword hello', 'badword') = true",
                "contains_sensitive_word('hello world', 'badword', 'spam') = false"
        })
public class ContainsSensitiveWordFunction implements ValueParser {

    private final ValueParser textParser;
    private final List<ValueParser> wordParsers;

    public ContainsSensitiveWordFunction(Function expr) {
        List<Expression> expressions = expr.getParameters().getExpressions();
        textParser = OperatorTools.buildParser(expressions.get(0));
        wordParsers = new ArrayList<>();
        for (int i = 1; i < expressions.size(); i++) {
            wordParsers.add(OperatorTools.buildParser(expressions.get(i)));
        }
    }

    @Override
    public Object parse(SourceData sourceData, int rowIndex, Context context) {
        Object textObj = textParser.parse(sourceData, rowIndex, context);
        if (textObj == null) {
            return null;
        }
        String text = OperatorTools.parseString(textObj);
        if (wordParsers.isEmpty()) {
            return false;
        }
        for (ValueParser wordParser : wordParsers) {
            Object wordObj = wordParser.parse(sourceData, rowIndex, context);
            if (wordObj != null) {
                String word = OperatorTools.parseString(wordObj);
                if (word != null && !word.isEmpty() && text.contains(word)) {
                    return true;
                }
            }
        }
        return false;
    }
}
