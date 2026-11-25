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
import com.hazelcast.internal.serialization.impl.ExtraReflectiveCompactSerializationRestrictions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Centralizes the restriction checks that decide whether a class may be used for
 * reflective (zero-config) Compact serialization.
 * <p>
 * CVE-2026-107726 backport [adapted from upstream 361979da]: this is a port of the upstream
 * {@code ReflectiveClassFilter}. The optional, user-facing allow/block-list
 * configuration path (via {@code CompactSerializationConfig.getZeroConfigFilter()} and
 * {@code ConfigAccessor}) does not exist in this release and is intentionally omitted.
 * The always-on JDK-library blocklist and the programmatic default/property blocklists
 * are retained, which is what provides the secure-by-default protection.
 */
public class ReflectiveClassFilter {

    private static final ClassFilter JDK_LIB_BLOCKLIST = new ClassFilter()
            .addPrefixes("java.", "javax.", "com.sun.", "sun.", "jdk.");

    @Nullable
    private final ClassFilter blockList;
    @Nullable
    private final ClassFilter allowList;

    // We take the union over all configured allowlists and the union over all configured blocklists
    public ReflectiveClassFilter(@Nonnull ExtraReflectiveCompactSerializationRestrictions extraRestrictions) {
        ClassFilter defaultBlockList = extraRestrictions.defaultBlockList();
        blockList = union(JDK_LIB_BLOCKLIST, defaultBlockList, extraRestrictions.propertyBlockList());
        allowList = union(extraRestrictions.propertyAllowList());
    }

    private static ClassFilter union(ClassFilter... filters) {
        ClassFilter result = null;
        for (ClassFilter filter : filters) {
            if (filter != null) {
                result = result == null ? new ClassFilter(filter) : result.add(filter);
            }
        }
        return result;
    }

    /**
     * A class is restricted from direct reflective serialization if it matches any of the following:
     *
     * <ol>
     *     <li>It is not present on any allowlist</li>
     *     <li>It is present on any blocklist</li>
     *     <li>It is a primitive, array or Void</li>
     * </ol>
     *
     * @param clazz The class to test
     * @return Whether the class is restricted
     */
    public boolean isRestricted(@Nonnull Class<?> clazz) {
        // CVE-2026-107726 backport [adapted from upstream 361979da: upstream tests clazz.getPackage() == null, which
        // on Java 9+ identifies primitives, arrays and Void. On Java 8 it is also null for a user class in the
        // default package, which the zero-config serializer has always supported (see CompactUtil), so those
        // types are tested for explicitly here]
        // Primitive and 1D array fields are supported but these are handled when populating the reader/writers.
        // These classes are never directly associated to a reflective serializer.
        return clazz.isPrimitive() || clazz.isArray() || Void.class.equals(clazz)
                || isOnBlockList(clazz) || !isOnAllowList(clazz);
    }

    private boolean isOnAllowList(Class<?> clazz) {
        return allowList == null || allowList.isListed(clazz.getName());
    }

    private boolean isOnBlockList(Class<?> clazz) {
        return blockList != null && blockList.isListed(clazz.getName());
    }
}
