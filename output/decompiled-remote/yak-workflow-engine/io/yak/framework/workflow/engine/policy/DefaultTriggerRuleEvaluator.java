/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.policy;

import io.yak.framework.workflow.engine.definition.TriggerRule;
import io.yak.framework.workflow.engine.execution.NodeExecution;
import io.yak.framework.workflow.engine.policy.TriggerRuleEvaluator;
import java.util.Collection;

public final class DefaultTriggerRuleEvaluator
implements TriggerRuleEvaluator {
    @Override
    public boolean isSatisfied(TriggerRule triggerRule, Collection<NodeExecution> predecessors) {
        return switch (triggerRule) {
            default -> throw new MatchException(null, null);
            case TriggerRule.ALL_SUCCESS -> predecessors.stream().allMatch(NodeExecution::isEffectiveSuccess);
            case TriggerRule.ALL_DONE -> predecessors.stream().allMatch(node -> node.status().isTerminal());
            case TriggerRule.NONE_FAILED -> predecessors.stream().noneMatch(NodeExecution::isFailureLike);
            case TriggerRule.ONE_SUCCESS -> predecessors.stream().anyMatch(NodeExecution::isEffectiveSuccess);
            case TriggerRule.ALWAYS -> true;
        };
    }
}

