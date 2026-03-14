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
        String transformSql = "select sensitive_word_filter(string1, string2, string3) from source";
        TransformConfig config = new TransformConfig(transformSql);
        TransformProcessor<String, String> processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));

        // case1: single sensitive word replaced
        List<String> output1 = processor.transform("Hello bad world|bad|***", new HashMap<>());
        Assert.assertEquals(1, output1.size());
        Assert.assertEquals("result=Hello *** world", output1.get(0));

        // case2: multiple sensitive words in comma-separated list, all replaced
        List<String> output2 = processor.transform("Hello bad world|bad,world|***", new HashMap<>());
        Assert.assertEquals(1, output2.size());
        Assert.assertEquals("result=Hello *** ***", output2.get(0));

        // case3: sensitive word not present in text
        List<String> output3 = processor.transform("Hello world|bad|***", new HashMap<>());
        Assert.assertEquals(1, output3.size());
        Assert.assertEquals("result=Hello world", output3.get(0));

        // case4: empty sensitive words list leaves text unchanged
        List<String> output4 = processor.transform("Hello world||***", new HashMap<>());
        Assert.assertEquals(1, output4.size());
        Assert.assertEquals("result=Hello world", output4.get(0));

        // case5: partial-word replacement (sensitive_word_filter replaces substrings)
        List<String> output5 = processor.transform("my nickname|nick|**", new HashMap<>());
        Assert.assertEquals(1, output5.size());
        Assert.assertEquals("result=my **name", output5.get(0));

        // case6: multiple occurrences of the same sensitive word all replaced
        List<String> output6 = processor.transform("bad bad bad|bad|***", new HashMap<>());
        Assert.assertEquals(1, output6.size());
        Assert.assertEquals("result=*** *** ***", output6.get(0));

        // case7: replacement is empty string (word deletion)
        List<String> output7 = processor.transform("Hello bad world|bad|", new HashMap<>());
        Assert.assertEquals(1, output7.size());
        Assert.assertEquals("result=Hello  world", output7.get(0));
    }
}
