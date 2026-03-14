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
        String transformSql = null;
        TransformConfig config = null;
        TransformProcessor<String, String> processor = null;
        List<String> output = null;

        // case1: single sensitive word replacement
        transformSql = "select sensitive_word_filter(string1, '***', string2) from source";
        config = new TransformConfig(transformSql);
        processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));
        output = processor.transform("hello world|world||1|2|3", new HashMap<>());
        Assert.assertEquals(1, output.size());
        Assert.assertEquals("result=hello ***", output.get(0));

        // case2: multiple sensitive word replacement
        transformSql = "select sensitive_word_filter(string1, '***', string2, string3) from source";
        config = new TransformConfig(transformSql);
        processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));
        output = processor.transform("I love apple and pear|apple|pear|1|2|3", new HashMap<>());
        Assert.assertEquals(1, output.size());
        Assert.assertEquals("result=I love *** and ***", output.get(0));

        // case3: sensitive word not found - text unchanged
        transformSql = "select sensitive_word_filter(string1, '***', string2) from source";
        config = new TransformConfig(transformSql);
        processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));
        output = processor.transform("hello world|badword||1|2|3", new HashMap<>());
        Assert.assertEquals(1, output.size());
        Assert.assertEquals("result=hello world", output.get(0));

        // case4: text is empty - returns empty string (no matches in empty text)
        transformSql = "select sensitive_word_filter(string1, '***', string2) from source";
        config = new TransformConfig(transformSql);
        processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));
        output = processor.transform("|world||1|2|3", new HashMap<>());
        Assert.assertEquals(1, output.size());
        Assert.assertEquals("result=", output.get(0));

        // case5: long text with multiple occurrences
        transformSql = "select sensitive_word_filter(string1, '[FILTERED]', string2, string3) from source";
        config = new TransformConfig(transformSql);
        processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));
        output = processor.transform(
                "This article contains spam and more spam and also junk content|spam|junk|1|2|3",
                new HashMap<>());
        Assert.assertEquals(1, output.size());
        Assert.assertEquals("result=This article contains [FILTERED] and more [FILTERED] and also [FILTERED] content",
                output.get(0));

        // case6: sensitive word at the beginning and end of text
        transformSql = "select sensitive_word_filter(string1, '***', string2) from source";
        config = new TransformConfig(transformSql);
        processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));
        output = processor.transform("badword hello badword|badword||1|2|3", new HashMap<>());
        Assert.assertEquals(1, output.size());
        Assert.assertEquals("result=*** hello ***", output.get(0));
    }

    @Test
    public void testContainsSensitiveWordFunction() throws Exception {
        String transformSql = null;
        TransformConfig config = null;
        TransformProcessor<String, String> processor = null;
        List<String> output = null;

        // case1: text contains the sensitive word - returns true
        transformSql = "select contains_sensitive_word(string1, string2) from source";
        config = new TransformConfig(transformSql);
        processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));
        output = processor.transform("badword hello|badword||1|2|3", new HashMap<>());
        Assert.assertEquals(1, output.size());
        Assert.assertEquals("result=true", output.get(0));

        // case2: text does not contain the sensitive word - returns false
        transformSql = "select contains_sensitive_word(string1, string2) from source";
        config = new TransformConfig(transformSql);
        processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));
        output = processor.transform("hello world|badword||1|2|3", new HashMap<>());
        Assert.assertEquals(1, output.size());
        Assert.assertEquals("result=false", output.get(0));

        // case3: nickname exactly matches a sensitive word - returns true
        transformSql = "select contains_sensitive_word(string1, string2, string3) from source";
        config = new TransformConfig(transformSql);
        processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));
        output = processor.transform("spam|badword|spam|1|2|3", new HashMap<>());
        Assert.assertEquals(1, output.size());
        Assert.assertEquals("result=true", output.get(0));

        // case4: nickname contains no sensitive words - returns false
        transformSql = "select contains_sensitive_word(string1, string2, string3) from source";
        config = new TransformConfig(transformSql);
        processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));
        output = processor.transform("gooduser|badword|spam|1|2|3", new HashMap<>());
        Assert.assertEquals(1, output.size());
        Assert.assertEquals("result=false", output.get(0));

        // case5: text is empty - returns false (empty string contains no sensitive words)
        transformSql = "select contains_sensitive_word(string1, string2) from source";
        config = new TransformConfig(transformSql);
        processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));
        output = processor.transform("|badword||1|2|3", new HashMap<>());
        Assert.assertEquals(1, output.size());
        Assert.assertEquals("result=false", output.get(0));
    }
}
