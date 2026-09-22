/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.databind.ObjectMapper
 *  io.yak.framework.schedule.api.ScheduleDefinitionRepository
 *  io.yak.framework.schedule.api.ScheduleEngine
 *  io.yak.framework.schedule.api.ScheduleEngineBindingRepository
 *  io.yak.framework.schedule.api.ScheduleExecutionLogRepository
 *  io.yak.framework.schedule.api.ScheduleManager
 *  io.yak.framework.schedule.api.ScheduleOperationAuditRepository
 *  org.springframework.beans.factory.SmartInitializingSingleton
 *  org.springframework.boot.autoconfigure.AutoConfiguration
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
 *  org.springframework.boot.context.properties.EnableConfigurationProperties
 *  org.springframework.context.ApplicationContext
 *  org.springframework.context.annotation.Bean
 */
package io.yak.framework.schedule.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.framework.schedule.api.ScheduleDefinitionRepository;
import io.yak.framework.schedule.api.ScheduleEngine;
import io.yak.framework.schedule.api.ScheduleEngineBindingRepository;
import io.yak.framework.schedule.api.ScheduleExecutionLogRepository;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.framework.schedule.api.ScheduleOperationAuditRepository;
import io.yak.framework.schedule.core.CurrentOperatorProvider;
import io.yak.framework.schedule.core.DefaultScheduleManager;
import io.yak.framework.schedule.core.InMemoryScheduleDefinitionRepository;
import io.yak.framework.schedule.core.InMemoryScheduleEngineBindingRepository;
import io.yak.framework.schedule.core.InMemoryScheduleExecutionLogRepository;
import io.yak.framework.schedule.core.InMemoryScheduleOperationAuditRepository;
import io.yak.framework.schedule.core.ScheduleEngineRegistry;
import io.yak.framework.schedule.core.ScheduleExecutionDispatcher;
import io.yak.framework.schedule.core.ScheduleProperties;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix="yak.schedule", name={"enabled"}, havingValue="true", matchIfMissing=true)
@EnableConfigurationProperties(value={ScheduleProperties.class})
public class ScheduleCoreAutoConfiguration {
    @Bean(value={"yakScheduleObjectMapper"})
    @ConditionalOnMissingBean(name={"yakScheduleObjectMapper"})
    ObjectMapper yakScheduleObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    @ConditionalOnMissingBean
    ScheduleDefinitionRepository scheduleDefinitionRepository() {
        return new InMemoryScheduleDefinitionRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    ScheduleEngineBindingRepository scheduleEngineBindingRepository() {
        return new InMemoryScheduleEngineBindingRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    ScheduleExecutionLogRepository scheduleExecutionLogRepository(ScheduleProperties properties) {
        return new InMemoryScheduleExecutionLogRepository(Math.max(1, properties.getInMemoryCapacity()));
    }

    @Bean
    @ConditionalOnMissingBean
    ScheduleOperationAuditRepository scheduleOperationAuditRepository(ScheduleProperties properties) {
        return new InMemoryScheduleOperationAuditRepository(Math.max(1, properties.getInMemoryCapacity()));
    }

    @Bean
    @ConditionalOnMissingBean
    CurrentOperatorProvider currentOperatorProvider() {
        return () -> "SYSTEM";
    }

    @Bean
    ScheduleEngineRegistry scheduleEngineRegistry(List<ScheduleEngine> engines) {
        return new ScheduleEngineRegistry(engines);
    }

    @Bean
    ScheduleExecutionDispatcher scheduleExecutionDispatcher(ApplicationContext applicationContext, ScheduleExecutionLogRepository logRepository) {
        return new ScheduleExecutionDispatcher(applicationContext, logRepository);
    }

    @Bean
    @ConditionalOnMissingBean(value={ScheduleManager.class})
    ScheduleManager scheduleManager(ScheduleProperties properties, ScheduleEngineRegistry registry, ScheduleDefinitionRepository definitionRepository, ScheduleOperationAuditRepository auditRepository, CurrentOperatorProvider operatorProvider) {
        return new DefaultScheduleManager(properties, registry, definitionRepository, auditRepository, operatorProvider);
    }

    @Bean
    SmartInitializingSingleton scheduleEngineSelectionValidator(ScheduleProperties properties, ScheduleEngineRegistry registry) {
        return () -> registry.required(properties.getEngine());
    }
}

