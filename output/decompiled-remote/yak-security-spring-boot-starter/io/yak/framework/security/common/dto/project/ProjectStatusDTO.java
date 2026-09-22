/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.project;

import lombok.Generated;

public class ProjectStatusDTO {
    private Boolean running;

    @Generated
    public ProjectStatusDTO() {
    }

    @Generated
    public Boolean getRunning() {
        return this.running;
    }

    @Generated
    public void setRunning(Boolean running) {
        this.running = running;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ProjectStatusDTO)) {
            return false;
        }
        ProjectStatusDTO other = (ProjectStatusDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Boolean this$running = this.getRunning();
        Boolean other$running = other.getRunning();
        return !(this$running == null ? other$running != null : !((Object)this$running).equals(other$running));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof ProjectStatusDTO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Boolean $running = this.getRunning();
        result = result * 59 + ($running == null ? 43 : ((Object)$running).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "ProjectStatusDTO(running=" + this.getRunning() + ")";
    }
}

