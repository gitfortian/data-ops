/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.vo.dept;

import lombok.Generated;

public class DeptVO {
    private Long id;
    private String deptName;
    private String description;
    private Long parentId;
    private Boolean leaf;
    private Integer level;
    private Integer childDeptCount;
    private Integer userCount;

    @Generated
    public DeptVO() {
    }

    @Generated
    public Long getId() {
        return this.id;
    }

    @Generated
    public String getDeptName() {
        return this.deptName;
    }

    @Generated
    public String getDescription() {
        return this.description;
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
    public Integer getChildDeptCount() {
        return this.childDeptCount;
    }

    @Generated
    public Integer getUserCount() {
        return this.userCount;
    }

    @Generated
    public void setId(Long id) {
        this.id = id;
    }

    @Generated
    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    @Generated
    public void setDescription(String description) {
        this.description = description;
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
    public void setChildDeptCount(Integer childDeptCount) {
        this.childDeptCount = childDeptCount;
    }

    @Generated
    public void setUserCount(Integer userCount) {
        this.userCount = userCount;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof DeptVO)) {
            return false;
        }
        DeptVO other = (DeptVO)o;
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
        Integer this$childDeptCount = this.getChildDeptCount();
        Integer other$childDeptCount = other.getChildDeptCount();
        if (this$childDeptCount == null ? other$childDeptCount != null : !((Object)this$childDeptCount).equals(other$childDeptCount)) {
            return false;
        }
        Integer this$userCount = this.getUserCount();
        Integer other$userCount = other.getUserCount();
        if (this$userCount == null ? other$userCount != null : !((Object)this$userCount).equals(other$userCount)) {
            return false;
        }
        String this$deptName = this.getDeptName();
        String other$deptName = other.getDeptName();
        if (this$deptName == null ? other$deptName != null : !this$deptName.equals(other$deptName)) {
            return false;
        }
        String this$description = this.getDescription();
        String other$description = other.getDescription();
        return !(this$description == null ? other$description != null : !this$description.equals(other$description));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof DeptVO;
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
        Integer $childDeptCount = this.getChildDeptCount();
        result = result * 59 + ($childDeptCount == null ? 43 : ((Object)$childDeptCount).hashCode());
        Integer $userCount = this.getUserCount();
        result = result * 59 + ($userCount == null ? 43 : ((Object)$userCount).hashCode());
        String $deptName = this.getDeptName();
        result = result * 59 + ($deptName == null ? 43 : $deptName.hashCode());
        String $description = this.getDescription();
        result = result * 59 + ($description == null ? 43 : $description.hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "DeptVO(id=" + this.getId() + ", deptName=" + this.getDeptName() + ", description=" + this.getDescription() + ", parentId=" + this.getParentId() + ", leaf=" + this.getLeaf() + ", level=" + this.getLevel() + ", childDeptCount=" + this.getChildDeptCount() + ", userCount=" + this.getUserCount() + ")";
    }
}

