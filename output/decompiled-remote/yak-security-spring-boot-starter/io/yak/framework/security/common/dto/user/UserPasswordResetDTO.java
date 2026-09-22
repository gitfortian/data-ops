/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.user;

import lombok.Generated;

public class UserPasswordResetDTO {
    private String password;

    @Generated
    public UserPasswordResetDTO() {
    }

    @Generated
    public String getPassword() {
        return this.password;
    }

    @Generated
    public void setPassword(String password) {
        this.password = password;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof UserPasswordResetDTO)) {
            return false;
        }
        UserPasswordResetDTO other = (UserPasswordResetDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        String this$password = this.getPassword();
        String other$password = other.getPassword();
        return !(this$password == null ? other$password != null : !this$password.equals(other$password));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof UserPasswordResetDTO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        String $password = this.getPassword();
        result = result * 59 + ($password == null ? 43 : $password.hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "UserPasswordResetDTO(password=" + this.getPassword() + ")";
    }
}

