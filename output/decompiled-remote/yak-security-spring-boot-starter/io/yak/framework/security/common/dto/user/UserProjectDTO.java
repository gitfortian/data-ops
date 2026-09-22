/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.user;

import lombok.Generated;

public class UserProjectDTO {
    private Long id;
    private Long userId;
    private Integer userType;
    private Long projectId;
    private Boolean isDelete;

    @Generated
    public UserProjectDTO() {
    }

    @Generated
    public Long getId() {
        return this.id;
    }

    @Generated
    public Long getUserId() {
        return this.userId;
    }

    @Generated
    public Integer getUserType() {
        return this.userType;
    }

    @Generated
    public Long getProjectId() {
        return this.projectId;
    }

    @Generated
    public Boolean getIsDelete() {
        return this.isDelete;
    }

    @Generated
    public void setId(Long id) {
        this.id = id;
    }

    @Generated
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    @Generated
    public void setUserType(Integer userType) {
        this.userType = userType;
    }

    @Generated
    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    @Generated
    public void setIsDelete(Boolean isDelete) {
        this.isDelete = isDelete;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof UserProjectDTO)) {
            return false;
        }
        UserProjectDTO other = (UserProjectDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
            return false;
        }
        Long this$userId = this.getUserId();
        Long other$userId = other.getUserId();
        if (this$userId == null ? other$userId != null : !((Object)this$userId).equals(other$userId)) {
            return false;
        }
        Integer this$userType = this.getUserType();
        Integer other$userType = other.getUserType();
        if (this$userType == null ? other$userType != null : !((Object)this$userType).equals(other$userType)) {
            return false;
        }
        Long this$projectId = this.getProjectId();
        Long other$projectId = other.getProjectId();
        if (this$projectId == null ? other$projectId != null : !((Object)this$projectId).equals(other$projectId)) {
            return false;
        }
        Boolean this$isDelete = this.getIsDelete();
        Boolean other$isDelete = other.getIsDelete();
        return !(this$isDelete == null ? other$isDelete != null : !((Object)this$isDelete).equals(other$isDelete));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof UserProjectDTO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        Long $userId = this.getUserId();
        result = result * 59 + ($userId == null ? 43 : ((Object)$userId).hashCode());
        Integer $userType = this.getUserType();
        result = result * 59 + ($userType == null ? 43 : ((Object)$userType).hashCode());
        Long $projectId = this.getProjectId();
        result = result * 59 + ($projectId == null ? 43 : ((Object)$projectId).hashCode());
        Boolean $isDelete = this.getIsDelete();
        result = result * 59 + ($isDelete == null ? 43 : ((Object)$isDelete).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "UserProjectDTO(id=" + this.getId() + ", userId=" + this.getUserId() + ", userType=" + this.getUserType() + ", projectId=" + this.getProjectId() + ", isDelete=" + this.getIsDelete() + ")";
    }
}

