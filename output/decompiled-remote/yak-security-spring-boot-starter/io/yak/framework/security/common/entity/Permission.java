/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.entity;

import lombok.Generated;

public class Permission {
    private Long id;
    private String permissionCode;
    private String permissionName;
    private Long parentId;
    private Boolean leaf;
    private Integer level;
    private String description;
    private String menuCode;
    private transient String parentCode;
    private Boolean active;
    private Boolean declared;

    @Generated
    public Permission() {
    }

    @Generated
    public Long getId() {
        return this.id;
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
    public Long getParentId() {
        return this.parentId;
    }

    @Generated
    public Boolean getLeaf() {
        return this.leaf;
    }

    @Generated
    public Integer getLevel() {
        return this.level;
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
    public String getParentCode() {
        return this.parentCode;
    }

    @Generated
    public Boolean getActive() {
        return this.active;
    }

    @Generated
    public Boolean getDeclared() {
        return this.declared;
    }

    @Generated
    public void setId(Long id) {
        this.id = id;
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
    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    @Generated
    public void setLeaf(Boolean leaf) {
        this.leaf = leaf;
    }

    @Generated
    public void setLevel(Integer level) {
        this.level = level;
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
    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    @Generated
    public void setActive(Boolean active) {
        this.active = active;
    }

    @Generated
    public void setDeclared(Boolean declared) {
        this.declared = declared;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Permission)) {
            return false;
        }
        Permission other = (Permission)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
            return false;
        }
        Long this$parentId = this.getParentId();
        Long other$parentId = other.getParentId();
        if (this$parentId == null ? other$parentId != null : !((Object)this$parentId).equals(other$parentId)) {
            return false;
        }
        Boolean this$leaf = this.getLeaf();
        Boolean other$leaf = other.getLeaf();
        if (this$leaf == null ? other$leaf != null : !((Object)this$leaf).equals(other$leaf)) {
            return false;
        }
        Integer this$level = this.getLevel();
        Integer other$level = other.getLevel();
        if (this$level == null ? other$level != null : !((Object)this$level).equals(other$level)) {
            return false;
        }
        Boolean this$active = this.getActive();
        Boolean other$active = other.getActive();
        if (this$active == null ? other$active != null : !((Object)this$active).equals(other$active)) {
            return false;
        }
        Boolean this$declared = this.getDeclared();
        Boolean other$declared = other.getDeclared();
        if (this$declared == null ? other$declared != null : !((Object)this$declared).equals(other$declared)) {
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
        return !(this$menuCode == null ? other$menuCode != null : !this$menuCode.equals(other$menuCode));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof Permission;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        Long $parentId = this.getParentId();
        result = result * 59 + ($parentId == null ? 43 : ((Object)$parentId).hashCode());
        Boolean $leaf = this.getLeaf();
        result = result * 59 + ($leaf == null ? 43 : ((Object)$leaf).hashCode());
        Integer $level = this.getLevel();
        result = result * 59 + ($level == null ? 43 : ((Object)$level).hashCode());
        Boolean $active = this.getActive();
        result = result * 59 + ($active == null ? 43 : ((Object)$active).hashCode());
        Boolean $declared = this.getDeclared();
        result = result * 59 + ($declared == null ? 43 : ((Object)$declared).hashCode());
        String $permissionCode = this.getPermissionCode();
        result = result * 59 + ($permissionCode == null ? 43 : $permissionCode.hashCode());
        String $permissionName = this.getPermissionName();
        result = result * 59 + ($permissionName == null ? 43 : $permissionName.hashCode());
        String $description = this.getDescription();
        result = result * 59 + ($description == null ? 43 : $description.hashCode());
        String $menuCode = this.getMenuCode();
        result = result * 59 + ($menuCode == null ? 43 : $menuCode.hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "Permission(id=" + this.getId() + ", permissionCode=" + this.getPermissionCode() + ", permissionName=" + this.getPermissionName() + ", parentId=" + this.getParentId() + ", leaf=" + this.getLeaf() + ", level=" + this.getLevel() + ", description=" + this.getDescription() + ", menuCode=" + this.getMenuCode() + ", parentCode=" + this.getParentCode() + ", active=" + this.getActive() + ", declared=" + this.getDeclared() + ")";
    }
}

