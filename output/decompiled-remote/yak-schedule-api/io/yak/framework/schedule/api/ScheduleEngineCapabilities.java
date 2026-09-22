/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.schedule.api;

import io.yak.framework.schedule.api.ConcurrencyPolicy;
import io.yak.framework.schedule.api.MisfirePolicy;
import io.yak.framework.schedule.api.TriggerType;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public record ScheduleEngineCapabilities(Set<TriggerType> triggerTypes, Set<ConcurrencyPolicy> concurrencyPolicies, Set<MisfirePolicy> misfirePolicies, boolean pauseResume, boolean runNow, boolean timezone, boolean dynamicCreate) {
    public ScheduleEngineCapabilities {
        triggerTypes = ScheduleEngineCapabilities.immutableEnumSet(triggerTypes, TriggerType.class);
        concurrencyPolicies = ScheduleEngineCapabilities.immutableEnumSet(concurrencyPolicies, ConcurrencyPolicy.class);
        misfirePolicies = ScheduleEngineCapabilities.immutableEnumSet(misfirePolicies, MisfirePolicy.class);
    }

    public boolean supports(TriggerType type) {
        return this.triggerTypes.contains((Object)type);
    }

    public boolean supports(ConcurrencyPolicy policy) {
        return this.concurrencyPolicies.contains((Object)policy);
    }

    public boolean supports(MisfirePolicy policy) {
        return this.misfirePolicies.contains((Object)policy);
    }

    private static <E extends Enum<E>> Set<E> immutableEnumSet(Set<E> values, Class<E> enumType) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }
}

