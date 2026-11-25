/*
 * Copyright (c) 2008-2022, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.config;

import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link ClassFilter#add(ClassFilter)}, added by the CVE-2026-107726 backport.
 * <p>
 * Not from upstream: written for the 5.2.5 backport.
 */
@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class ClassFilterTest {

    @Test
    public void testAdd_whenOtherHasClassesPackagesAndPrefixes() {
        ClassFilter filter = new ClassFilter().addClasses("a.A").addPackages("pkg.one").addPrefixes("pre.one");
        ClassFilter other = new ClassFilter().addClasses("b.B").addPackages("pkg.two").addPrefixes("pre.two");

        filter.add(other);

        assertEquals(2, filter.getClasses().size());
        assertTrue(filter.getClasses().contains("a.A"));
        assertTrue(filter.getClasses().contains("b.B"));
        assertEquals(2, filter.getPackages().size());
        assertTrue(filter.getPackages().contains("pkg.two"));
        assertEquals(2, filter.getPrefixes().size());
        assertTrue(filter.getPrefixes().contains("pre.two"));
        assertTrue(filter.isListed("b.B"));
        assertTrue(filter.isListed("pkg.two.Whatever"));
        assertTrue(filter.isListed("pre.two.Whatever"));
    }

    @Test
    public void testAdd_doesNotModifyTheOtherFilter() {
        ClassFilter filter = new ClassFilter().addClasses("a.A");
        ClassFilter other = new ClassFilter().addClasses("b.B");

        filter.add(other);

        assertEquals(1, other.getClasses().size());
        assertTrue(other.getClasses().contains("b.B"));
        assertTrue(other.getPackages().isEmpty());
        assertTrue(other.getPrefixes().isEmpty());
    }

    @Test
    public void testAdd_returnsThisFilter() {
        ClassFilter filter = new ClassFilter();
        assertSame(filter, filter.add(new ClassFilter().addClasses("b.B")));
    }

    @Test
    public void testAdd_whenOtherIsEmpty() {
        ClassFilter filter = new ClassFilter().addClasses("a.A");

        filter.add(new ClassFilter());

        assertEquals(1, filter.getClasses().size());
    }
}
