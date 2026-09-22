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

@TableName(value="yak_security_role_permission")
public class RolePermissionPO
extends BasePO {
    private Long roleId;
    private Long permissionId;

    @Generated
    public Long getRoleId() {
        return this.roleId;
    }

    @Generated
    public Long getPermissionId() {
        return this.permissionId;
    }

    @Generated
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    @Generated
    public void setPermissionId(Long permissionId) {
        this.permissionId = permissionId;
    }

    @Override
    @Generated
    public String toString() {
        return "RolePermissionPO(super=" + super.toString() + ", roleId=" + this.getRoleId() + ", permissionId=" + this.getPermissionId() + ")";
    }

    @Override
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RolePermissionPO)) {
            return false;
        }
        RolePermissionPO other = (RolePermissionPO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        Long this$roleId = this.getRoleId();
        Long other$roleId = other.getRoleId();
        if (this$roleId == null ? other$roleId != null : !((Object)this$roleId).equals(other$roleId)) {
            return false;
        }
        Long this$permissionId = this.getPermissionId();
        Long other$permissionId = other.getPermissionId();
        return !(this$permissionId == null ? other$permissionId != null : !((Object)this$permissionId).equals(other$permissionId));
    }

    @Override
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof RolePermissionPO;
    }

    @Override
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        Long $roleId = this.getRoleId();
        result = result * 59 + ($roleId == null ? 43 : ((Object)$roleId).hashCode());
        Long $permissionId = this.getPermissionId();
        result = result * 59 + ($permissionId == null ? 43 : ((Object)$permissionId).hashCode());
        return result;
    }
}

