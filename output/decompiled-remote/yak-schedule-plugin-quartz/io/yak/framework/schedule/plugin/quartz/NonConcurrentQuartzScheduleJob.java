/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.quartz.DisallowConcurrentExecution
 */
package io.yak.framework.schedule.plugin.quartz;

import io.yak.framework.schedule.plugin.quartz.QuartzScheduleJob;
import org.quartz.DisallowConcurrentExecution;

@DisallowConcurrentExecution
public final class NonConcurrentQuartzScheduleJob
extends QuartzScheduleJob {
}

