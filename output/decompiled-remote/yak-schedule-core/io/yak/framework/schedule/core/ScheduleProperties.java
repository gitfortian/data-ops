/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.boot.context.properties.ConfigurationProperties
 */
package io.yak.framework.schedule.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(value="yak.schedule")
public class ScheduleProperties {
    private boolean enabled = true;
    private String engine = "quartz";
    private int inMemoryCapacity = 10000;

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEngine() {
        return this.engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public int getInMemoryCapacity() {
        return this.inMemoryCapacity;
    }

    public void setInMemoryCapacity(int inMemoryCapacity) {
        this.inMemoryCapacity = inMemoryCapacity;
    }
}

