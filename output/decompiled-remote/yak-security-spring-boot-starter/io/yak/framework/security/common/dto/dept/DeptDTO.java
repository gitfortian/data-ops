/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.dept;

import java.util.ArrayList;
import java.util.List;
import lombok.Generated;

public class DeptDTO {
    private String deptName;
    private String description;
    private List<DeptDTO> childDeptDTOList;

    public List<DeptDTO> getChildDeptDTOList() {
        if (this.childDeptDTOList == null) {
            this.childDeptDTOList = new ArrayList<DeptDTO>();
        }
        return this.childDeptDTOList;
    }

    @Generated
    public DeptDTO() {
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
    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    @Generated
    public void setDescription(String description) {
        this.description = description;
    }

    @Generated
    public void setChildDeptDTOList(List<DeptDTO> childDeptDTOList) {
        this.childDeptDTOList = childDeptDTOList;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof DeptDTO)) {
            return false;
        }
        DeptDTO other = (DeptDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        String this$deptName = this.getDeptName();
        String other$deptName = other.getDeptName();
        if (this$deptName == null ? other$deptName != null : !this$deptName.equals(other$deptName)) {
            return false;
        }
        String this$description = this.getDescription();
        String other$description = other.getDescription();
        if (this$description == null ? other$description != null : !this$description.equals(other$description)) {
            return false;
        }
        List<DeptDTO> this$childDeptDTOList = this.getChildDeptDTOList();
        List<DeptDTO> other$childDeptDTOList = other.getChildDeptDTOList();
        return !(this$childDeptDTOList == null ? other$childDeptDTOList != null : !((Object)this$childDeptDTOList).equals(other$childDeptDTOList));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof DeptDTO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        String $deptName = this.getDeptName();
        result = result * 59 + ($deptName == null ? 43 : $deptName.hashCode());
        String $description = this.getDescription();
        result = result * 59 + ($description == null ? 43 : $description.hashCode());
        List<DeptDTO> $childDeptDTOList = this.getChildDeptDTOList();
        result = result * 59 + ($childDeptDTOList == null ? 43 : ((Object)$childDeptDTOList).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "DeptDTO(deptName=" + this.getDeptName() + ", description=" + this.getDescription() + ", childDeptDTOList=" + String.valueOf(this.getChildDeptDTOList()) + ")";
    }
}

