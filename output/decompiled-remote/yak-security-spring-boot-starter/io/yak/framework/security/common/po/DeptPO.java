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

@TableName(value="yak_security_dept")
public class DeptPO
extends BasePO {
    private String deptName;
    private String description;
    private Long parentId;
    private Boolean leaf;
    private Integer level;

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

    @Override
    @Generated
    public String toString() {
        return "DeptPO(super=" + super.toString() + ", deptName=" + this.getDeptName() + ", description=" + this.getDescription() + ", parentId=" + this.getParentId() + ", leaf=" + this.getLeaf() + ", level=" + this.getLevel() + ")";
    }

    @Override
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof DeptPO)) {
            return false;
        }
        DeptPO other = (DeptPO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (!super.equals(o)) {
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
        String this$deptName = this.getDeptName();
        String other$deptName = other.getDeptName();
        if (this$deptName == null ? other$deptName != null : !this$deptName.equals(other$deptName)) {
            return false;
        }
        String this$description = this.getDescription();
        String other$description = other.getDescription();
        return !(this$description == null ? other$description != null : !this$description.equals(other$description));
    }

    @Override
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof DeptPO;
    }

    @Override
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        Long $parentId = this.getParentId();
        result = result * 59 + ($parentId == null ? 43 : ((Object)$parentId).hashCode());
        Boolean $leaf = this.getLeaf();
        result = result * 59 + ($leaf == null ? 43 : ((Object)$leaf).hashCode());
        Integer $level = this.getLevel();
        result = result * 59 + ($level == null ? 43 : ((Object)$level).hashCode());
        String $deptName = this.getDeptName();
        result = result * 59 + ($deptName == null ? 43 : $deptName.hashCode());
        String $description = this.getDescription();
        result = result * 59 + ($description == null ? 43 : $description.hashCode());
        return result;
    }
}

