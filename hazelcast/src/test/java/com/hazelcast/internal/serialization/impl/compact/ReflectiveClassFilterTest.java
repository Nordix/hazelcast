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

package com.hazelcast.internal.serialization.impl.compact;

import com.hazelcast.config.ClassFilter;
import com.hazelcast.internal.nio.Packet;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.internal.serialization.SerializationService;
import com.hazelcast.internal.serialization.impl.ExtraReflectiveCompactSerializationRestrictions;
import com.hazelcast.internal.serialization.impl.HeapData;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link ReflectiveClassFilter}, which decides whether a class may be used for reflective
 * (zero-config) Compact serialization. Backported for CVE-2026-107726.
 * <p>
 * The security property under test: internal/JDK/unsafe classes must be restricted from
 * reflective Compact serialization (which otherwise allows a low-privileged client to read
 * arbitrary member memory), while ordinary user classes continue to work.
 * <p>
 * Not from upstream: written for the 5.2.5 backport. Upstream's equivalent tests use Java 16+ features
 * and the allow/block-list configuration API that this backport does not include. The allow-list and
 * block-list tests use the programmatic {@link ExtraReflectiveCompactSerializationRestrictions} instead.
 */
@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class ReflectiveClassFilterTest {

    private static final String TEST_PACKAGE = "com.hazelcast.internal.serialization.impl.compact";

    /** Mirror of the production default blocklist in DefaultSerializationServiceBuilder. */
    private static ClassFilter defaultBlockList() {
        ClassFilter blockList = new ClassFilter();
        blockList.addPrefixes("com.hazelcast.shaded");
        blockList.addPackages("org.jctools.queues");
        blockList.addClasses(
                HeapData.class.getName(),
                Packet.class.getName(),
                // package-private, so it cannot be referenced as a class literal from here
                "com.hazelcast.internal.serialization.impl.UnsafeObjectDataInput",
                "org.agrona.concurrent.status.AtomicCounter"
        );
        return blockList;
    }

    private static ReflectiveClassFilter defaultFilter() {
        return filter(null, null, defaultBlockList());
    }

    private static ReflectiveClassFilter filter(ClassFilter propertyAllowList, ClassFilter propertyBlockList,
                                                ClassFilter defaultBlockList) {
        return new ReflectiveClassFilter(new ExtraReflectiveCompactSerializationRestrictions(
                propertyAllowList, propertyBlockList, defaultBlockList));
    }

    @Test
    public void testIsRestricted_whenClassIsOnDefaultBlockList() {
        ReflectiveClassFilter filter = defaultFilter();
        assertTrue("HeapData must be restricted", filter.isRestricted(HeapData.class));
        assertTrue("Packet must be restricted", filter.isRestricted(Packet.class));
    }

    @Test
    public void testIsRestricted_whenClassIsInJdkPackage() {
        ReflectiveClassFilter filter = defaultFilter();
        assertTrue("java.* must be restricted", filter.isRestricted(java.util.ArrayList.class));
        assertTrue("javax.* must be restricted", filter.isRestricted(javax.naming.Name.class));
    }

    @Test
    public void testIsNotRestricted_whenOrdinaryUserClass() {
        ReflectiveClassFilter filter = defaultFilter();
        assertFalse("ordinary POJO must not be restricted", filter.isRestricted(SimplePojo.class));
    }

    @Test
    public void testIsRestricted_whenClassIsInJdkPackageAndThereAreNoExtraRestrictions() {
        // Even with NO extra restrictions, the always-on JDK library blocklist applies.
        ReflectiveClassFilter filter =
                new ReflectiveClassFilter(ExtraReflectiveCompactSerializationRestrictions.NO_RESTRICTIONS);
        assertTrue("java.* must be restricted even with no extra config", filter.isRestricted(java.util.HashMap.class));
        assertFalse("ordinary POJO must not be restricted", filter.isRestricted(SimplePojo.class));
    }

    @Test
    public void testIsRestricted_whenClassIsPrimitiveArrayOrVoid() {
        ReflectiveClassFilter filter = defaultFilter();
        assertTrue(filter.isRestricted(int.class));
        assertTrue(filter.isRestricted(int[].class));
        assertTrue(filter.isRestricted(SimplePojo[].class));
        assertTrue(filter.isRestricted(Void.class));
    }

    @Test
    public void testIsNotRestricted_whenClassIsInDefaultPackage() throws IOException {
        // On Java 8 getPackage() is null for a class in the default package, which the zero-config serializer
        // has always supported; the filter must not mistake it for a primitive, array or Void.
        Class<?> defaultPackageClass = defineClassInDefaultPackage("DefaultPackageDto");
        assertEquals("DefaultPackageDto", defaultPackageClass.getName());

        assertFalse(defaultFilter().isRestricted(defaultPackageClass));
        assertFalse(new ReflectiveClassFilter(ExtraReflectiveCompactSerializationRestrictions.NO_RESTRICTIONS)
                .isRestricted(defaultPackageClass));
    }

    @Test
    public void testIsNotRestricted_whenClassIsOnAllowList() {
        ReflectiveClassFilter filter = filter(new ClassFilter().addClasses(SimplePojo.class.getName()), null, null);
        assertFalse(filter.isRestricted(SimplePojo.class));
    }

    @Test
    public void testIsRestricted_whenClassIsNotOnAllowList() {
        ReflectiveClassFilter filter = filter(new ClassFilter().addClasses(SimplePojo.class.getName()), null, null);
        assertTrue(filter.isRestricted(OtherPojo.class));
    }

    @Test
    public void testIsNotRestricted_whenClassMatchesAllowListPackageOrPrefix() {
        ReflectiveClassFilter byPackage = filter(new ClassFilter().addPackages(TEST_PACKAGE), null, null);
        assertFalse(byPackage.isRestricted(SimplePojo.class));
        assertFalse(byPackage.isRestricted(OtherPojo.class));

        ReflectiveClassFilter byPrefix = filter(new ClassFilter().addPrefixes(TEST_PACKAGE + ".ReflectiveClassFilterTest"),
                null, null);
        assertFalse(byPrefix.isRestricted(SimplePojo.class));
        assertFalse(byPrefix.isRestricted(OtherPojo.class));
    }

    @Test
    public void testIsRestricted_whenAllowListIsEmpty() {
        // an empty allow list is "nothing is allowed", not "no allow list"
        ReflectiveClassFilter filter = filter(new ClassFilter(), null, null);
        assertTrue(filter.isRestricted(SimplePojo.class));
    }

    @Test
    public void testIsRestricted_whenClassIsOnPropertyBlockList() {
        ReflectiveClassFilter filter = filter(null, new ClassFilter().addClasses(SimplePojo.class.getName()), null);
        assertTrue(filter.isRestricted(SimplePojo.class));
        assertFalse(filter.isRestricted(OtherPojo.class));
    }

    @Test
    public void testIsRestricted_whenClassIsOnEitherPropertyOrDefaultBlockList() {
        ReflectiveClassFilter filter = filter(null,
                new ClassFilter().addClasses(SimplePojo.class.getName()),
                new ClassFilter().addClasses(OtherPojo.class.getName()));
        assertTrue("property block list entry", filter.isRestricted(SimplePojo.class));
        assertTrue("default block list entry", filter.isRestricted(OtherPojo.class));
    }

    @Test
    public void testIsRestricted_whenClassIsOnBothAllowListAndBlockList() {
        ReflectiveClassFilter filter = filter(
                new ClassFilter().addPackages(TEST_PACKAGE),
                new ClassFilter().addClasses(SimplePojo.class.getName()),
                null);
        assertTrue("block list wins over allow list", filter.isRestricted(SimplePojo.class));
        assertFalse(filter.isRestricted(OtherPojo.class));
    }

    @Test
    public void testIsRestricted_whenClassIsInJdkPackageEvenIfOnAllowList() {
        // the always-on JDK library blocklist cannot be overridden by an allow list
        ReflectiveClassFilter filter = filter(new ClassFilter().addClasses(java.util.ArrayList.class.getName()), null, null);
        assertTrue(filter.isRestricted(java.util.ArrayList.class));
    }

    @Test
    public void testSerializingClassReflectively_whenTheClassIsNotRestricted() {
        // Confirms the restriction mechanism does not break normal zero-config compact serialization.
        SerializationService ss = CompactTestUtil.createSerializationService();
        SimplePojo original = new SimplePojo(42, "hello");
        Data data = ss.toData(original);
        SimplePojo restored = ss.toObject(data);
        assertEquals(original, restored);
    }

    /**
     * Defines, in a fresh class loader, an empty public class with the given name in the default package.
     * A source file in the default package is not allowed by the project's Checkstyle rules, so the class
     * file is written by hand: magic, version 50, a four-entry constant pool (this class name, this class,
     * java/lang/Object, super class), ACC_PUBLIC | ACC_SUPER, and no interfaces, fields, methods or attributes.
     */
    private static Class<?> defineClassInDefaultPackage(String name) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(0xCAFEBABE);
            out.writeShort(0);
            out.writeShort(50);
            out.writeShort(5);
            out.writeByte(1);
            out.writeUTF(name);
            out.writeByte(7);
            out.writeShort(1);
            out.writeByte(1);
            out.writeUTF("java/lang/Object");
            out.writeByte(7);
            out.writeShort(3);
            out.writeShort(0x0021);
            out.writeShort(2);
            out.writeShort(4);
            out.writeShort(0);
            out.writeShort(0);
            out.writeShort(0);
            out.writeShort(0);
        }
        byte[] code = bytes.toByteArray();
        return new ClassLoader(ReflectiveClassFilterTest.class.getClassLoader()) {
            Class<?> define() {
                return defineClass(name, code, 0, code.length);
            }
        }.define();
    }

    public static class OtherPojo {
    }

    public static class SimplePojo {
        private int n;
        private String s;

        @SuppressWarnings("unused")
        public SimplePojo() {
        }

        public SimplePojo(int n, String s) {
            this.n = n;
            this.s = s;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SimplePojo that = (SimplePojo) o;
            return n == that.n && Objects.equals(s, that.s);
        }

        @Override
        public int hashCode() {
            return Objects.hash(n, s);
        }
    }
}
