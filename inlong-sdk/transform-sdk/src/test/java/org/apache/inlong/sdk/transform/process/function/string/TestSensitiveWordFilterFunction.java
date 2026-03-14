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

/**
 * Tests for {@link SensitiveWordFilterFunction}.
 *
 * <p>The function exposes two matching strategies to handle different text lengths:
 * <ul>
 *   <li><b>normal</b> – sequential {@code String.replace()}, best for short text such as
 *       comments or nicknames.</li>
 *   <li><b>fast</b> – Aho-Corasick multi-pattern algorithm, best for long text such as
 *       articles.</li>
 * </ul>
 * When the {@code mode} argument is omitted the function selects the strategy automatically
 * based on the length of the input text.
 */
public class TestSensitiveWordFilterFunction extends AbstractFunctionStringTestBase {

    // ------------------------------------------------------------------
    // 'normal' mode (short-text / comment / nickname path)
    // ------------------------------------------------------------------

    @Test
    public void testNormalModeSingleWord() throws Exception {
        String sql = "select sensitive_word_filter(string1, string2, string3, 'normal') from source";
        TransformConfig config = new TransformConfig(sql);
        TransformProcessor<String, String> processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));

        // case1: single sensitive word replaced
        List<String> out1 = processor.transform("Hello badword world|badword|***", new HashMap<>());
        Assert.assertEquals(1, out1.size());
        Assert.assertEquals("result=Hello *** world", out1.get(0));

        // case2: word not present – text unchanged
        List<String> out2 = processor.transform("Hello clean world|badword|***", new HashMap<>());
        Assert.assertEquals(1, out2.size());
        Assert.assertEquals("result=Hello clean world", out2.get(0));

        // case3: multiple occurrences all replaced
        List<String> out3 = processor.transform("badword is bad and badword again|badword|***", new HashMap<>());
        Assert.assertEquals(1, out3.size());
        Assert.assertEquals("result=*** is bad and *** again", out3.get(0));
    }

    @Test
    public void testNormalModeMultipleWords() throws Exception {
        String sql = "select sensitive_word_filter(string1, string2, string3, 'normal') from source";
        TransformConfig config = new TransformConfig(sql);
        TransformProcessor<String, String> processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));

        // case1: two sensitive words, both present
        List<String> out1 = processor.transform(
                "He said word1 and word2|word1,word2|***", new HashMap<>());
        Assert.assertEquals(1, out1.size());
        Assert.assertEquals("result=He said *** and ***", out1.get(0));

        // case2: only one of two words present
        List<String> out2 = processor.transform(
                "He said word1 only|word1,word2|***", new HashMap<>());
        Assert.assertEquals(1, out2.size());
        Assert.assertEquals("result=He said *** only", out2.get(0));
    }

    @Test
    public void testNormalModeNullAndEdgeCases() throws Exception {
        String sql = "select sensitive_word_filter(string1, string2, string3, 'normal') from source";
        TransformConfig config = new TransformConfig(sql);
        TransformProcessor<String, String> processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));

        // case1: empty sensitive-word list – text unchanged
        List<String> out1 = processor.transform("Hello world||***", new HashMap<>());
        Assert.assertEquals(1, out1.size());
        Assert.assertEquals("result=Hello world", out1.get(0));

        // case2: empty text
        List<String> out2 = processor.transform("|badword|***", new HashMap<>());
        Assert.assertEquals(1, out2.size());
        Assert.assertEquals("result=", out2.get(0));

        // case3: empty replacement – words are removed
        List<String> out3 = processor.transform("Hello badword world|badword|", new HashMap<>());
        Assert.assertEquals(1, out3.size());
        Assert.assertEquals("result=Hello  world", out3.get(0));
    }

    // ------------------------------------------------------------------
    // 'fast' mode (long-text / article path – Aho-Corasick)
    // ------------------------------------------------------------------

    @Test
    public void testFastModeSingleWord() throws Exception {
        String sql = "select sensitive_word_filter(string1, string2, string3, 'fast') from source";
        TransformConfig config = new TransformConfig(sql);
        TransformProcessor<String, String> processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));

        // case1: single sensitive word replaced
        List<String> out1 = processor.transform("Hello badword world|badword|***", new HashMap<>());
        Assert.assertEquals(1, out1.size());
        Assert.assertEquals("result=Hello *** world", out1.get(0));

        // case2: word not present – text unchanged
        List<String> out2 = processor.transform("Hello clean world|badword|***", new HashMap<>());
        Assert.assertEquals(1, out2.size());
        Assert.assertEquals("result=Hello clean world", out2.get(0));

        // case3: multiple occurrences all replaced
        List<String> out3 = processor.transform("badword is bad and badword again|badword|***", new HashMap<>());
        Assert.assertEquals(1, out3.size());
        Assert.assertEquals("result=*** is bad and *** again", out3.get(0));
    }

    @Test
    public void testFastModeMultipleWords() throws Exception {
        String sql = "select sensitive_word_filter(string1, string2, string3, 'fast') from source";
        TransformConfig config = new TransformConfig(sql);
        TransformProcessor<String, String> processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));

        // case1: two sensitive words, both present
        List<String> out1 = processor.transform(
                "He said word1 and word2|word1,word2|***", new HashMap<>());
        Assert.assertEquals(1, out1.size());
        Assert.assertEquals("result=He said *** and ***", out1.get(0));

        // case2: only one of two words present
        List<String> out2 = processor.transform(
                "He said word1 only|word1,word2|***", new HashMap<>());
        Assert.assertEquals(1, out2.size());
        Assert.assertEquals("result=He said *** only", out2.get(0));
    }

    @Test
    public void testFastModeEdgeCases() throws Exception {
        String sql = "select sensitive_word_filter(string1, string2, string3, 'fast') from source";
        TransformConfig config = new TransformConfig(sql);
        TransformProcessor<String, String> processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));

        // case1: empty sensitive-word list – text unchanged
        List<String> out1 = processor.transform("Hello world||***", new HashMap<>());
        Assert.assertEquals(1, out1.size());
        Assert.assertEquals("result=Hello world", out1.get(0));

        // case2: empty text
        List<String> out2 = processor.transform("|badword|***", new HashMap<>());
        Assert.assertEquals(1, out2.size());
        Assert.assertEquals("result=", out2.get(0));

        // case3: overlapping patterns – leftmost match wins
        List<String> out3 = processor.transform("abcde|abc,bcd|***", new HashMap<>());
        Assert.assertEquals(1, out3.size());
        Assert.assertEquals("result=***de", out3.get(0));
    }

    // ------------------------------------------------------------------
    // Auto-selection (no mode argument)
    // ------------------------------------------------------------------

    @Test
    public void testAutoModeShortText() throws Exception {
        // Three-argument form: text length < 200 → 'normal' mode is chosen automatically
        String sql = "select sensitive_word_filter(string1, string2, string3) from source";
        TransformConfig config = new TransformConfig(sql);
        TransformProcessor<String, String> processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));

        List<String> out = processor.transform("Hello badword world|badword|***", new HashMap<>());
        Assert.assertEquals(1, out.size());
        Assert.assertEquals("result=Hello *** world", out.get(0));
    }

    @Test
    public void testAutoModeLongText() throws Exception {
        // Three-argument form: text length >= 200 → 'fast' mode is chosen automatically
        String sql = "select sensitive_word_filter(string1, string2, string3) from source";
        TransformConfig config = new TransformConfig(sql);
        TransformProcessor<String, String> processor = TransformProcessor
                .create(config, SourceDecoderFactory.createCsvDecoder(csvSource),
                        SinkEncoderFactory.createKvEncoder(kvSink));

        // Build a text that is longer than 200 characters and contains a sensitive word
        String longText = "This is a very long article that contains a sensitive badword somewhere in the "
                + "middle of the text so that the auto-selection logic picks the fast Aho-Corasick mode "
                + "for efficient multi-pattern replacement across the entire document.";
        List<String> out = processor.transform(longText + "|badword|***", new HashMap<>());
        Assert.assertEquals(1, out.size());
        String expected = longText.replace("badword", "***");
        Assert.assertEquals("result=" + expected, out.get(0));
    }

    // ------------------------------------------------------------------
    // Consistency: both modes produce the same output
    // ------------------------------------------------------------------

    @Test
    public void testNormalAndFastProduceSameResult() throws Exception {
        String sqlNormal =
                "select sensitive_word_filter(string1, string2, string3, 'normal') from source";
        String sqlFast =
                "select sensitive_word_filter(string1, string2, string3, 'fast') from source";

        TransformConfig cfgNormal = new TransformConfig(sqlNormal);
        TransformConfig cfgFast = new TransformConfig(sqlFast);

        TransformProcessor<String, String> procNormal = TransformProcessor.create(
                cfgNormal,
                SourceDecoderFactory.createCsvDecoder(csvSource),
                SinkEncoderFactory.createKvEncoder(kvSink));
        TransformProcessor<String, String> procFast = TransformProcessor.create(
                cfgFast,
                SourceDecoderFactory.createCsvDecoder(csvSource),
                SinkEncoderFactory.createKvEncoder(kvSink));

        String input = "He said word1 and word2, but word1 is worse|word1,word2|***";
        List<String> outNormal = procNormal.transform(input, new HashMap<>());
        List<String> outFast = procFast.transform(input, new HashMap<>());

        Assert.assertEquals(1, outNormal.size());
        Assert.assertEquals(1, outFast.size());
        Assert.assertEquals(outNormal.get(0), outFast.get(0));
    }
}
