package io.yak.ops.business.governance.rule.result;

import java.util.List;

public class RuleResult {

    private final boolean passed;
    private final Severity severity;
    private final List<RuleFinding> findings;

    public RuleResult(boolean passed, Severity severity, List<RuleFinding> findings) {
        this.passed = passed;
        this.severity = severity;
        this.findings = findings;
    }

    public boolean isPassed() { return passed; }
    public Severity getSeverity() { return severity; }
    public List<RuleFinding> getFindings() { return findings; }
}
