/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.annotation.TableName
 *  lombok.Generated
 */
package io.yak.framework.security.common.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.framework.security.common.po.BasePO;
import lombok.Generated;

@TableName(value="yak_security_menu")
public class MenuPO
extends BasePO {
    private String menuCode;
    private String menuName;
    private String parentCode;
    private String routePath;
    private String iconKey;
    private Integer menuType;
    private Integer sortOrder;
    private Boolean visible;
    private Boolean active;
    private String requiredPermissionCode;
    private String description;

    @Generated
    public String getMenuCode() {
        return this.menuCode;
    }

    @Generated
    public String getMenuName() {
        return this.menuName;
    }

    @Generated
    public String getParentCode() {
        return this.parentCode;
    }

    @Generated
    public String getRoutePath() {
        return this.routePath;
    }

    @Generated
    public String getIconKey() {
        return this.iconKey;
    }

    @Generated
    public Integer getMenuType() {
        return this.menuType;
    }

    @Generated
    public Integer getSortOrder() {
        return this.sortOrder;
    }

    @Generated
    public Boolean getVisible() {
        return this.visible;
    }

    @Generated
    public Boolean getActive() {
        return this.active;
    }

    @Generated
    public String getRequiredPermissionCode() {
        return this.requiredPermissionCode;
    }

    @Generated
    public String getDescription() {
        return this.description;
    }

    @Generated
    public void setMenuCode(String menuCode) {
        this.menuCode = menuCode;
    }

    @Generated
    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }

    @Generated
    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    @Generated
    public void setRoutePath(String routePath) {
        this.routePath = routePath;
    }

    @Generated
    public void setIconKey(String iconKey) {
        this.iconKey = iconKey;
    }

    @Generated
    public void setMenuType(Integer menuType) {
        this.menuType = menuType;
    }

    @Generated
    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Generated
    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

    @Generated
    public void setActive(Boolean active) {
        this.active = active;
    }

    @Generated
    public void setRequiredPermissionCode(String requiredPermissionCode) {
        this.requiredPermissionCode = requiredPermissionCode;
    }

    @Generated
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    @Generated
    public String toString() {
        return "MenuPO(super=" + super.toString() + ", menuCode=" + this.getMenuCode() + ", menuName=" + this.getMenuName() + ", parentCode=" + this.getParentCode() + ", routePath=" + this.getRoutePath() + ", iconKey=" + this.getIconKey() + ", menuType=" + this.getMenuType() + ", sortOrder=" + this.getSortOrder() + ", visible=" + this.getVisible() + ", active=" + this.getActive() + ", requiredPermissionCode=" + this.getRequiredPermissionCode() + ", description=" + this.getDescription() + ")";
    }

    @Override
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof MenuPO)) {
            return false;
        }
        MenuPO other = (MenuPO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        Integer this$menuType = this.getMenuType();
        Integer other$menuType = other.getMenuType();
        if (this$menuType == null ? other$menuType != null : !((Object)this$menuType).equals(other$menuType)) {
            return false;
        }
        Integer this$sortOrder = this.getSortOrder();
        Integer other$sortOrder = other.getSortOrder();
        if (this$sortOrder == null ? other$sortOrder != null : !((Object)this$sortOrder).equals(other$sortOrder)) {
            return false;
        }
        Boolean this$visible = this.getVisible();
        Boolean other$visible = other.getVisible();
        if (this$visible == null ? other$visible != null : !((Object)this$visible).equals(other$visible)) {
            return false;
        }
        Boolean this$active = this.getActive();
        Boolean other$active = other.getActive();
        if (this$active == null ? other$active != null : !((Object)this$active).equals(other$active)) {
            return false;
        }
        String this$menuCode = this.getMenuCode();
        String other$menuCode = other.getMenuCode();
        if (this$menuCode == null ? other$menuCode != null : !this$menuCode.equals(other$menuCode)) {
            return false;
        }
        String this$menuName = this.getMenuName();
        String other$menuName = other.getMenuName();
        if (this$menuName == null ? other$menuName != null : !this$menuName.equals(other$menuName)) {
            return false;
        }
        String this$parentCode = this.getParentCode();
        String other$parentCode = other.getParentCode();
        if (this$parentCode == null ? other$parentCode != null : !this$parentCode.equals(other$parentCode)) {
            return false;
        }
        String this$routePath = this.getRoutePath();
        String other$routePath = other.getRoutePath();
        if (this$routePath == null ? other$routePath != null : !this$routePath.equals(other$routePath)) {
            return false;
        }
        String this$iconKey = this.getIconKey();
        String other$iconKey = other.getIconKey();
        if (this$iconKey == null ? other$iconKey != null : !this$iconKey.equals(other$iconKey)) {
            return false;
        }
        String this$requiredPermissionCode = this.getRequiredPermissionCode();
        String other$requiredPermissionCode = other.getRequiredPermissionCode();
        if (this$requiredPermissionCode == null ? other$requiredPermissionCode != null : !this$requiredPermissionCode.equals(other$requiredPermissionCode)) {
            return false;
        }
        String this$description = this.getDescription();
        String other$description = other.getDescription();
        return !(this$description == null ? other$description != null : !this$description.equals(other$description));
    }

    @Override
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof MenuPO;
    }

    @Override
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        Integer $menuType = this.getMenuType();
        result = result * 59 + ($menuType == null ? 43 : ((Object)$menuType).hashCode());
        Integer $sortOrder = this.getSortOrder();
        result = result * 59 + ($sortOrder == null ? 43 : ((Object)$sortOrder).hashCode());
        Boolean $visible = this.getVisible();
        result = result * 59 + ($visible == null ? 43 : ((Object)$visible).hashCode());
        Boolean $active = this.getActive();
        result = result * 59 + ($active == null ? 43 : ((Object)$active).hashCode());
        String $menuCode = this.getMenuCode();
        result = result * 59 + ($menuCode == null ? 43 : $menuCode.hashCode());
        String $menuName = this.getMenuName();
        result = result * 59 + ($menuName == null ? 43 : $menuName.hashCode());
        String $parentCode = this.getParentCode();
        result = result * 59 + ($parentCode == null ? 43 : $parentCode.hashCode());
        String $routePath = this.getRoutePath();
        result = result * 59 + ($routePath == null ? 43 : $routePath.hashCode());
        String $iconKey = this.getIconKey();
        result = result * 59 + ($iconKey == null ? 43 : $iconKey.hashCode());
        String $requiredPermissionCode = this.getRequiredPermissionCode();
        result = result * 59 + ($requiredPermissionCode == null ? 43 : $requiredPermissionCode.hashCode());
        String $description = this.getDescription();
        result = result * 59 + ($description == null ? 43 : $description.hashCode());
        return result;
    }
}

