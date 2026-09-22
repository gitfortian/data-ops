/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.vo.user;

import lombok.Generated;

public class UserBasicVO {
    private Long id;
    private String userName;
    private String realName;
    private Long deptId;

    @Generated
    public UserBasicVO() {
    }

    @Generated
    public Long getId() {
        return this.id;
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
    public Long getDeptId() {
        return this.deptId;
    }

    @Generated
    public void setId(Long id) {
        this.id = id;
    }

    @Generated
    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Generated
    public void setRealName(String realName) {
        this.realName = realName;
    }

    @Generated
    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof UserBasicVO)) {
            return false;
        }
        UserBasicVO other = (UserBasicVO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
            return false;
        }
        Long this$deptId = this.getDeptId();
        Long other$deptId = other.getDeptId();
        if (this$deptId == null ? other$deptId != null : !((Object)this$deptId).equals(other$deptId)) {
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

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof UserBasicVO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        Long $deptId = this.getDeptId();
        result = result * 59 + ($deptId == null ? 43 : ((Object)$deptId).hashCode());
        String $userName = this.getUserName();
        result = result * 59 + ($userName == null ? 43 : $userName.hashCode());
        String $realName = this.getRealName();
        result = result * 59 + ($realName == null ? 43 : $realName.hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "UserBasicVO(id=" + this.getId() + ", userName=" + this.getUserName() + ", realName=" + this.getRealName() + ", deptId=" + this.getDeptId() + ")";
    }
}

