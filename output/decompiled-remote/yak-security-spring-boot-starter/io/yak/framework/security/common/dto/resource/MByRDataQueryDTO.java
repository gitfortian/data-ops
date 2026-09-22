/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.resource;

import lombok.Generated;

public class MByRDataQueryDTO {
    private Long projectId;
    private Long resourceTypeId;
    private Long resourceId;
    private Integer controlLevel;
    private Boolean batch;

    @Generated
    public MByRDataQueryDTO() {
    }

    @Generated
    public Long getProjectId() {
        return this.projectId;
    }

    @Generated
    public Long getResourceTypeId() {
        return this.resourceTypeId;
    }

    @Generated
    public Long getResourceId() {
        return this.resourceId;
    }

    @Generated
    public Integer getControlLevel() {
        return this.controlLevel;
    }

    @Generated
    public Boolean getBatch() {
        return this.batch;
    }

    @Generated
    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    @Generated
    public void setResourceTypeId(Long resourceTypeId) {
        this.resourceTypeId = resourceTypeId;
    }

    @Generated
    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    @Generated
    public void setControlLevel(Integer controlLevel) {
        this.controlLevel = controlLevel;
    }

    @Generated
    public void setBatch(Boolean batch) {
        this.batch = batch;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof MByRDataQueryDTO)) {
            return false;
        }
        MByRDataQueryDTO other = (MByRDataQueryDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Long this$projectId = this.getProjectId();
        Long other$projectId = other.getProjectId();
        if (this$projectId == null ? other$projectId != null : !((Object)this$projectId).equals(other$projectId)) {
            return false;
        }
        Long this$resourceTypeId = this.getResourceTypeId();
        Long other$resourceTypeId = other.getResourceTypeId();
        if (this$resourceTypeId == null ? other$resourceTypeId != null : !((Object)this$resourceTypeId).equals(other$resourceTypeId)) {
            return false;
        }
        Long this$resourceId = this.getResourceId();
        Long other$resourceId = other.getResourceId();
        if (this$resourceId == null ? other$resourceId != null : !((Object)this$resourceId).equals(other$resourceId)) {
            return false;
        }
        Integer this$controlLevel = this.getControlLevel();
        Integer other$controlLevel = other.getControlLevel();
        if (this$controlLevel == null ? other$controlLevel != null : !((Object)this$controlLevel).equals(other$controlLevel)) {
            return false;
        }
        Boolean this$batch = this.getBatch();
        Boolean other$batch = other.getBatch();
        return !(this$batch == null ? other$batch != null : !((Object)this$batch).equals(other$batch));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof MByRDataQueryDTO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $projectId = this.getProjectId();
        result = result * 59 + ($projectId == null ? 43 : ((Object)$projectId).hashCode());
        Long $resourceTypeId = this.getResourceTypeId();
        result = result * 59 + ($resourceTypeId == null ? 43 : ((Object)$resourceTypeId).hashCode());
        Long $resourceId = this.getResourceId();
        result = result * 59 + ($resourceId == null ? 43 : ((Object)$resourceId).hashCode());
        Integer $controlLevel = this.getControlLevel();
        result = result * 59 + ($controlLevel == null ? 43 : ((Object)$controlLevel).hashCode());
        Boolean $batch = this.getBatch();
        result = result * 59 + ($batch == null ? 43 : ((Object)$batch).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "MByRDataQueryDTO(projectId=" + this.getProjectId() + ", resourceTypeId=" + this.getResourceTypeId() + ", resourceId=" + this.getResourceId() + ", controlLevel=" + this.getControlLevel() + ", batch=" + this.getBatch() + ")";
    }
}

