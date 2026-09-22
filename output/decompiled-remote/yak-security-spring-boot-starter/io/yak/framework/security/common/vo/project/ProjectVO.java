/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.vo.project;

import io.yak.framework.security.common.vo.dept.DeptBriefVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import java.util.Date;
import java.util.List;
import lombok.Generated;

public class ProjectVO {
    private Long id;
    private String projectCode;
    private String projectName;
    private List<UserBriefVO> userList;
    private List<UserBriefVO> ownerList;
    private String description;
    private Boolean running;
    private List<DeptBriefVO> deptList;
    private Long deptId;
    private Date createTime;
    private Date updateTime;

    @Generated
    public ProjectVO() {
    }

    @Generated
    public Long getId() {
        return this.id;
    }

    @Generated
    public String getProjectCode() {
        return this.projectCode;
    }

    @Generated
    public String getProjectName() {
        return this.projectName;
    }

    @Generated
    public List<UserBriefVO> getUserList() {
        return this.userList;
    }

    @Generated
    public List<UserBriefVO> getOwnerList() {
        return this.ownerList;
    }

    @Generated
    public String getDescription() {
        return this.description;
    }

    @Generated
    public Boolean getRunning() {
        return this.running;
    }

    @Generated
    public List<DeptBriefVO> getDeptList() {
        return this.deptList;
    }

    @Generated
    public Long getDeptId() {
        return this.deptId;
    }

    @Generated
    public Date getCreateTime() {
        return this.createTime;
    }

    @Generated
    public Date getUpdateTime() {
        return this.updateTime;
    }

    @Generated
    public void setId(Long id) {
        this.id = id;
    }

    @Generated
    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    @Generated
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    @Generated
    public void setUserList(List<UserBriefVO> userList) {
        this.userList = userList;
    }

    @Generated
    public void setOwnerList(List<UserBriefVO> ownerList) {
        this.ownerList = ownerList;
    }

    @Generated
    public void setDescription(String description) {
        this.description = description;
    }

    @Generated
    public void setRunning(Boolean running) {
        this.running = running;
    }

    @Generated
    public void setDeptList(List<DeptBriefVO> deptList) {
        this.deptList = deptList;
    }

    @Generated
    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    @Generated
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Generated
    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ProjectVO)) {
            return false;
        }
        ProjectVO other = (ProjectVO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
            return false;
        }
        Boolean this$running = this.getRunning();
        Boolean other$running = other.getRunning();
        if (this$running == null ? other$running != null : !((Object)this$running).equals(other$running)) {
            return false;
        }
        Long this$deptId = this.getDeptId();
        Long other$deptId = other.getDeptId();
        if (this$deptId == null ? other$deptId != null : !((Object)this$deptId).equals(other$deptId)) {
            return false;
        }
        String this$projectCode = this.getProjectCode();
        String other$projectCode = other.getProjectCode();
        if (this$projectCode == null ? other$projectCode != null : !this$projectCode.equals(other$projectCode)) {
            return false;
        }
        String this$projectName = this.getProjectName();
        String other$projectName = other.getProjectName();
        if (this$projectName == null ? other$projectName != null : !this$projectName.equals(other$projectName)) {
            return false;
        }
        List<UserBriefVO> this$userList = this.getUserList();
        List<UserBriefVO> other$userList = other.getUserList();
        if (this$userList == null ? other$userList != null : !((Object)this$userList).equals(other$userList)) {
            return false;
        }
        List<UserBriefVO> this$ownerList = this.getOwnerList();
        List<UserBriefVO> other$ownerList = other.getOwnerList();
        if (this$ownerList == null ? other$ownerList != null : !((Object)this$ownerList).equals(other$ownerList)) {
            return false;
        }
        String this$description = this.getDescription();
        String other$description = other.getDescription();
        if (this$description == null ? other$description != null : !this$description.equals(other$description)) {
            return false;
        }
        List<DeptBriefVO> this$deptList = this.getDeptList();
        List<DeptBriefVO> other$deptList = other.getDeptList();
        if (this$deptList == null ? other$deptList != null : !((Object)this$deptList).equals(other$deptList)) {
            return false;
        }
        Date this$createTime = this.getCreateTime();
        Date other$createTime = other.getCreateTime();
        if (this$createTime == null ? other$createTime != null : !((Object)this$createTime).equals(other$createTime)) {
            return false;
        }
        Date this$updateTime = this.getUpdateTime();
        Date other$updateTime = other.getUpdateTime();
        return !(this$updateTime == null ? other$updateTime != null : !((Object)this$updateTime).equals(other$updateTime));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof ProjectVO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        Boolean $running = this.getRunning();
        result = result * 59 + ($running == null ? 43 : ((Object)$running).hashCode());
        Long $deptId = this.getDeptId();
        result = result * 59 + ($deptId == null ? 43 : ((Object)$deptId).hashCode());
        String $projectCode = this.getProjectCode();
        result = result * 59 + ($projectCode == null ? 43 : $projectCode.hashCode());
        String $projectName = this.getProjectName();
        result = result * 59 + ($projectName == null ? 43 : $projectName.hashCode());
        List<UserBriefVO> $userList = this.getUserList();
        result = result * 59 + ($userList == null ? 43 : ((Object)$userList).hashCode());
        List<UserBriefVO> $ownerList = this.getOwnerList();
        result = result * 59 + ($ownerList == null ? 43 : ((Object)$ownerList).hashCode());
        String $description = this.getDescription();
        result = result * 59 + ($description == null ? 43 : $description.hashCode());
        List<DeptBriefVO> $deptList = this.getDeptList();
        result = result * 59 + ($deptList == null ? 43 : ((Object)$deptList).hashCode());
        Date $createTime = this.getCreateTime();
        result = result * 59 + ($createTime == null ? 43 : ((Object)$createTime).hashCode());
        Date $updateTime = this.getUpdateTime();
        result = result * 59 + ($updateTime == null ? 43 : ((Object)$updateTime).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "ProjectVO(id=" + this.getId() + ", projectCode=" + this.getProjectCode() + ", projectName=" + this.getProjectName() + ", userList=" + String.valueOf(this.getUserList()) + ", ownerList=" + String.valueOf(this.getOwnerList()) + ", description=" + this.getDescription() + ", running=" + this.getRunning() + ", deptList=" + String.valueOf(this.getDeptList()) + ", deptId=" + this.getDeptId() + ", createTime=" + String.valueOf(this.getCreateTime()) + ", updateTime=" + String.valueOf(this.getUpdateTime()) + ")";
    }
}

