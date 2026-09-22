/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.permission;

import java.util.ArrayList;
import java.util.List;
import lombok.Generated;

public class PermissionDTO {
    private String permissionCode;
    private String permissionName;
    private String description;
    private String menuCode;
    private List<PermissionDTO> childPermissionDTOList;

    public List<PermissionDTO> getChildPermissionDTOList() {
        if (this.childPermissionDTOList == null) {
            this.childPermissionDTOList = new ArrayList<PermissionDTO>();
        }
        return this.childPermissionDTOList;
    }

    public PermissionDTO() {
    }

    public PermissionDTO(String permissionName, String description) {
        this.permissionName = permissionName;
        this.description = description;
    }

    public PermissionDTO(String permissionName) {
        this.permissionName = permissionName;
        this.description = permissionName;
    }

    @Generated
    public String getPermissionCode() {
        return this.permissionCode;
    }

    @Generated
    public String getPermissionName() {
        return this.permissionName;
    }

    @Generated
    public String getDescription() {
        return this.description;
    }

    @Generated
    public String getMenuCode() {
        return this.menuCode;
    }

    @Generated
    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    @Generated
    public void setPermissionName(String permissionName) {
        this.permissionName = permissionName;
    }

    @Generated
    public void setDescription(String description) {
        this.description = description;
    }

    @Generated
    public void setMenuCode(String menuCode) {
        this.menuCode = menuCode;
    }

    @Generated
    public void setChildPermissionDTOList(List<PermissionDTO> childPermissionDTOList) {
        this.childPermissionDTOList = childPermissionDTOList;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof PermissionDTO)) {
            return false;
        }
        PermissionDTO other = (PermissionDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        String this$permissionCode = this.getPermissionCode();
        String other$permissionCode = other.getPermissionCode();
        if (this$permissionCode == null ? other$permissionCode != null : !this$permissionCode.equals(other$permissionCode)) {
            return false;
        }
        String this$permissionName = this.getPermissionName();
        String other$permissionName = other.getPermissionName();
        if (this$permissionName == null ? other$permissionName != null : !this$permissionName.equals(other$permissionName)) {
            return false;
        }
        String this$description = this.getDescription();
        String other$description = other.getDescription();
        if (this$description == null ? other$description != null : !this$description.equals(other$description)) {
            return false;
        }
        String this$menuCode = this.getMenuCode();
        String other$menuCode = other.getMenuCode();
        if (this$menuCode == null ? other$menuCode != null : !this$menuCode.equals(other$menuCode)) {
            return false;
        }
        List<PermissionDTO> this$childPermissionDTOList = this.getChildPermissionDTOList();
        List<PermissionDTO> other$childPermissionDTOList = other.getChildPermissionDTOList();
        return !(this$childPermissionDTOList == null ? other$childPermissionDTOList != null : !((Object)this$childPermissionDTOList).equals(other$childPermissionDTOList));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof PermissionDTO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        String $permissionCode = this.getPermissionCode();
        result = result * 59 + ($permissionCode == null ? 43 : $permissionCode.hashCode());
        String $permissionName = this.getPermissionName();
        result = result * 59 + ($permissionName == null ? 43 : $permissionName.hashCode());
        String $description = this.getDescription();
        result = result * 59 + ($description == null ? 43 : $description.hashCode());
        String $menuCode = this.getMenuCode();
        result = result * 59 + ($menuCode == null ? 43 : $menuCode.hashCode());
        List<PermissionDTO> $childPermissionDTOList = this.getChildPermissionDTOList();
        result = result * 59 + ($childPermissionDTOList == null ? 43 : ((Object)$childPermissionDTOList).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "PermissionDTO(permissionCode=" + this.getPermissionCode() + ", permissionName=" + this.getPermissionName() + ", description=" + this.getDescription() + ", menuCode=" + this.getMenuCode() + ", childPermissionDTOList=" + String.valueOf(this.getChildPermissionDTOList()) + ")";
    }
}

