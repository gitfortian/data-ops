/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.definition;

import io.yak.framework.workflow.engine.definition.NodeFailurePolicy;
import io.yak.framework.workflow.engine.definition.NodeInputMapping;
import io.yak.framework.workflow.engine.definition.NodeTimeoutPolicy;
import io.yak.framework.workflow.engine.definition.RetryPolicy;
import io.yak.framework.workflow.engine.definition.TriggerRule;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record NodeDefinition(String id, String name, TriggerRule triggerRule, RetryPolicy retryPolicy, NodeFailurePolicy failurePolicy, NodeTimeoutPolicy timeoutPolicy, NodeInputMapping inputMapping, Map<String, Object> configuration) {
    public NodeDefinition {
        id = NodeDefinition.requireText(id, "id");
        name = name == null || name.isBlank() ? id : name;
        triggerRule = Objects.requireNonNullElse(triggerRule, TriggerRule.ALL_SUCCESS);
        retryPolicy = Objects.requireNonNullElseGet(retryPolicy, RetryPolicy::none);
        failurePolicy = Objects.requireNonNullElse(failurePolicy, NodeFailurePolicy.FAIL_WORKFLOW);
        timeoutPolicy = Objects.requireNonNullElseGet(timeoutPolicy, NodeTimeoutPolicy::none);
        inputMapping = Objects.requireNonNullElseGet(inputMapping, NodeInputMapping::none);
        configuration = configuration == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(configuration));
    }

    public NodeDefinition(String id, String name, TriggerRule triggerRule, RetryPolicy retryPolicy, NodeFailurePolicy failurePolicy, NodeTimeoutPolicy timeoutPolicy, Map<String, Object> configuration) {
        this(id, name, triggerRule, retryPolicy, failurePolicy, timeoutPolicy, NodeInputMapping.none(), configuration);
    }

    public NodeDefinition(String id, String name, TriggerRule triggerRule, RetryPolicy retryPolicy, NodeFailurePolicy failurePolicy, Map<String, Object> configuration) {
        this(id, name, triggerRule, retryPolicy, failurePolicy, NodeTimeoutPolicy.none(), NodeInputMapping.none(), configuration);
    }

    public static NodeDefinition task(String id) {
        return new NodeDefinition(id, id, TriggerRule.ALL_SUCCESS, RetryPolicy.none(), NodeFailurePolicy.FAIL_WORKFLOW, NodeTimeoutPolicy.none(), NodeInputMapping.none(), Map.of());
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}

