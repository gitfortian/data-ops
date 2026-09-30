package io.yak.ops.business.governance.rule.result;

public class RuleFinding {

    private final String assetId;
    private final String message;
    private final Severity severity;
    private final String suggestion;

    public RuleFinding(String assetId, String message, Severity severity, String suggestion) {
        this.assetId = assetId;
        this.message = message;
        this.severity = severity;
        this.suggestion = suggestion;
    }

    public String getAssetId() { return assetId; }
    public String getMessage() { return message; }
    public Severity getSeverity() { return severity; }
    public String getSuggestion() { return suggestion; }
}
