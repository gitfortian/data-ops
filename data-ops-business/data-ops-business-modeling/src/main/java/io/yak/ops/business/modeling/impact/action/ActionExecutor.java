package io.yak.ops.business.modeling.impact.action;

public interface ActionExecutor {

    String actionType();

    void execute(GovernanceAction action);
}
