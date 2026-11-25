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

package com.hazelcast.internal.serialization.impl.compact.integration;

import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import com.hazelcast.nio.serialization.genericrecord.GenericRecordBuilder;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Member/client tests for the reflective Compact serialization restrictions backported for
 * CVE-2026-107726.
 * <p>
 * A client is free to write a {@link GenericRecord} with an arbitrary type name. Before the fix, a
 * member reading such a record would treat the type name as a class name and reflectively
 * materialise that class, even if it is an internal or unsafe type. These tests simulate such a
 * client and verify that, for restricted classes, the member falls back to a {@link GenericRecord}
 * instead, while allowed classes continue to be deserialized to their classes.
 * <p>
 * Not from upstream: written for the 5.2.5 backport. Upstream's equivalent
 * ({@code NestedReflectiveRestrictionTest}) depends on the allow/block-list configuration API that
 * this backport does not include.
 */
@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class ReflectiveRestrictionIntegrationTest extends HazelcastTestSupport {

    private static final String[] RESTRICTED_TYPE_NAMES = {
            // internal classes on the default blocklist
            "com.hazelcast.internal.serialization.impl.HeapData",
            "com.hazelcast.internal.nio.Packet",
            // package-private class, only reachable by name
            "com.hazelcast.internal.serialization.impl.UnsafeObjectDataInput",
            // always-on JDK library blocklist
            "java.util.ArrayList",
    };

    private final TestHazelcastFactory factory = new TestHazelcastFactory();

    private HazelcastInstance member;
    private HazelcastInstance client;

    @Before
    public void setUp() {
        member = factory.newHazelcastInstance(smallInstanceConfigWithoutJetAndMetrics());
        client = factory.newHazelcastClient();
    }

    @After
    public void tearDown() {
        factory.terminateAll();
    }

    @Test
    public void testReadingRecord_whenTypeNameIsRestricted() {
        IMap<Integer, Object> clientMap = client.getMap("restricted");
        IMap<Integer, Object> memberMap = member.getMap("restricted");

        for (int i = 0; i < RESTRICTED_TYPE_NAMES.length; i++) {
            String typeName = RESTRICTED_TYPE_NAMES[i];
            clientMap.set(i, GenericRecordBuilder.compact(typeName).setInt32("m", 42).build());

            Object fromMember = memberMap.get(i);
            assertTrue(typeName + " must be read as GenericRecord by the member, but was "
                    + (fromMember == null ? null : fromMember.getClass()), fromMember instanceof GenericRecord);
            assertEquals(typeName, 42, ((GenericRecord) fromMember).getInt32("m"));

            Object fromClient = clientMap.get(i);
            assertTrue(typeName + " must be read as GenericRecord by the client, but was "
                    + (fromClient == null ? null : fromClient.getClass()), fromClient instanceof GenericRecord);
        }
    }

    @Test
    public void testReadingRecord_whenTypeNameIsAllowed() {
        IMap<Integer, Object> clientMap = client.getMap("allowed");
        IMap<Integer, Object> memberMap = member.getMap("allowed");

        // written as an object by the client
        SimpleDto dto = new SimpleDto(7);
        clientMap.set(0, dto);
        assertEquals(dto, memberMap.get(0));
        assertEquals(dto, clientMap.get(0));

        // written as a GenericRecord named after an allowed class
        clientMap.set(1, GenericRecordBuilder.compact(SimpleDto.class.getName()).setInt32("m", 8).build());
        assertEquals(new SimpleDto(8), memberMap.get(1));
    }

    @Test
    public void testReadingRecord_whenNestedTypeNameIsRestricted() {
        IMap<Integer, Object> clientMap = client.getMap("nested");
        IMap<Integer, Object> memberMap = member.getMap("nested");

        GenericRecord restrictedInner = GenericRecordBuilder.compact("java.util.ArrayList").setInt32("m", 1).build();
        clientMap.set(0, GenericRecordBuilder.compact(OuterDto.class.getName())
                .setString("other", "x")
                .setGenericRecord("inner", restrictedInner)
                .build());

        Object fromMember = memberMap.get(0);
        assertTrue("outer value containing a restricted nested type must be read as GenericRecord, but was "
                + (fromMember == null ? null : fromMember.getClass()), fromMember instanceof GenericRecord);
        GenericRecord outer = (GenericRecord) fromMember;
        assertEquals("x", outer.getString("other"));
        GenericRecord inner = outer.getGenericRecord("inner");
        assertNotNull(inner);
        assertEquals(1, inner.getInt32("m"));
    }

    @Test
    public void testReadingRecord_whenNestedTypeNameIsAllowed() {
        IMap<Integer, Object> clientMap = client.getMap("nestedAllowed");
        IMap<Integer, Object> memberMap = member.getMap("nestedAllowed");

        GenericRecord allowedInner = GenericRecordBuilder.compact(SimpleDto.class.getName()).setInt32("m", 5).build();
        clientMap.set(0, GenericRecordBuilder.compact(OuterDto.class.getName())
                .setString("other", "y")
                .setGenericRecord("inner", allowedInner)
                .build());

        assertEquals(new OuterDto(new SimpleDto(5), "y"), memberMap.get(0));
    }

    public static class SimpleDto {
        private int m;

        @SuppressWarnings("unused")
        public SimpleDto() {
        }

        public SimpleDto(int m) {
            this.m = m;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            return m == ((SimpleDto) o).m;
        }

        @Override
        public int hashCode() {
            return Objects.hash(m);
        }
    }

    public static class OuterDto {
        private SimpleDto inner;
        private String other;

        @SuppressWarnings("unused")
        public OuterDto() {
        }

        public OuterDto(SimpleDto inner, String other) {
            this.inner = inner;
            this.other = other;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            OuterDto that = (OuterDto) o;
            return Objects.equals(inner, that.inner) && Objects.equals(other, that.other);
        }

        @Override
        public int hashCode() {
            return Objects.hash(inner, other);
        }
    }
}
