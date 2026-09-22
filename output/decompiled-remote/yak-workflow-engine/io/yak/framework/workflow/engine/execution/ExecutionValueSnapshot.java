/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ExecutionValueSnapshot {
    private ExecutionValueSnapshot() {
    }

    public static Map<String, Object> immutableMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap copy = new LinkedHashMap();
        source.forEach((key, value) -> copy.put(key, ExecutionValueSnapshot.snapshot(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object snapshot(Object value) {
        if (value instanceof Map) {
            Map map = (Map)value;
            LinkedHashMap copy = new LinkedHashMap();
            map.forEach((key, nestedValue) -> copy.put(key, ExecutionValueSnapshot.snapshot(nestedValue)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List) {
            List list = (List)value;
            ArrayList copy = new ArrayList(list.size());
            list.forEach(item -> copy.add(ExecutionValueSnapshot.snapshot(item)));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Set) {
            Set set = (Set)value;
            LinkedHashSet copy = new LinkedHashSet();
            set.forEach(item -> copy.add(ExecutionValueSnapshot.snapshot(item)));
            return Collections.unmodifiableSet(copy);
        }
        return value;
    }
}

