/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.execution;

import io.yak.framework.workflow.engine.definition.NodeInputMapping;
import io.yak.framework.workflow.engine.definition.NodeInputReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class NodeInputResolver {
    public Map<String, Object> resolve(NodeInputMapping inputMapping, Map<String, Object> workflowInput, Map<String, Map<String, Object>> predecessorOutputs) {
        Map<Object, Object> predecessors;
        Objects.requireNonNull(inputMapping, "inputMapping");
        Map<Object, Object> workflow = workflowInput == null ? Map.of() : workflowInput;
        Map<Object, Object> map = predecessors = predecessorOutputs == null ? Map.of() : predecessorOutputs;
        if (inputMapping.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap resolved = new LinkedHashMap();
        inputMapping.bindings().forEach((target, reference) -> resolved.put(target, this.resolveReference((String)reference, (Map<String, Object>)workflow, (Map<String, Map<String, Object>>)predecessors)));
        return Collections.unmodifiableMap(resolved);
    }

    private Object resolveReference(String reference, Map<String, Object> workflowInput, Map<String, Map<String, Object>> predecessorOutputs) {
        if (NodeInputReference.isWorkflowReference(reference)) {
            return this.readPath(workflowInput, NodeInputReference.workflowPath(reference));
        }
        String predecessorId = NodeInputReference.matchPredecessor(reference, predecessorOutputs.keySet());
        if (predecessorId == null) {
            return null;
        }
        Map<String, Object> output = predecessorOutputs.get(predecessorId);
        return this.readPath(output, NodeInputReference.predecessorPath(reference, predecessorId));
    }

    private Object readPath(Object root, String path) {
        if (path == null || path.isBlank()) {
            return root;
        }
        Object current = root;
        for (String segment : this.split(path)) {
            if (current instanceof Map) {
                Map map = (Map)current;
                current = map.get(segment);
            } else if (current instanceof List) {
                List list = (List)current;
                Integer index = this.parseIndex(segment);
                if (index == null || index < 0 || index >= list.size()) {
                    return null;
                }
                current = list.get(index);
            } else {
                return null;
            }
            if (current != null) continue;
            return null;
        }
        return current;
    }

    private List<String> split(String path) {
        ArrayList<String> segments = new ArrayList<String>();
        for (String segment : path.split("\\.")) {
            if (segment.isEmpty()) continue;
            segments.add(segment);
        }
        return segments;
    }

    private Integer parseIndex(String segment) {
        try {
            return Integer.valueOf(segment);
        }
        catch (NumberFormatException ignored) {
            return null;
        }
    }
}

