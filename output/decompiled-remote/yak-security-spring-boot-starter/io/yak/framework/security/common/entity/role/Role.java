/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.entity.role;

import io.yak.framework.security.common.entity.BaseEntity;
import lombok.Generated;

public class Role
extends BaseEntity {
    private String roleCode;
    private String roleName;
    private String description;
    private String lastReviser;

    @Generated
    public Role() {
    }

    @Generated
    public String getRoleCode() {
        return this.roleCode;
    }

    @Generated
    public String getRoleName() {
        return this.roleName;
    }

    @Generated
    public String getDescription() {
        return this.description;
    }

    @Generated
    public String getLastReviser() {
        return this.lastReviser;
    }

    @Generated
    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    @Generated
    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    @Generated
    public void setDescription(String description) {
        this.description = description;
    }

    @Generated
    public void setLastReviser(String lastReviser) {
        this.lastReviser = lastReviser;
    }

    @Override
    @Generated
    public String toString() {
        return "Role(roleCode=" + this.getRoleCode() + ", roleName=" + this.getRoleName() + ", description=" + this.getDescription() + ", lastReviser=" + this.getLastReviser() + ")";
    }

    @Override
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Role)) {
            return false;
        }
        Role other = (Role)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        String this$roleCode = this.getRoleCode();
        String other$roleCode = other.getRoleCode();
        if (this$roleCode == null ? other$roleCode != null : !this$roleCode.equals(other$roleCode)) {
            return false;
        }
        String this$roleName = this.getRoleName();
        String other$roleName = other.getRoleName();
        if (this$roleName == null ? other$roleName != null : !this$roleName.equals(other$roleName)) {
            return false;
        }
        String this$description = this.getDescription();
        String other$description = other.getDescription();
        if (this$description == null ? other$description != null : !this$description.equals(other$description)) {
            return false;
        }
        String this$lastReviser = this.getLastReviser();
        String other$lastReviser = other.getLastReviser();
        return !(this$lastReviser == null ? other$lastReviser != null : !this$lastReviser.equals(other$lastReviser));
    }

    @Override
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof Role;
    }

    @Override
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        String $roleCode = this.getRoleCode();
        result = result * 59 + ($roleCode == null ? 43 : $roleCode.hashCode());
        String $roleName = this.getRoleName();
        result = result * 59 + ($roleName == null ? 43 : $roleName.hashCode());
        String $description = this.getDescription();
        result = result * 59 + ($description == null ? 43 : $description.hashCode());
        String $lastReviser = this.getLastReviser();
        result = result * 59 + ($lastReviser == null ? 43 : $lastReviser.hashCode());
        return result;
    }
}

