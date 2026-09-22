/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.entity.dept;

import lombok.Generated;

public class DeptBrief {
    private Long id;
    private String deptName;
    private Boolean leaf;
    private Integer level;
    private Long parentId;

    @Generated
    public DeptBrief() {
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
    public Boolean getLeaf() {
        return this.leaf;
    }

    @Generated
    public Integer getLevel() {
        return this.level;
    }

    @Generated
    public Long getParentId() {
        return this.parentId;
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
    public void setLeaf(Boolean leaf) {
        this.leaf = leaf;
    }

    @Generated
    public void setLevel(Integer level) {
        this.level = level;
    }

    @Generated
    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof DeptBrief)) {
            return false;
        }
        DeptBrief other = (DeptBrief)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
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
        Long this$parentId = this.getParentId();
        Long other$parentId = other.getParentId();
        if (this$parentId == null ? other$parentId != null : !((Object)this$parentId).equals(other$parentId)) {
            return false;
        }
        String this$deptName = this.getDeptName();
        String other$deptName = other.getDeptName();
        return !(this$deptName == null ? other$deptName != null : !this$deptName.equals(other$deptName));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof DeptBrief;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        Boolean $leaf = this.getLeaf();
        result = result * 59 + ($leaf == null ? 43 : ((Object)$leaf).hashCode());
        Integer $level = this.getLevel();
        result = result * 59 + ($level == null ? 43 : ((Object)$level).hashCode());
        Long $parentId = this.getParentId();
        result = result * 59 + ($parentId == null ? 43 : ((Object)$parentId).hashCode());
        String $deptName = this.getDeptName();
        result = result * 59 + ($deptName == null ? 43 : $deptName.hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "DeptBrief(id=" + this.getId() + ", deptName=" + this.getDeptName() + ", leaf=" + this.getLeaf() + ", level=" + this.getLevel() + ", parentId=" + this.getParentId() + ")";
    }
}

