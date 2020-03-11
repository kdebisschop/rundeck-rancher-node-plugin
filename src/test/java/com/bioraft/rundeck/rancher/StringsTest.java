/*
 * Copyright 2019 BioRAFT, Inc. (http://bioraft.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bioraft.rundeck.rancher;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;

/**
 * Tests for Nexus3OptionProvider.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-11
 */
@RunWith(MockitoJUnitRunner.class)
public class StringsTest {

    private Strings strings;

    @Before
    public void setup() {
        this.strings = new Strings();
    }

    @Test
    public void testObjectWrapping() {
        String string = "\"a\": 1";
        assertEquals(wrapObject(string), strings.ensureStringIsJsonObject(string));

        assertEquals(wrapObject(string), strings.ensureStringIsJsonObject("{" + string + "}"));

        assertEquals(wrapObject(string), strings.ensureStringIsJsonObject(" \n \t\r{" + string + "} \n\t"));

        assertEquals("", strings.ensureStringIsJsonObject("  "));

        assertEquals("", strings.ensureStringIsJsonObject(null));
    }

    @Test
    public void testArrayWrapping() {
        String string = "\"a\": 1";
        assertEquals(wrapArray(string), strings.ensureStringIsJsonArray(string));

        assertEquals(wrapArray(string), strings.ensureStringIsJsonArray("[" + string + "]"));

        assertEquals(wrapArray(string), strings.ensureStringIsJsonArray("\r\n\t[" + string + "] \n "));

        assertEquals("", strings.ensureStringIsJsonArray("  "));

        assertEquals("", strings.ensureStringIsJsonArray(null));
    }

    @Test
    public void testMountSplitter() {
        testOneMount("/mount/point", "/local", "");
        testOneMount("/mount", "/local/storage",":ro");
    }

    private void testOneMount(String mountPoint, String local, String options) {
        assertEquals(mountPoint, strings.mountPoint(local + ":" + mountPoint + options));
    }

    private String wrapArray(String string) {
        return "[" + string + "]";
    }

    private String wrapObject(String string) {
        return "{" + string + "}";
    }
}
