/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  jakarta.validation.constraints.NotBlank
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.account;

import jakarta.validation.constraints.NotBlank;
import lombok.Generated;

public class AccountLoginDTO {
    @NotBlank
    private String userName;
    @NotBlank
    private String pw;

    @Generated
    public AccountLoginDTO() {
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
    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Generated
    public void setPw(String pw) {
        this.pw = pw;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof AccountLoginDTO)) {
            return false;
        }
        AccountLoginDTO other = (AccountLoginDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        String this$userName = this.getUserName();
        String other$userName = other.getUserName();
        if (this$userName == null ? other$userName != null : !this$userName.equals(other$userName)) {
            return false;
        }
        String this$pw = this.getPw();
        String other$pw = other.getPw();
        return !(this$pw == null ? other$pw != null : !this$pw.equals(other$pw));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof AccountLoginDTO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        String $userName = this.getUserName();
        result = result * 59 + ($userName == null ? 43 : $userName.hashCode());
        String $pw = this.getPw();
        result = result * 59 + ($pw == null ? 43 : $pw.hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "AccountLoginDTO(userName=" + this.getUserName() + ", pw=" + this.getPw() + ")";
    }
}

