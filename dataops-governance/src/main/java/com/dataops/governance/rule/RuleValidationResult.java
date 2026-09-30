package com.dataops.governance.rule;

public class RuleValidationResult {
    private boolean valid;
    private String message;

    public RuleValidationResult(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
    }

    public boolean isValid() { return valid; }
    public String getMessage() { return message; }
}
