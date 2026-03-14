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

public class TestSensitiveWordFilterFunction extends AbstractFunctionStringTestBase {

    @Test
    public void testSensitiveWordFilterFunction() throws Exception {
        // -----------------------------------------------------------------------
        // Short text strategy (text length <= 200): simple string matching
        // SQL: sensitive_word_filter(text, sensitiveWords, replacement)
        // -----------------------------------------------------------------------
        String transformSql = "select sensitive_word_filter(string1, string2, string3) from source";
        TransformConfig config = new TransformConfig(transformSql);
        TransformProcessor<String, String> processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));

        // case1: basic replacement - sensitive_word_filter('Hello World', 'World', '*')
        List<String> output1 = processor.transform("Hello World|World|*", new HashMap<>());
        Assert.assertEquals(1, output1.size());
        Assert.assertEquals("result=Hello *****", output1.get(0));

        // case2: multiple sensitive words - sensitive_word_filter('bad word here', 'bad,word', '#')
        List<String> output2 = processor.transform("bad word here|bad,word|#", new HashMap<>());
        Assert.assertEquals(1, output2.size());
        Assert.assertEquals("result=### #### here", output2.get(0));

        // case3: no match - sensitive_word_filter('Hello World', 'xyz', '*')
        List<String> output3 = processor.transform("Hello World|xyz|*", new HashMap<>());
        Assert.assertEquals(1, output3.size());
        Assert.assertEquals("result=Hello World", output3.get(0));

        // case4: case-sensitive (no match for different case)
        // sensitive_word_filter('Hello World', 'world', '*')
        List<String> output4 = processor.transform("Hello World|world|*", new HashMap<>());
        Assert.assertEquals(1, output4.size());
        Assert.assertEquals("result=Hello World", output4.get(0));

        // case5: word appearing multiple times
        // sensitive_word_filter('abc abc abc', 'abc', '*')
        List<String> output5 = processor.transform("abc abc abc|abc|*", new HashMap<>());
        Assert.assertEquals(1, output5.size());
        Assert.assertEquals("result=*** *** ***", output5.get(0));

        // case6: overlapping patterns - sensitive_word_filter('abcbc', 'abc,bc', '*')
        List<String> output6 = processor.transform("abcbc|abc,bc|*", new HashMap<>());
        Assert.assertEquals(1, output6.size());
        Assert.assertEquals("result=*****", output6.get(0));

        // case7: empty sensitiveWords -> text returned unchanged
        // sensitive_word_filter('Hello World', '', '*')
        List<String> output7 = processor.transform("Hello World||*", new HashMap<>());
        Assert.assertEquals(1, output7.size());
        Assert.assertEquals("result=Hello World", output7.get(0));

        // case8: null sensitiveWords (missing field) -> null result shown as empty
        // sensitive_word_filter('Hello World', null, '*')
        List<String> output8 = processor.transform("Hello World", new HashMap<>());
        Assert.assertEquals(1, output8.size());
        Assert.assertEquals("result=", output8.get(0));

        // -----------------------------------------------------------------------
        // Default replacement (no third argument): defaults to '*'
        // -----------------------------------------------------------------------
        String transformSql2 = "select sensitive_word_filter(string1, string2) from source";
        TransformConfig config2 = new TransformConfig(transformSql2);
        TransformProcessor<String, String> processor2 = TransformProcessor
                .create(config2, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));

        // case9: default replacement '*' - sensitive_word_filter('Hello World', 'World')
        List<String> output9 = processor2.transform("Hello World|World", new HashMap<>());
        Assert.assertEquals(1, output9.size());
        Assert.assertEquals("result=Hello *****", output9.get(0));

        // -----------------------------------------------------------------------
        // Long text strategy (text length > 200): Aho-Corasick algorithm
        // -----------------------------------------------------------------------
        // Build a text that is > 200 characters to trigger the Aho-Corasick path
        String longText = "This is a long article designed to test the Aho-Corasick algorithm. "
                + "The article contains several paragraphs of text to exceed the threshold. "
                + "In this article there are some bad words and some evil content that needs "
                + "to be filtered out using our sensitive word filtering functionality.";
        Assert.assertTrue("Long text must exceed threshold", longText.length() > 200);

        String expectedLongText = "This is a long article designed to test the Aho-Corasick algorithm. "
                + "The article contains several paragraphs of text to exceed the threshold. "
                + "In this article there are some *** words and some **** content that needs "
                + "to be filtered out using our sensitive word filtering functionality.";

        // case10: long text - sensitive_word_filter(longText, 'bad,evil', '*')
        List<String> output10 = processor.transform(longText + "|bad,evil|*", new HashMap<>());
        Assert.assertEquals(1, output10.size());
        Assert.assertEquals("result=" + expectedLongText, output10.get(0));

        // case11: long text with multiple occurrences of a sensitive word
        String longText2 = "The word bad appears multiple times: bad, bad and bad again. "
                + "We want to make sure all occurrences of bad are replaced correctly. "
                + "This text is intentionally made long enough to exceed two hundred characters "
                + "so that the Aho-Corasick algorithm is triggered for this test case.";
        Assert.assertTrue("Long text 2 must exceed threshold", longText2.length() > 200);

        String expectedLongText2 = "The word *** appears multiple times: ***, *** and *** again. "
                + "We want to make sure all occurrences of *** are replaced correctly. "
                + "This text is intentionally made long enough to exceed two hundred characters "
                + "so that the Aho-Corasick algorithm is triggered for this test case.";

        List<String> output11 = processor.transform(longText2 + "|bad|*", new HashMap<>());
        Assert.assertEquals(1, output11.size());
        Assert.assertEquals("result=" + expectedLongText2, output11.get(0));
    }
}
