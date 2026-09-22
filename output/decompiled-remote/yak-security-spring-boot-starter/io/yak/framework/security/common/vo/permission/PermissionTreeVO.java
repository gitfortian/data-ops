/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.vo.permission;

import java.util.List;
import lombok.Generated;

public class PermissionTreeVO {
    private Long id;
    private Boolean has;
    private String permissionCode;
    private String permissionName;
    private Long parentId;
    private Boolean leaf;
    private String description;
    private Boolean active;
    private Boolean declared;
    private String menuCode;
    private String nodeType;
    private List<PermissionTreeVO> childList;

    @Generated
    public static PermissionTreeVOBuilder builder() {
        return new PermissionTreeVOBuilder();
    }

    @Generated
    public Long getId() {
        return this.id;
    }

    @Generated
    public Boolean getHas() {
        return this.has;
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
    public String getDescription() {
        return this.description;
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
    public String getMenuCode() {
        return this.menuCode;
    }

    @Generated
    public String getNodeType() {
        return this.nodeType;
    }

    @Generated
    public List<PermissionTreeVO> getChildList() {
        return this.childList;
    }

    @Generated
    public void setId(Long id) {
        this.id = id;
    }

    @Generated
    public void setHas(Boolean has) {
        this.has = has;
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
    public void setDescription(String description) {
        this.description = description;
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
    public void setMenuCode(String menuCode) {
        this.menuCode = menuCode;
    }

    @Generated
    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    @Generated
    public void setChildList(List<PermissionTreeVO> childList) {
        this.childList = childList;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof PermissionTreeVO)) {
            return false;
        }
        PermissionTreeVO other = (PermissionTreeVO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
            return false;
        }
        Boolean this$has = this.getHas();
        Boolean other$has = other.getHas();
        if (this$has == null ? other$has != null : !((Object)this$has).equals(other$has)) {
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
        if (this$menuCode == null ? other$menuCode != null : !this$menuCode.equals(other$menuCode)) {
            return false;
        }
        String this$nodeType = this.getNodeType();
        String other$nodeType = other.getNodeType();
        if (this$nodeType == null ? other$nodeType != null : !this$nodeType.equals(other$nodeType)) {
            return false;
        }
        List<PermissionTreeVO> this$childList = this.getChildList();
        List<PermissionTreeVO> other$childList = other.getChildList();
        return !(this$childList == null ? other$childList != null : !((Object)this$childList).equals(other$childList));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof PermissionTreeVO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        Boolean $has = this.getHas();
        result = result * 59 + ($has == null ? 43 : ((Object)$has).hashCode());
        Long $parentId = this.getParentId();
        result = result * 59 + ($parentId == null ? 43 : ((Object)$parentId).hashCode());
        Boolean $leaf = this.getLeaf();
        result = result * 59 + ($leaf == null ? 43 : ((Object)$leaf).hashCode());
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
        String $nodeType = this.getNodeType();
        result = result * 59 + ($nodeType == null ? 43 : $nodeType.hashCode());
        List<PermissionTreeVO> $childList = this.getChildList();
        result = result * 59 + ($childList == null ? 43 : ((Object)$childList).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "PermissionTreeVO(id=" + this.getId() + ", has=" + this.getHas() + ", permissionCode=" + this.getPermissionCode() + ", permissionName=" + this.getPermissionName() + ", parentId=" + this.getParentId() + ", leaf=" + this.getLeaf() + ", description=" + this.getDescription() + ", active=" + this.getActive() + ", declared=" + this.getDeclared() + ", menuCode=" + this.getMenuCode() + ", nodeType=" + this.getNodeType() + ", childList=" + String.valueOf(this.getChildList()) + ")";
    }

    @Generated
    public PermissionTreeVO(Long id, Boolean has, String permissionCode, String permissionName, Long parentId, Boolean leaf, String description, Boolean active, Boolean declared, String menuCode, String nodeType, List<PermissionTreeVO> childList) {
        this.id = id;
        this.has = has;
        this.permissionCode = permissionCode;
        this.permissionName = permissionName;
        this.parentId = parentId;
        this.leaf = leaf;
        this.description = description;
        this.active = active;
        this.declared = declared;
        this.menuCode = menuCode;
        this.nodeType = nodeType;
        this.childList = childList;
    }

    @Generated
    public PermissionTreeVO() {
    }

    @Generated
    public static class PermissionTreeVOBuilder {
        @Generated
        private Long id;
        @Generated
        private Boolean has;
        @Generated
        private String permissionCode;
        @Generated
        private String permissionName;
        @Generated
        private Long parentId;
        @Generated
        private Boolean leaf;
        @Generated
        private String description;
        @Generated
        private Boolean active;
        @Generated
        private Boolean declared;
        @Generated
        private String menuCode;
        @Generated
        private String nodeType;
        @Generated
        private List<PermissionTreeVO> childList;

        @Generated
        PermissionTreeVOBuilder() {
        }

        @Generated
        public PermissionTreeVOBuilder id(Long id) {
            this.id = id;
            return this;
        }

        @Generated
        public PermissionTreeVOBuilder has(Boolean has) {
            this.has = has;
            return this;
        }

        @Generated
        public PermissionTreeVOBuilder permissionCode(String permissionCode) {
            this.permissionCode = permissionCode;
            return this;
        }

        @Generated
        public PermissionTreeVOBuilder permissionName(String permissionName) {
            this.permissionName = permissionName;
            return this;
        }

        @Generated
        public PermissionTreeVOBuilder parentId(Long parentId) {
            this.parentId = parentId;
            return this;
        }

        @Generated
        public PermissionTreeVOBuilder leaf(Boolean leaf) {
            this.leaf = leaf;
            return this;
        }

        @Generated
        public PermissionTreeVOBuilder description(String description) {
            this.description = description;
            return this;
        }

        @Generated
        public PermissionTreeVOBuilder active(Boolean active) {
            this.active = active;
            return this;
        }

        @Generated
        public PermissionTreeVOBuilder declared(Boolean declared) {
            this.declared = declared;
            return this;
        }

        @Generated
        public PermissionTreeVOBuilder menuCode(String menuCode) {
            this.menuCode = menuCode;
            return this;
        }

        @Generated
        public PermissionTreeVOBuilder nodeType(String nodeType) {
            this.nodeType = nodeType;
            return this;
        }

        @Generated
        public PermissionTreeVOBuilder childList(List<PermissionTreeVO> childList) {
            this.childList = childList;
            return this;
        }

        @Generated
        public PermissionTreeVO build() {
            return new PermissionTreeVO(this.id, this.has, this.permissionCode, this.permissionName, this.parentId, this.leaf, this.description, this.active, this.declared, this.menuCode, this.nodeType, this.childList);
        }

        @Generated
        public String toString() {
            return "PermissionTreeVO.PermissionTreeVOBuilder(id=" + this.id + ", has=" + this.has + ", permissionCode=" + this.permissionCode + ", permissionName=" + this.permissionName + ", parentId=" + this.parentId + ", leaf=" + this.leaf + ", description=" + this.description + ", active=" + this.active + ", declared=" + this.declared + ", menuCode=" + this.menuCode + ", nodeType=" + this.nodeType + ", childList=" + String.valueOf(this.childList) + ")";
        }
    }
}

