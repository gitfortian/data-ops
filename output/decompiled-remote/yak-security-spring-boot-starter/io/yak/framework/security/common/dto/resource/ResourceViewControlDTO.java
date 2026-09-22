/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.resource;

import lombok.Generated;

public class ResourceViewControlDTO {
    private Boolean enabled;

    @Generated
    public ResourceViewControlDTO() {
    }

    @Generated
    public Boolean getEnabled() {
        return this.enabled;
    }

    @Generated
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ResourceViewControlDTO)) {
            return false;
        }
        ResourceViewControlDTO other = (ResourceViewControlDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Boolean this$enabled = this.getEnabled();
        Boolean other$enabled = other.getEnabled();
        return !(this$enabled == null ? other$enabled != null : !((Object)this$enabled).equals(other$enabled));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof ResourceViewControlDTO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Boolean $enabled = this.getEnabled();
        result = result * 59 + ($enabled == null ? 43 : ((Object)$enabled).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "ResourceViewControlDTO(enabled=" + this.getEnabled() + ")";
    }
}

