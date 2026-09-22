/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.policy;

import io.yak.framework.workflow.engine.definition.TriggerRule;
import io.yak.framework.workflow.engine.execution.NodeExecution;
import java.util.Collection;

@FunctionalInterface
public interface TriggerRuleEvaluator {
    public boolean isSatisfied(TriggerRule var1, Collection<NodeExecution> var2);
}

