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
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static com.bioraft.rundeck.rancher.RancherShared.ensureStringIsJsonArray;
import static com.bioraft.rundeck.rancher.RancherShared.ensureStringIsJsonObject;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for Nexus3OptionProvider.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-11
 */
@RunWith(MockitoJUnitRunner.class)
public class RancherSharedTest {
    @Test
    public void testArrayWrapping() {
        String string = "\"a\": 1";
        assertEquals(wrapArray(string), ensureStringIsJsonArray(string));

        assertEquals(wrapArray(string), ensureStringIsJsonArray("[" + string + "]"));

        assertEquals(wrapArray(string), ensureStringIsJsonArray("\r\n\t[" + string + "] \n "));
    }

    @Test
    public void testObjectWrapping() {
        String string = "\"a\": 1";
        assertEquals(wrapObject(string), ensureStringIsJsonObject(string));

        assertEquals(wrapObject(string), ensureStringIsJsonObject("{" + string + "}"));

        assertEquals(wrapObject(string), ensureStringIsJsonObject(" \n \t\r{" + string + "} \n\t"));
    }

    private String wrapArray(String string) {
        return "[" + string + "]";
    }

    private String wrapObject(String string) {
        return "{" + string + "}";
    }
}
