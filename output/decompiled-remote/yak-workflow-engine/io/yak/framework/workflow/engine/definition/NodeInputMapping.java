/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.definition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class NodeInputMapping {
    private static final NodeInputMapping NONE = new NodeInputMapping(Map.of());
    private final Map<String, String> bindings;

    private NodeInputMapping(Map<String, String> bindings) {
        LinkedHashMap normalized = new LinkedHashMap();
        if (bindings != null) {
            bindings.forEach((target, reference) -> {
                String targetName = NodeInputMapping.requireText(target, "target");
                String sourceReference = NodeInputMapping.requireText(reference, "reference");
                normalized.put(targetName, sourceReference);
            });
        }
        this.bindings = Collections.unmodifiableMap(normalized);
    }

    public static NodeInputMapping none() {
        return NONE;
    }

    public static NodeInputMapping of(Map<String, String> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return NONE;
        }
        return new NodeInputMapping(bindings);
    }

    public Map<String, String> bindings() {
        return this.bindings;
    }

    public boolean isEmpty() {
        return this.bindings.isEmpty();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}

