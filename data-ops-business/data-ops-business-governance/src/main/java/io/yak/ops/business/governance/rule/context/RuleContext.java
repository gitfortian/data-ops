package io.yak.ops.business.governance.rule.context;

import java.util.Map;

public class RuleContext {

    private final String assetId;
    private final String domain;
    private final Map<String, Object> facts;

    public RuleContext(String assetId, String domain, Map<String, Object> facts) {
        this.assetId = assetId;
        this.domain = domain;
        this.facts = facts;
    }

    public String getAssetId() {
        return assetId;
    }

    public String getDomain() {
        return domain;
    }

    public Map<String, Object> getFacts() {
        return facts;
    }
}
