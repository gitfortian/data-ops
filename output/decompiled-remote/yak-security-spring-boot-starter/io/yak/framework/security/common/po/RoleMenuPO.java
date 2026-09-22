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

@TableName(value="yak_security_role_menu")
public class RoleMenuPO
extends BasePO {
    private Long roleId;
    private Long menuId;

    @Generated
    public Long getRoleId() {
        return this.roleId;
    }

    @Generated
    public Long getMenuId() {
        return this.menuId;
    }

    @Generated
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    @Generated
    public void setMenuId(Long menuId) {
        this.menuId = menuId;
    }

    @Override
    @Generated
    public String toString() {
        return "RoleMenuPO(super=" + super.toString() + ", roleId=" + this.getRoleId() + ", menuId=" + this.getMenuId() + ")";
    }

    @Override
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RoleMenuPO)) {
            return false;
        }
        RoleMenuPO other = (RoleMenuPO)o;
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
        Long this$menuId = this.getMenuId();
        Long other$menuId = other.getMenuId();
        return !(this$menuId == null ? other$menuId != null : !((Object)this$menuId).equals(other$menuId));
    }

    @Override
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof RoleMenuPO;
    }

    @Override
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        Long $roleId = this.getRoleId();
        result = result * 59 + ($roleId == null ? 43 : ((Object)$roleId).hashCode());
        Long $menuId = this.getMenuId();
        result = result * 59 + ($menuId == null ? 43 : ((Object)$menuId).hashCode());
        return result;
    }
}

