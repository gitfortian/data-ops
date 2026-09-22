/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.user;

import io.yak.framework.security.common.dto.PageParamDTO;
import lombok.Generated;

public class UserQueryDTO
extends PageParamDTO {
    private Long id;
    private Long roleId;
    private String userName;
    private String realName;

    @Generated
    public UserQueryDTO() {
    }

    @Generated
    public Long getId() {
        return this.id;
    }

    @Generated
    public Long getRoleId() {
        return this.roleId;
    }

    @Generated
    public String getUserName() {
        return this.userName;
    }

    @Generated
    public String getRealName() {
        return this.realName;
    }

    @Generated
    public void setId(Long id) {
        this.id = id;
    }

    @Generated
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    @Generated
    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Generated
    public void setRealName(String realName) {
        this.realName = realName;
    }

    @Override
    @Generated
    public String toString() {
        return "UserQueryDTO(id=" + this.getId() + ", roleId=" + this.getRoleId() + ", userName=" + this.getUserName() + ", realName=" + this.getRealName() + ")";
    }

    @Override
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof UserQueryDTO)) {
            return false;
        }
        UserQueryDTO other = (UserQueryDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
            return false;
        }
        Long this$roleId = this.getRoleId();
        Long other$roleId = other.getRoleId();
        if (this$roleId == null ? other$roleId != null : !((Object)this$roleId).equals(other$roleId)) {
            return false;
        }
        String this$userName = this.getUserName();
        String other$userName = other.getUserName();
        if (this$userName == null ? other$userName != null : !this$userName.equals(other$userName)) {
            return false;
        }
        String this$realName = this.getRealName();
        String other$realName = other.getRealName();
        return !(this$realName == null ? other$realName != null : !this$realName.equals(other$realName));
    }

    @Override
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof UserQueryDTO;
    }

    @Override
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        Long $roleId = this.getRoleId();
        result = result * 59 + ($roleId == null ? 43 : ((Object)$roleId).hashCode());
        String $userName = this.getUserName();
        result = result * 59 + ($userName == null ? 43 : $userName.hashCode());
        String $realName = this.getRealName();
        result = result * 59 + ($realName == null ? 43 : $realName.hashCode());
        return result;
    }
}

