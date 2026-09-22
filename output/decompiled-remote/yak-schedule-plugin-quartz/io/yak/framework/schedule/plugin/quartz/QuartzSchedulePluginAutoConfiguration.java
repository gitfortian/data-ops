/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.databind.ObjectMapper
 *  io.yak.framework.schedule.api.ScheduleEngine
 *  io.yak.framework.schedule.api.ScheduleEngineBindingRepository
 *  io.yak.framework.schedule.core.ScheduleCoreAutoConfiguration
 *  org.quartz.Scheduler
 *  org.quartz.spi.JobFactory
 *  org.springframework.beans.factory.annotation.Qualifier
 *  org.springframework.beans.factory.config.AutowireCapableBeanFactory
 *  org.springframework.boot.autoconfigure.AutoConfiguration
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnClass
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
 *  org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration
 *  org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer
 *  org.springframework.context.annotation.Bean
 */
package io.yak.framework.schedule.plugin.quartz;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.framework.schedule.api.ScheduleEngine;
import io.yak.framework.schedule.api.ScheduleEngineBindingRepository;
import io.yak.framework.schedule.core.ScheduleCoreAutoConfiguration;
import io.yak.framework.schedule.plugin.quartz.AutowiringQuartzJobFactory;
import io.yak.framework.schedule.plugin.quartz.QuartzScheduleEngine;
import org.quartz.Scheduler;
import org.quartz.spi.JobFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before={ScheduleCoreAutoConfiguration.class}, after={QuartzAutoConfiguration.class})
@ConditionalOnClass(value={Scheduler.class})
@ConditionalOnProperty(prefix="yak.schedule", name={"engine"}, havingValue="quartz", matchIfMissing=true)
public class QuartzSchedulePluginAutoConfiguration {
    @Bean
    SchedulerFactoryBeanCustomizer yakScheduleQuartzJobFactoryCustomizer(AutowireCapableBeanFactory beanFactory) {
        return factory -> factory.setJobFactory((JobFactory)new AutowiringQuartzJobFactory(beanFactory));
    }

    @Bean
    @ConditionalOnMissingBean(name={"quartzScheduleEngine"})
    ScheduleEngine quartzScheduleEngine(Scheduler scheduler, @Qualifier(value="yakScheduleObjectMapper") ObjectMapper objectMapper, ScheduleEngineBindingRepository bindingRepository) {
        return new QuartzScheduleEngine(scheduler, objectMapper, bindingRepository);
    }
}

