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

import org.apache.inlong.sdk.transform.decode.SourceDecoderFactory;
import org.apache.inlong.sdk.transform.encode.SinkEncoderFactory;
import org.apache.inlong.sdk.transform.pojo.TransformConfig;
import org.apache.inlong.sdk.transform.process.TransformProcessor;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;

public class TestSensitiveWordFilterACFunction extends AbstractFunctionStringTestBase {

    @Test
    public void testSensitiveWordFilterACFunction() throws Exception {
        String transformSql = "select sensitive_word_filter_ac(string1, string2, string3) from source";
        TransformConfig config = new TransformConfig(transformSql);
        TransformProcessor<String, String> processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));

        // case1: sensitive_word_filter_ac('Hello bad world', 'bad', '***')
        List<String> output1 = processor.transform("Hello bad world|bad|***", new HashMap<>());
        Assert.assertEquals(1, output1.size());
        Assert.assertEquals("result=Hello *** world", output1.get(0));

        // case2: sensitive_word_filter_ac('good morning', 'bad,evil', '***') -> no match
        List<String> output2 = processor.transform("good morning|bad,evil|***", new HashMap<>());
        Assert.assertEquals(1, output2.size());
        Assert.assertEquals("result=good morning", output2.get(0));

        // case3: sensitive_word_filter_ac('bad evil text', 'bad,evil', '***') -> multiple words
        List<String> output3 = processor.transform("bad evil text|bad,evil|***", new HashMap<>());
        Assert.assertEquals(1, output3.size());
        Assert.assertEquals("result=*** *** text", output3.get(0));

        // case4: sensitive_word_filter_ac('hello world', '', '***') -> empty sensitive_words
        List<String> output4 = processor.transform("hello world||***", new HashMap<>());
        Assert.assertEquals(1, output4.size());
        Assert.assertEquals("result=hello world", output4.get(0));

        // case5: sensitive_word_filter_ac('badword here', 'badword', '') -> replace with empty
        List<String> output5 = processor.transform("badword here|badword|", new HashMap<>());
        Assert.assertEquals(1, output5.size());
        Assert.assertEquals("result= here", output5.get(0));

        // case6: sensitive_word_filter_ac('bad bad bad', 'bad', '***') -> repeated word
        List<String> output6 = processor.transform("bad bad bad|bad|***", new HashMap<>());
        Assert.assertEquals(1, output6.size());
        Assert.assertEquals("result=*** *** ***", output6.get(0));

        // case7: long text with multiple patterns – validates efficiency for article-length input
        // sensitive_word_filter_ac('The quick brown fox jumps over the lazy dog', 'fox,lazy', '***')
        List<String> output7 =
                processor.transform("The quick brown fox jumps over the lazy dog|fox,lazy|***", new HashMap<>());
        Assert.assertEquals(1, output7.size());
        Assert.assertEquals("result=The quick brown *** jumps over the *** dog", output7.get(0));

        // case8: overlapping patterns – longer match wins (greedy left-to-right)
        // sensitive_word_filter_ac('abcde', 'ab,abcd', '***') -> 'abcd' is longer match at pos 0
        List<String> output8 = processor.transform("abcde|ab,abcd|***", new HashMap<>());
        Assert.assertEquals(1, output8.size());
        Assert.assertEquals("result=***e", output8.get(0));
    }
}
