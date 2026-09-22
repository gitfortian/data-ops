/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.vo.role;

import java.util.List;
import lombok.Generated;

public class RoleDeleteCheckVO {
    private Long roleId;
    private List<String> userNameList;

    @Generated
    public RoleDeleteCheckVO() {
    }

    @Generated
    public Long getRoleId() {
        return this.roleId;
    }

    @Generated
    public List<String> getUserNameList() {
        return this.userNameList;
    }

    @Generated
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    @Generated
    public void setUserNameList(List<String> userNameList) {
        this.userNameList = userNameList;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RoleDeleteCheckVO)) {
            return false;
        }
        RoleDeleteCheckVO other = (RoleDeleteCheckVO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Long this$roleId = this.getRoleId();
        Long other$roleId = other.getRoleId();
        if (this$roleId == null ? other$roleId != null : !((Object)this$roleId).equals(other$roleId)) {
            return false;
        }
        List<String> this$userNameList = this.getUserNameList();
        List<String> other$userNameList = other.getUserNameList();
        return !(this$userNameList == null ? other$userNameList != null : !((Object)this$userNameList).equals(other$userNameList));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof RoleDeleteCheckVO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $roleId = this.getRoleId();
        result = result * 59 + ($roleId == null ? 43 : ((Object)$roleId).hashCode());
        List<String> $userNameList = this.getUserNameList();
        result = result * 59 + ($userNameList == null ? 43 : ((Object)$userNameList).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "RoleDeleteCheckVO(roleId=" + this.getRoleId() + ", userNameList=" + String.valueOf(this.getUserNameList()) + ")";
    }
}

