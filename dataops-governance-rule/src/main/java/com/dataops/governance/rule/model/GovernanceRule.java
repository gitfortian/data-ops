package com.dataops.governance.rule.model;

public class GovernanceRule {

    private final String ruleId;
    private final String code;
    private final String name;
    private final RuleType type;
    private final RuleStatus status;
    private final RuleVersion version;

    public GovernanceRule(String ruleId, String code, String name, RuleType type, RuleStatus status, RuleVersion version) {
        this.ruleId = ruleId;
        this.code = code;
        this.name = name;
        this.type = type;
        this.status = status;
        this.version = version;
    }

    public String getRuleId() { return ruleId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public RuleType getType() { return type; }
    public RuleStatus getStatus() { return status; }
    public RuleVersion getVersion() { return version; }
}
