package io.yak.ops.business.modeling.impact.action;

public class GovernanceAction {
    private String actionCode;
    private String impactId;
    private String status;

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getImpactId() {
        return impactId;
    }

    public void setImpactId(String impactId) {
        this.impactId = impactId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
