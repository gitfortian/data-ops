package io.yak.ops.business.modeling.governance;

/**
 * Persistence model for impact governance result.
 */
public class ImpactGovernancePersistenceModel {

    private String impactId;
    private String status;
    private String ruleResult;

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

    public String getRuleResult() {
        return ruleResult;
    }

    public void setRuleResult(String ruleResult) {
        this.ruleResult = ruleResult;
    }
}
