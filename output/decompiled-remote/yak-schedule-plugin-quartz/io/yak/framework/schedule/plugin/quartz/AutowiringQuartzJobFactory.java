/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.quartz.spi.TriggerFiredBundle
 *  org.springframework.beans.factory.config.AutowireCapableBeanFactory
 *  org.springframework.scheduling.quartz.SpringBeanJobFactory
 */
package io.yak.framework.schedule.plugin.quartz;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

public final class AutowiringQuartzJobFactory
extends SpringBeanJobFactory {
    private final AutowireCapableBeanFactory beanFactory;

    public AutowiringQuartzJobFactory(AutowireCapableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
        Object job = super.createJobInstance(bundle);
        this.beanFactory.autowireBean(job);
        return job;
    }
}

