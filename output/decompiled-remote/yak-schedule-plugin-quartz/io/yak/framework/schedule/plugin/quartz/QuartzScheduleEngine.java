/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.databind.ObjectMapper
 *  io.yak.framework.schedule.api.ConcurrencyPolicy
 *  io.yak.framework.schedule.api.MisfirePolicy
 *  io.yak.framework.schedule.api.ScheduleDefinition
 *  io.yak.framework.schedule.api.ScheduleEngine
 *  io.yak.framework.schedule.api.ScheduleEngineBinding
 *  io.yak.framework.schedule.api.ScheduleEngineBindingRepository
 *  io.yak.framework.schedule.api.ScheduleEngineCapabilities
 *  io.yak.framework.schedule.api.ScheduleKey
 *  io.yak.framework.schedule.api.ScheduleNotFoundException
 *  io.yak.framework.schedule.api.ScheduleProviderException
 *  io.yak.framework.schedule.api.ScheduleSnapshot
 *  io.yak.framework.schedule.api.ScheduleStatus
 *  io.yak.framework.schedule.api.ScheduleTriggerResult
 *  io.yak.framework.schedule.api.TriggerType
 *  org.quartz.CronScheduleBuilder
 *  org.quartz.JobBuilder
 *  org.quartz.JobDataMap
 *  org.quartz.JobDetail
 *  org.quartz.JobKey
 *  org.quartz.ScheduleBuilder
 *  org.quartz.Scheduler
 *  org.quartz.SchedulerException
 *  org.quartz.SimpleScheduleBuilder
 *  org.quartz.Trigger
 *  org.quartz.Trigger$TriggerState
 *  org.quartz.TriggerBuilder
 *  org.quartz.TriggerKey
 *  org.quartz.impl.matchers.GroupMatcher
 */
package io.yak.framework.schedule.plugin.quartz;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.framework.schedule.api.ConcurrencyPolicy;
import io.yak.framework.schedule.api.MisfirePolicy;
import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleEngine;
import io.yak.framework.schedule.api.ScheduleEngineBinding;
import io.yak.framework.schedule.api.ScheduleEngineBindingRepository;
import io.yak.framework.schedule.api.ScheduleEngineCapabilities;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.framework.schedule.api.ScheduleNotFoundException;
import io.yak.framework.schedule.api.ScheduleProviderException;
import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.framework.schedule.api.ScheduleStatus;
import io.yak.framework.schedule.api.ScheduleTriggerResult;
import io.yak.framework.schedule.api.TriggerType;
import io.yak.framework.schedule.plugin.quartz.NonConcurrentQuartzScheduleJob;
import io.yak.framework.schedule.plugin.quartz.QuartzScheduleJob;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ScheduleBuilder;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;

