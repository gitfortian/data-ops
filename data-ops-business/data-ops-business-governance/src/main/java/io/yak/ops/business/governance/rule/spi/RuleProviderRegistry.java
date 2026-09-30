package io.yak.ops.business.governance.rule.spi;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RuleProviderRegistry {

    private final Map<String, GovernanceRuleProvider> providers = new HashMap<>();

    public void register(GovernanceRuleProvider provider) {
        providers.put(provider.code(), provider);
    }

    public GovernanceRuleProvider get(String code) {
        return providers.get(code);
    }

    public Map<String, GovernanceRuleProvider> all() {
        return Collections.unmodifiableMap(providers);
    }
}
