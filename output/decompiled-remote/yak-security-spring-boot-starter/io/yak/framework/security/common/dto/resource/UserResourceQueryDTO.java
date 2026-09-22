/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.resource;

import lombok.Generated;

public class UserResourceQueryDTO {
    private Integer controlLevel;
    private Long projectId;
    private Long resourceTypeId;
    private Long resourceId;

    public UserResourceQueryDTO(int controlLevel, Long projectId, Long resourceTypeId, Long resourceId) {
        this.controlLevel = controlLevel;
        this.projectId = projectId;
        this.resourceTypeId = resourceTypeId;
        this.resourceId = resourceId;
    }

    public UserResourceQueryDTO(int controlLevel, Long projectId, Long resourceTypeId) {
        this.controlLevel = controlLevel;
        this.projectId = projectId;
        this.resourceTypeId = resourceTypeId;
        this.resourceId = null;
    }

    public UserResourceQueryDTO(int controlLevel, Long projectId) {
        this.controlLevel = controlLevel;
        this.projectId = projectId;
        this.resourceTypeId = null;
        this.resourceId = null;
    }

    public static UserResourceQueryDTO getOpenViewPermissionControlQueryEntity() {
        return new UserResourceQueryDTO(0, 0L, 0L, 0L);
    }

    @Generated
    public Integer getControlLevel() {
        return this.controlLevel;
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
    public void setControlLevel(Integer controlLevel) {
        this.controlLevel = controlLevel;
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
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof UserResourceQueryDTO)) {
            return false;
        }
        UserResourceQueryDTO other = (UserResourceQueryDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Integer this$controlLevel = this.getControlLevel();
        Integer other$controlLevel = other.getControlLevel();
        if (this$controlLevel == null ? other$controlLevel != null : !((Object)this$controlLevel).equals(other$controlLevel)) {
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
        return !(this$resourceId == null ? other$resourceId != null : !((Object)this$resourceId).equals(other$resourceId));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof UserResourceQueryDTO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Integer $controlLevel = this.getControlLevel();
        result = result * 59 + ($controlLevel == null ? 43 : ((Object)$controlLevel).hashCode());
        Long $projectId = this.getProjectId();
        result = result * 59 + ($projectId == null ? 43 : ((Object)$projectId).hashCode());
        Long $resourceTypeId = this.getResourceTypeId();
        result = result * 59 + ($resourceTypeId == null ? 43 : ((Object)$resourceTypeId).hashCode());
        Long $resourceId = this.getResourceId();
        result = result * 59 + ($resourceId == null ? 43 : ((Object)$resourceId).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "UserResourceQueryDTO(controlLevel=" + this.getControlLevel() + ", projectId=" + this.getProjectId() + ", resourceTypeId=" + this.getResourceTypeId() + ", resourceId=" + this.getResourceId() + ")";
    }
}

