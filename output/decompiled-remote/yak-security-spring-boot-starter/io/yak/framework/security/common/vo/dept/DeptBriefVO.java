/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.vo.dept;

import lombok.Generated;

public class DeptBriefVO {
    private Long id;
    private String deptName;
    private Long parentId;

    @Generated
    public DeptBriefVO() {
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
    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof DeptBriefVO)) {
            return false;
        }
        DeptBriefVO other = (DeptBriefVO)o;
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
        String this$deptName = this.getDeptName();
        String other$deptName = other.getDeptName();
        return !(this$deptName == null ? other$deptName != null : !this$deptName.equals(other$deptName));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof DeptBriefVO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        Long $parentId = this.getParentId();
        result = result * 59 + ($parentId == null ? 43 : ((Object)$parentId).hashCode());
        String $deptName = this.getDeptName();
        result = result * 59 + ($deptName == null ? 43 : $deptName.hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "DeptBriefVO(id=" + this.getId() + ", deptName=" + this.getDeptName() + ", parentId=" + this.getParentId() + ")";
    }
}