public final class QuartzScheduleEngine
implements ScheduleEngine {
    private final Scheduler scheduler;
    private final ObjectMapper objectMapper;
    private final ScheduleEngineBindingRepository bindingRepository;
    private final ScheduleEngineCapabilities capabilities = new ScheduleEngineCapabilities(EnumSet.of(TriggerType.CRON, TriggerType.ONE_TIME), EnumSet.allOf(ConcurrencyPolicy.class), EnumSet.allOf(MisfirePolicy.class), true, true, true, true);

    public QuartzScheduleEngine(Scheduler scheduler, ObjectMapper objectMapper, ScheduleEngineBindingRepository bindingRepository) {
        this.scheduler = scheduler;
        this.objectMapper = objectMapper;
        this.bindingRepository = bindingRepository;
    }

    public String type() {
        return "quartz";
    }

    public ScheduleEngineCapabilities capabilities() {
        return this.capabilities;
    }

    public ScheduleSnapshot save(ScheduleDefinition definition) {
        try {
            JobKey jobKey = QuartzScheduleEngine.jobKey(definition.key());
            TriggerKey triggerKey = QuartzScheduleEngine.triggerKey(definition.key());
            JobDetail job = this.buildJob(jobKey, definition);
            Trigger trigger = this.buildTrigger(triggerKey, jobKey, definition);
            if (this.scheduler.checkExists(jobKey)) {
                this.scheduler.deleteJob(jobKey);
            }
            this.scheduler.scheduleJob(job, trigger);
            if (!definition.enabled()) {
                this.scheduler.pauseJob(jobKey);
            }
            this.bindingRepository.save(new ScheduleEngineBinding(definition.key(), this.type(), QuartzScheduleEngine.externalId(definition.key()), Map.of()));
            return this.snapshot(jobKey);
        }
        catch (SchedulerException exception) {
            throw this.providerFailure("save", definition.key(), (Exception)((Object)exception));
        }
    }

    public void pause(ScheduleKey key) {
        this.execute("pause", key, () -> this.scheduler.pauseJob(this.required(key)));
    }

    public void resume(ScheduleKey key) {
        this.execute("resume", key, () -> this.scheduler.resumeJob(this.required(key)));
    }

    public void delete(ScheduleKey key) {
        this.execute("delete", key, () -> {
            this.scheduler.deleteJob(this.required(key));
            this.bindingRepository.delete(key, this.type());
        });
    }

    public ScheduleTriggerResult runNow(ScheduleKey key) {
        String triggerId = UUID.randomUUID().toString();
        this.execute("runNow", key, () -> {
            JobDataMap data = new JobDataMap();
            data.put("yak.schedule.manual-trigger-id", triggerId);
            this.scheduler.triggerJob(this.required(key), data);
        });
        return new ScheduleTriggerResult(triggerId, QuartzScheduleEngine.externalId(key), Instant.now());
    }

    public Optional<ScheduleSnapshot> get(ScheduleKey key) {
        try {
            JobKey jobKey = QuartzScheduleEngine.jobKey(key);
            if (!this.scheduler.checkExists(jobKey)) {
                return Optional.empty();
            }
            return Optional.of(this.snapshot(jobKey));
        }
        catch (SchedulerException exception) {
            throw this.providerFailure("get", key, (Exception)((Object)exception));
        }
    }

    public List<ScheduleSnapshot> list(String namespace) {
        try {
            ArrayList<ScheduleSnapshot> result = new ArrayList<ScheduleSnapshot>();
            for (JobKey key : this.scheduler.getJobKeys(GroupMatcher.jobGroupEquals((String)namespace))) {
                result.add(this.snapshot(key));
            }
            result.sort(Comparator.comparing(item -> item.definition().key().name()));
            return List.copyOf(result);
        }
        catch (SchedulerException exception) {
            throw new ScheduleProviderException("Quartz list failed for namespace: " + namespace, (Throwable)exception);
        }
    }

    private JobDetail buildJob(JobKey jobKey, ScheduleDefinition definition) {
        try {
            Class jobClass = definition.policy().concurrencyPolicy() == ConcurrencyPolicy.FORBID ? NonConcurrentQuartzScheduleJob.class : QuartzScheduleJob.class;
            JobDataMap data = new JobDataMap();
            data.put("yak.schedule.definition", this.objectMapper.writeValueAsString((Object)definition));
            return JobBuilder.newJob(jobClass).withIdentity(jobKey).usingJobData(data).build();
        }
        catch (Exception exception) {
            throw new ScheduleProviderException("Serialize Quartz schedule failed: " + definition.key().value(), (Throwable)exception);
        }
    }

    private Trigger buildTrigger(TriggerKey triggerKey, JobKey jobKey, ScheduleDefinition definition) {
        if (definition.trigger().type() == TriggerType.CRON) {
            CronScheduleBuilder builder = CronScheduleBuilder.cronSchedule((String)definition.trigger().expression()).inTimeZone(TimeZone.getTimeZone(definition.trigger().zoneId()));
            builder = definition.policy().misfirePolicy() == MisfirePolicy.IGNORE ? builder.withMisfireHandlingInstructionDoNothing() : builder.withMisfireHandlingInstructionFireAndProceed();
            return TriggerBuilder.newTrigger().withIdentity(triggerKey).forJob(jobKey).withSchedule((ScheduleBuilder)builder).build();
        }
        SimpleScheduleBuilder builder = SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0);
        builder = definition.policy().misfirePolicy() == MisfirePolicy.IGNORE ? builder.withMisfireHandlingInstructionNextWithExistingCount() : builder.withMisfireHandlingInstructionFireNow();
        return TriggerBuilder.newTrigger().withIdentity(triggerKey).forJob(jobKey).startAt(Date.from(definition.trigger().executeAt())).withSchedule((ScheduleBuilder)builder).build();
    }

    private ScheduleSnapshot snapshot(JobKey jobKey) throws SchedulerException {
        ScheduleDefinition definition;
        JobDetail job = this.scheduler.getJobDetail(jobKey);
        if (job == null) {
            throw new ScheduleNotFoundException(new ScheduleKey(jobKey.getGroup(), jobKey.getName()));
        }
        Trigger trigger = this.scheduler.getTrigger(TriggerKey.triggerKey((String)jobKey.getName(), (String)jobKey.getGroup()));
        try {
            definition = (ScheduleDefinition)this.objectMapper.readValue(job.getJobDataMap().getString("yak.schedule.definition"), ScheduleDefinition.class);
        }
        catch (Exception exception) {
            throw new ScheduleProviderException("Deserialize Quartz schedule failed: " + String.valueOf(jobKey), (Throwable)exception);
        }
        return new ScheduleSnapshot(definition, this.type(), QuartzScheduleEngine.externalId(definition.key()), this.status(trigger), QuartzScheduleEngine.instant(trigger == null ? null : trigger.getNextFireTime()), QuartzScheduleEngine.instant(trigger == null ? null : trigger.getPreviousFireTime()));
    }

    private ScheduleStatus status(Trigger trigger) throws SchedulerException {
        if (trigger == null) {
            return ScheduleStatus.COMPLETED;
        }
        return switch (this.scheduler.getTriggerState(trigger.getKey())) {
            case Trigger.TriggerState.PAUSED -> ScheduleStatus.PAUSED;
            case Trigger.TriggerState.COMPLETE, Trigger.TriggerState.NONE -> ScheduleStatus.COMPLETED;
            case Trigger.TriggerState.NORMAL, Trigger.TriggerState.BLOCKED -> ScheduleStatus.ENABLED;
            default -> ScheduleStatus.UNKNOWN;
        };
    }

    private JobKey required(ScheduleKey key) throws SchedulerException {
        JobKey jobKey = QuartzScheduleEngine.jobKey(key);
        if (!this.scheduler.checkExists(jobKey)) {
            throw new ScheduleNotFoundException(key);
        }
        return jobKey;
    }

    private void execute(String operation, ScheduleKey key, QuartzAction action) {
        try {
            action.run();
        }
        catch (ScheduleNotFoundException exception) {
            throw exception;
        }
        catch (SchedulerException exception) {
            throw this.providerFailure(operation, key, (Exception)((Object)exception));
        }
    }

    private ScheduleProviderException providerFailure(String operation, ScheduleKey key, Exception exception) {
        return new ScheduleProviderException("Quartz " + operation + " failed: " + key.value(), (Throwable)exception);
    }

    private static JobKey jobKey(ScheduleKey key) {
        return JobKey.jobKey((String)key.name(), (String)key.namespace());
    }

    private static TriggerKey triggerKey(ScheduleKey key) {
        return TriggerKey.triggerKey((String)key.name(), (String)key.namespace());
    }

    private static String externalId(ScheduleKey key) {
        return key.namespace() + "/" + key.name();
    }

    private static Instant instant(Date value) {
        return value == null ? null : value.toInstant();
    }

    @FunctionalInterface
    private static interface QuartzAction {
        public void run() throws SchedulerException;
    }
}

