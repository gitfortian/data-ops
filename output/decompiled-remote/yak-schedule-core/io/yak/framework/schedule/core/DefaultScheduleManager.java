/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.yak.framework.schedule.api.ScheduleDefinition
 *  io.yak.framework.schedule.api.ScheduleDefinitionRepository
 *  io.yak.framework.schedule.api.ScheduleEngine
 *  io.yak.framework.schedule.api.ScheduleEngineCapabilities
 *  io.yak.framework.schedule.api.ScheduleKey
 *  io.yak.framework.schedule.api.ScheduleManager
 *  io.yak.framework.schedule.api.ScheduleOperationAudit
 *  io.yak.framework.schedule.api.ScheduleOperationAuditRepository
 *  io.yak.framework.schedule.api.ScheduleSnapshot
 *  io.yak.framework.schedule.api.ScheduleTriggerResult
 *  io.yak.framework.schedule.api.TriggerType
 *  io.yak.framework.schedule.api.UnsupportedScheduleCapabilityException
 */
package io.yak.framework.schedule.core;

import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleDefinitionRepository;
import io.yak.framework.schedule.api.ScheduleEngine;
import io.yak.framework.schedule.api.ScheduleEngineCapabilities;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.framework.schedule.api.ScheduleOperationAudit;
import io.yak.framework.schedule.api.ScheduleOperationAuditRepository;
import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.framework.schedule.api.ScheduleTriggerResult;
import io.yak.framework.schedule.api.TriggerType;
import io.yak.framework.schedule.api.UnsupportedScheduleCapabilityException;
import io.yak.framework.schedule.core.CurrentOperatorProvider;
import io.yak.framework.schedule.core.ScheduleEngineRegistry;
import io.yak.framework.schedule.core.ScheduleProperties;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

public final class DefaultScheduleManager
implements ScheduleManager {
    private final ScheduleProperties properties;
    private final ScheduleEngineRegistry registry;
    private final ScheduleDefinitionRepository definitionRepository;
    private final ScheduleOperationAuditRepository auditRepository;
    private final CurrentOperatorProvider operatorProvider;

    public DefaultScheduleManager(ScheduleProperties properties, ScheduleEngineRegistry registry, ScheduleDefinitionRepository definitionRepository, ScheduleOperationAuditRepository auditRepository, CurrentOperatorProvider operatorProvider) {
        this.properties = properties;
        this.registry = registry;
        this.definitionRepository = definitionRepository;
        this.auditRepository = auditRepository;
        this.operatorProvider = operatorProvider;
    }

    public ScheduleSnapshot save(ScheduleDefinition definition) {
        ScheduleEngine engine = this.engine();
        this.validateCapabilities(engine.capabilities(), definition);
        ScheduleSnapshot snapshot = engine.save(definition);
        this.definitionRepository.save(definition);
        this.audit(definition.key(), "SAVE");
        return snapshot;
    }

    public void pause(ScheduleKey key) {
        this.engine().pause(key);
        this.audit(key, "PAUSE");
    }

    public void resume(ScheduleKey key) {
        this.engine().resume(key);
        this.audit(key, "RESUME");
    }

    public void delete(ScheduleKey key) {
        this.engine().delete(key);
        this.definitionRepository.delete(key);
        this.audit(key, "DELETE");
    }

    public ScheduleTriggerResult runNow(ScheduleKey key) {
        ScheduleTriggerResult result = this.engine().runNow(key);
        this.audit(key, "RUN_NOW");
        return result;
    }

    public Optional<ScheduleSnapshot> get(ScheduleKey key) {
        return this.engine().get(key);
    }

    public List<ScheduleSnapshot> list(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        return this.engine().list(namespace.trim());
    }

    private ScheduleEngine engine() {
        return this.registry.required(this.properties.getEngine());
    }

    private void validateCapabilities(ScheduleEngineCapabilities capabilities, ScheduleDefinition definition) {
        TriggerType triggerType = definition.trigger().type();
        if (!capabilities.supports(triggerType)) {
            throw new UnsupportedScheduleCapabilityException("Engine '" + this.properties.getEngine() + "' does not support trigger type " + String.valueOf(triggerType));
        }
        if (!capabilities.supports(definition.policy().concurrencyPolicy())) {
            throw new UnsupportedScheduleCapabilityException("Engine '" + this.properties.getEngine() + "' does not support concurrency policy " + String.valueOf(definition.policy().concurrencyPolicy()));
        }
        if (!capabilities.supports(definition.policy().misfirePolicy())) {
            throw new UnsupportedScheduleCapabilityException("Engine '" + this.properties.getEngine() + "' does not support misfire policy " + String.valueOf(definition.policy().misfirePolicy()));
        }
        if (triggerType == TriggerType.CRON && !capabilities.timezone() && definition.trigger().zoneId() != null && !ZoneId.systemDefault().equals(definition.trigger().zoneId())) {
            throw new UnsupportedScheduleCapabilityException("Engine '" + this.properties.getEngine() + "' does not support per-task timezone: " + String.valueOf(definition.trigger().zoneId()));
        }
        if (!definition.enabled() && !capabilities.pauseResume()) {
            throw new UnsupportedScheduleCapabilityException("Engine '" + this.properties.getEngine() + "' cannot create a disabled schedule");
        }
    }

    private void audit(ScheduleKey key, String operation) {
        String operator = this.operatorProvider.currentOperator();
        if (operator == null || operator.isBlank()) {
            operator = "SYSTEM";
        }
        this.auditRepository.save(new ScheduleOperationAudit(key, operation, operator, Instant.now()));
    }
}

