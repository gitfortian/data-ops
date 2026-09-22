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

@TableName(value="yak_security_user_role")
public class UserRolePO
extends BasePO {
    private Long userId;
    private Long roleId;

    @Generated
    public Long getUserId() {
        return this.userId;
    }

    @Generated
    public Long getRoleId() {
        return this.roleId;
    }

    @Generated
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    @Generated
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    @Override
    @Generated
    public String toString() {
        return "UserRolePO(super=" + super.toString() + ", userId=" + this.getUserId() + ", roleId=" + this.getRoleId() + ")";
    }

    @Override
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof UserRolePO)) {
            return false;
        }
        UserRolePO other = (UserRolePO)o;
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
        Long this$roleId = this.getRoleId();
        Long other$roleId = other.getRoleId();
        return !(this$roleId == null ? other$roleId != null : !((Object)this$roleId).equals(other$roleId));
    }

    @Override
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof UserRolePO;
    }

    @Override
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        Long $userId = this.getUserId();
        result = result * 59 + ($userId == null ? 43 : ((Object)$userId).hashCode());
        Long $roleId = this.getRoleId();
        result = result * 59 + ($roleId == null ? 43 : ((Object)$roleId).hashCode());
        return result;
    }
}

