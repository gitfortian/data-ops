/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.entity.user;

import io.yak.framework.security.common.entity.BaseEntity;
import lombok.Generated;

public class User
extends BaseEntity {
    private String userName;
    private String pw;
    private String salt;
    private String realName;
    private String phone;
    private String email;
    private Long deptId;
    private Integer status = 1;

    @Generated
    public User() {
    }

    @Generated
    public String getUserName() {
        return this.userName;
    }

    @Generated
    public String getPw() {
        return this.pw;
    }

    @Generated
    public String getSalt() {
        return this.salt;
    }

    @Generated
    public String getRealName() {
        return this.realName;
    }

    @Generated
    public String getPhone() {
        return this.phone;
    }

    @Generated
    public String getEmail() {
        return this.email;
    }

    @Generated
    public Long getDeptId() {
        return this.deptId;
    }

    @Generated
    public Integer getStatus() {
        return this.status;
    }

    @Generated
    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Generated
    public void setPw(String pw) {
        this.pw = pw;
    }

    @Generated
    public void setSalt(String salt) {
        this.salt = salt;
    }

    @Generated
    public void setRealName(String realName) {
        this.realName = realName;
    }

    @Generated
    public void setPhone(String phone) {
        this.phone = phone;
    }

    @Generated
    public void setEmail(String email) {
        this.email = email;
    }

    @Generated
    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    @Generated
    public void setStatus(Integer status) {
        this.status = status;
    }

    @Override
    @Generated
    public String toString() {
        return "User(userName=" + this.getUserName() + ", realName=" + this.getRealName() + ", phone=" + this.getPhone() + ", email=" + this.getEmail() + ", deptId=" + this.getDeptId() + ", status=" + this.getStatus() + ")";
    }

    @Override
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof User)) {
            return false;
        }
        User other = (User)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        Long this$deptId = this.getDeptId();
        Long other$deptId = other.getDeptId();
        if (this$deptId == null ? other$deptId != null : !((Object)this$deptId).equals(other$deptId)) {
            return false;
        }
        Integer this$status = this.getStatus();
        Integer other$status = other.getStatus();
        if (this$status == null ? other$status != null : !((Object)this$status).equals(other$status)) {
            return false;
        }
        String this$userName = this.getUserName();
        String other$userName = other.getUserName();
        if (this$userName == null ? other$userName != null : !this$userName.equals(other$userName)) {
            return false;
        }
        String this$pw = this.getPw();
        String other$pw = other.getPw();
        if (this$pw == null ? other$pw != null : !this$pw.equals(other$pw)) {
            return false;
        }
        String this$salt = this.getSalt();
        String other$salt = other.getSalt();
        if (this$salt == null ? other$salt != null : !this$salt.equals(other$salt)) {
            return false;
        }
        String this$realName = this.getRealName();
        String other$realName = other.getRealName();
        if (this$realName == null ? other$realName != null : !this$realName.equals(other$realName)) {
            return false;
        }
        String this$phone = this.getPhone();
        String other$phone = other.getPhone();
        if (this$phone == null ? other$phone != null : !this$phone.equals(other$phone)) {
            return false;
        }
        String this$email = this.getEmail();
        String other$email = other.getEmail();
        return !(this$email == null ? other$email != null : !this$email.equals(other$email));
    }

    @Override
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof User;
    }

    @Override
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        Long $deptId = this.getDeptId();
        result = result * 59 + ($deptId == null ? 43 : ((Object)$deptId).hashCode());
        Integer $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : ((Object)$status).hashCode());
        String $userName = this.getUserName();
        result = result * 59 + ($userName == null ? 43 : $userName.hashCode());
        String $pw = this.getPw();
        result = result * 59 + ($pw == null ? 43 : $pw.hashCode());
        String $salt = this.getSalt();
        result = result * 59 + ($salt == null ? 43 : $salt.hashCode());
        String $realName = this.getRealName();
        result = result * 59 + ($realName == null ? 43 : $realName.hashCode());
        String $phone = this.getPhone();
        result = result * 59 + ($phone == null ? 43 : $phone.hashCode());
        String $email = this.getEmail();
        result = result * 59 + ($email == null ? 43 : $email.hashCode());
        return result;
    }
}

