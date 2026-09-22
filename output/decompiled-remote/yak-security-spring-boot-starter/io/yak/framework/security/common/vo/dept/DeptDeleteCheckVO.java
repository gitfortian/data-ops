/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.vo.dept;

import java.util.ArrayList;
import java.util.List;
import lombok.Generated;

public class DeptDeleteCheckVO {
    private Long deptId;
    private boolean deletable;
    private List<String> childDeptNameList = new ArrayList<String>();
    private List<String> userNameList = new ArrayList<String>();

    @Generated
    public DeptDeleteCheckVO() {
    }

    @Generated
    public Long getDeptId() {
        return this.deptId;
    }

    @Generated
    public boolean isDeletable() {
        return this.deletable;
    }

    @Generated
    public List<String> getChildDeptNameList() {
        return this.childDeptNameList;
    }

    @Generated
    public List<String> getUserNameList() {
        return this.userNameList;
    }

    @Generated
    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    @Generated
    public void setDeletable(boolean deletable) {
        this.deletable = deletable;
    }

    @Generated
    public void setChildDeptNameList(List<String> childDeptNameList) {
        this.childDeptNameList = childDeptNameList;
    }

    @Generated
    public void setUserNameList(List<String> userNameList) {
        this.userNameList = userNameList;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof DeptDeleteCheckVO)) {
            return false;
        }
        DeptDeleteCheckVO other = (DeptDeleteCheckVO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (this.isDeletable() != other.isDeletable()) {
            return false;
        }
        Long this$deptId = this.getDeptId();
        Long other$deptId = other.getDeptId();
        if (this$deptId == null ? other$deptId != null : !((Object)this$deptId).equals(other$deptId)) {
            return false;
        }
        List<String> this$childDeptNameList = this.getChildDeptNameList();
        List<String> other$childDeptNameList = other.getChildDeptNameList();
        if (this$childDeptNameList == null ? other$childDeptNameList != null : !((Object)this$childDeptNameList).equals(other$childDeptNameList)) {
            return false;
        }
        List<String> this$userNameList = this.getUserNameList();
        List<String> other$userNameList = other.getUserNameList();
        return !(this$userNameList == null ? other$userNameList != null : !((Object)this$userNameList).equals(other$userNameList));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof DeptDeleteCheckVO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        result = result * 59 + (this.isDeletable() ? 79 : 97);
        Long $deptId = this.getDeptId();
        result = result * 59 + ($deptId == null ? 43 : ((Object)$deptId).hashCode());
        List<String> $childDeptNameList = this.getChildDeptNameList();
        result = result * 59 + ($childDeptNameList == null ? 43 : ((Object)$childDeptNameList).hashCode());
        List<String> $userNameList = this.getUserNameList();
        result = result * 59 + ($userNameList == null ? 43 : ((Object)$userNameList).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "DeptDeleteCheckVO(deptId=" + this.getDeptId() + ", deletable=" + this.isDeletable() + ", childDeptNameList=" + String.valueOf(this.getChildDeptNameList()) + ", userNameList=" + String.valueOf(this.getUserNameList()) + ")";
    }
}

