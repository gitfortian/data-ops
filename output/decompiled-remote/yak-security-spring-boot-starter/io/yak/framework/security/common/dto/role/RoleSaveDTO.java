/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.role;

import java.util.List;
import lombok.Generated;

public class RoleSaveDTO {
    private Long id;
    private String roleName;
    private String description;
    private List<Long> permissionIdList;

    @Generated
    public RoleSaveDTO() {
    }

    @Generated
    public Long getId() {
        return this.id;
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
    public List<Long> getPermissionIdList() {
        return this.permissionIdList;
    }

    @Generated
    public void setId(Long id) {
        this.id = id;
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
    public void setPermissionIdList(List<Long> permissionIdList) {
        this.permissionIdList = permissionIdList;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RoleSaveDTO)) {
            return false;
        }
        RoleSaveDTO other = (RoleSaveDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
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
        List<Long> this$permissionIdList = this.getPermissionIdList();
        List<Long> other$permissionIdList = other.getPermissionIdList();
        return !(this$permissionIdList == null ? other$permissionIdList != null : !((Object)this$permissionIdList).equals(other$permissionIdList));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof RoleSaveDTO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        String $roleName = this.getRoleName();
        result = result * 59 + ($roleName == null ? 43 : $roleName.hashCode());
        String $description = this.getDescription();
        result = result * 59 + ($description == null ? 43 : $description.hashCode());
        List<Long> $permissionIdList = this.getPermissionIdList();
        result = result * 59 + ($permissionIdList == null ? 43 : ((Object)$permissionIdList).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "RoleSaveDTO(id=" + this.getId() + ", roleName=" + this.getRoleName() + ", description=" + this.getDescription() + ", permissionIdList=" + String.valueOf(this.getPermissionIdList()) + ")";
    }
}

