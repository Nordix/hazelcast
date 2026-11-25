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

package com.hazelcast.internal.serialization.impl;

import com.hazelcast.config.ClassFilter;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Extra, programmatically-supplied restrictions applied to reflective (zero-config)
 * Compact serialization, on top of the always-on built-in restrictions.
 * <p>
 * CVE-2026-107726 backport [adapted from upstream 361979da]: upstream models this
 * as a Java {@code record}. This project targets a Java 8 baseline, so it is implemented
 * here as a plain immutable class with the equivalent accessors.
 */
public final class ExtraReflectiveCompactSerializationRestrictions {

    public static final ExtraReflectiveCompactSerializationRestrictions NO_RESTRICTIONS =
            new ExtraReflectiveCompactSerializationRestrictions(null, null, null);

    @Nullable
    private final ClassFilter propertyAllowList;
    @Nullable
    private final ClassFilter propertyBlockList;
    @Nullable
    private final ClassFilter defaultBlockList;

    public ExtraReflectiveCompactSerializationRestrictions(@Nullable ClassFilter propertyAllowList,
                                                           @Nullable ClassFilter propertyBlockList,
                                                           @Nullable ClassFilter defaultBlockList) {
        this.propertyAllowList = propertyAllowList;
        this.propertyBlockList = propertyBlockList;
        this.defaultBlockList = defaultBlockList;
    }

    @Nullable
    public ClassFilter propertyAllowList() {
        return propertyAllowList;
    }

    @Nullable
    public ClassFilter propertyBlockList() {
        return propertyBlockList;
    }

    @Nullable
    public ClassFilter defaultBlockList() {
        return defaultBlockList;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExtraReflectiveCompactSerializationRestrictions that = (ExtraReflectiveCompactSerializationRestrictions) o;
        return Objects.equals(propertyAllowList, that.propertyAllowList)
                && Objects.equals(propertyBlockList, that.propertyBlockList)
                && Objects.equals(defaultBlockList, that.defaultBlockList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyAllowList, propertyBlockList, defaultBlockList);
    }

    @Override
    public String toString() {
        return "ExtraReflectiveCompactSerializationRestrictions{"
                + "propertyAllowList=" + propertyAllowList
                + ", propertyBlockList=" + propertyBlockList
                + ", defaultBlockList=" + defaultBlockList
                + '}';
    }
}
