/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.annotation.TableName
 *  lombok.Generated
 */
package io.yak.framework.security.common.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.framework.security.common.po.BasePO;
import lombok.Generated;

@TableName(value="yak_security_user_resource")
public class UserResourcePO
extends BasePO {
    private Long userId;
    private Long projectId;
    private Long resourceTypeId;
    private Long resourceId;
    private Integer controlLevel;

    @Generated
    public Long getUserId() {
        return this.userId;
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
    public void setUserId(Long userId) {
        this.userId = userId;
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

    @Override
    @Generated
    public String toString() {
        return "UserResourcePO(super=" + super.toString() + ", userId=" + this.getUserId() + ", projectId=" + this.getProjectId() + ", resourceTypeId=" + this.getResourceTypeId() + ", resourceId=" + this.getResourceId() + ", controlLevel=" + this.getControlLevel() + ")";
    }

    @Override
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof UserResourcePO)) {
            return false;
        }
        UserResourcePO other = (UserResourcePO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        Long this$userId = this.getUserId();
        Long other$userId = other.getUserId();
        if (this$userId == null ? other$userId != null : !((Object)this$userId).equals(other$userId)) {
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
        return !(this$controlLevel == null ? other$controlLevel != null : !((Object)this$controlLevel).equals(other$controlLevel));
    }

    @Override
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof UserResourcePO;
    }

    @Override
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        Long $userId = this.getUserId();
        result = result * 59 + ($userId == null ? 43 : ((Object)$userId).hashCode());
        Long $projectId = this.getProjectId();
        result = result * 59 + ($projectId == null ? 43 : ((Object)$projectId).hashCode());
        Long $resourceTypeId = this.getResourceTypeId();
        result = result * 59 + ($resourceTypeId == null ? 43 : ((Object)$resourceTypeId).hashCode());
        Long $resourceId = this.getResourceId();
        result = result * 59 + ($resourceId == null ? 43 : ((Object)$resourceId).hashCode());
        Integer $controlLevel = this.getControlLevel();
        result = result * 59 + ($controlLevel == null ? 43 : ((Object)$controlLevel).hashCode());
        return result;
    }
}

