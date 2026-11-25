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

package com.hazelcast.serialization.compact.record;

import com.hazelcast.config.SerializationConfig;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.internal.serialization.InternalSerializationService;
import com.hazelcast.internal.serialization.impl.DefaultSerializationServiceBuilder;
import com.hazelcast.internal.serialization.impl.compact.CompactTestUtil;
import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import com.hazelcast.nio.serialization.genericrecord.GenericRecordBuilder;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that reading Compact data into Java records respects the reflective Compact serialization
 * restrictions backported for CVE-2026-107726: when the data names a restricted class, the value is read as
 * a {@link GenericRecord} instead of materialising that class, including when the restricted type is
 * nested inside an otherwise allowed record.
 * <p>
 * Not from upstream: written for the 5.2.5 backport. It checks the Java record read path, which upstream
 * changed in {@code JavaRecordReader} and {@code JavaRecordSerializer} and this backport did not port.
 */
@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class RecordSerializationRestrictionTest extends HazelcastTestSupport {

    private InternalSerializationService service;

    @Before
    public void createSerializationService() {
        service = new DefaultSerializationServiceBuilder()
                .setSchemaService(CompactTestUtil.createInMemorySchemaService())
                .setConfig(new SerializationConfig())
                .build();
    }

    @Test
    public void testReadingRecord_whenTypeNameIsRestricted() {
        GenericRecord restricted = GenericRecordBuilder.compact("java.util.ArrayList").setInt32("m", 1).build();

        Object read = roundTrip(restricted);

        assertThat(read).isInstanceOf(GenericRecord.class);
        assertThat(((GenericRecord) read).getInt32("m")).isEqualTo(1);
    }

    @Test
    public void testReadingRecord_whenNestedTypeNameIsRestricted() {
        GenericRecord restrictedInner = GenericRecordBuilder.compact("java.util.ArrayList").setInt32("m", 1).build();
        GenericRecord outer = GenericRecordBuilder.compact(Outer.class.getName())
                .setString("other", "x")
                .setGenericRecord("inner", restrictedInner)
                .build();

        Object read = roundTrip(outer);

        assertThat(read).isInstanceOf(GenericRecord.class);
        GenericRecord readOuter = (GenericRecord) read;
        assertThat(readOuter.getString("other")).isEqualTo("x");
        assertThat(readOuter.getGenericRecord("inner").getInt32("m")).isEqualTo(1);
    }

    @Test
    public void testReadingRecord_whenNestedTypeNameIsAllowed() {
        GenericRecord allowedInner = GenericRecordBuilder.compact(Inner.class.getName()).setInt32("m", 5).build();
        GenericRecord outer = GenericRecordBuilder.compact(Outer.class.getName())
                .setString("other", "y")
                .setGenericRecord("inner", allowedInner)
                .build();

        Object read = roundTrip(outer);

        assertThat(read).isEqualTo(new Outer(new Inner(5), "y"));
    }

    private Object roundTrip(GenericRecord record) {
        Data data = service.toData(record);
        return service.toObject(data);
    }

    public record Inner(int m) {
    }

    public record Outer(Inner inner, String other) {
    }
}
