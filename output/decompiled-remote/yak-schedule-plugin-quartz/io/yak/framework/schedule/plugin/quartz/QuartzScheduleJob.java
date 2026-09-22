/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.databind.ObjectMapper
 *  io.yak.framework.schedule.api.ScheduleDefinition
 *  io.yak.framework.schedule.api.ScheduleExecutionContext
 *  io.yak.framework.schedule.core.ScheduleExecutionDispatcher
 *  org.quartz.Job
 *  org.quartz.JobDataMap
 *  org.quartz.JobExecutionContext
 *  org.quartz.JobExecutionException
 *  org.springframework.beans.factory.annotation.Autowired
 *  org.springframework.beans.factory.annotation.Qualifier
 */
package io.yak.framework.schedule.plugin.quartz;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleExecutionContext;
import io.yak.framework.schedule.core.ScheduleExecutionDispatcher;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class QuartzScheduleJob
implements Job {
    @Autowired
    private ScheduleExecutionDispatcher dispatcher;
    @Autowired
    @Qualifier(value="yakScheduleObjectMapper")
    private ObjectMapper objectMapper;

    public void execute(JobExecutionContext quartzContext) throws JobExecutionException {
        JobDataMap data = quartzContext.getMergedJobDataMap();
        try {
            ScheduleDefinition definition = (ScheduleDefinition)this.objectMapper.readValue(data.getString("yak.schedule.definition"), ScheduleDefinition.class);
            String manualTriggerId = data.getString("yak.schedule.manual-trigger-id");
            boolean manual = manualTriggerId != null && !manualTriggerId.isBlank();
            String triggerId = manual ? manualTriggerId : UUID.randomUUID().toString();
            this.dispatcher.dispatch(new ScheduleExecutionContext(triggerId, definition.key(), "quartz", definition.target().handler(), definition.target().payload(), QuartzScheduleJob.instant(quartzContext.getScheduledFireTime()), QuartzScheduleJob.instant(quartzContext.getFireTime()), manual, quartzContext.getRefireCount() + 1));
        }
        catch (Exception exception) {
            JobExecutionException failure = new JobExecutionException((Throwable)exception);
            int maxRetries = this.maxRetries(data);
            failure.setRefireImmediately(quartzContext.getRefireCount() < maxRetries);
            throw failure;
        }
    }

    private int maxRetries(JobDataMap data) {
        try {
            ScheduleDefinition definition = (ScheduleDefinition)this.objectMapper.readValue(data.getString("yak.schedule.definition"), ScheduleDefinition.class);
            return definition.policy().triggerRetries();
        }
        catch (Exception ignored) {
            return 0;
        }
    }

    private static Instant instant(Date value) {
        return value == null ? Instant.now() : value.toInstant();
    }
}

