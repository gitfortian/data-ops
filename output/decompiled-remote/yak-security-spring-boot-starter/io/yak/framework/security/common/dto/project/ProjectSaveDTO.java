/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.project;

import java.util.List;
import lombok.Generated;

public class ProjectSaveDTO {
    private Long id;
    private String projectName;
    private List<Long> userIdList;
    private List<Long> ownerIdList;
    private String description;
    private Boolean running;
    private Long deptId;

    @Generated
    public ProjectSaveDTO() {
    }

    @Generated
    public Long getId() {
        return this.id;
    }

    @Generated
    public String getProjectName() {
        return this.projectName;
    }

    @Generated
    public List<Long> getUserIdList() {
        return this.userIdList;
    }

    @Generated
    public List<Long> getOwnerIdList() {
        return this.ownerIdList;
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
    public Long getDeptId() {
        return this.deptId;
    }

    @Generated
    public void setId(Long id) {
        this.id = id;
    }

    @Generated
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    @Generated
    public void setUserIdList(List<Long> userIdList) {
        this.userIdList = userIdList;
    }

    @Generated
    public void setOwnerIdList(List<Long> ownerIdList) {
        this.ownerIdList = ownerIdList;
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
    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ProjectSaveDTO)) {
            return false;
        }
        ProjectSaveDTO other = (ProjectSaveDTO)o;
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
        String this$projectName = this.getProjectName();
        String other$projectName = other.getProjectName();
        if (this$projectName == null ? other$projectName != null : !this$projectName.equals(other$projectName)) {
            return false;
        }
        List<Long> this$userIdList = this.getUserIdList();
        List<Long> other$userIdList = other.getUserIdList();
        if (this$userIdList == null ? other$userIdList != null : !((Object)this$userIdList).equals(other$userIdList)) {
            return false;
        }
        List<Long> this$ownerIdList = this.getOwnerIdList();
        List<Long> other$ownerIdList = other.getOwnerIdList();
        if (this$ownerIdList == null ? other$ownerIdList != null : !((Object)this$ownerIdList).equals(other$ownerIdList)) {
            return false;
        }
        String this$description = this.getDescription();
        String other$description = other.getDescription();
        return !(this$description == null ? other$description != null : !this$description.equals(other$description));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof ProjectSaveDTO;
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
        String $projectName = this.getProjectName();
        result = result * 59 + ($projectName == null ? 43 : $projectName.hashCode());
        List<Long> $userIdList = this.getUserIdList();
        result = result * 59 + ($userIdList == null ? 43 : ((Object)$userIdList).hashCode());
        List<Long> $ownerIdList = this.getOwnerIdList();
        result = result * 59 + ($ownerIdList == null ? 43 : ((Object)$ownerIdList).hashCode());
        String $description = this.getDescription();
        result = result * 59 + ($description == null ? 43 : $description.hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "ProjectSaveDTO(id=" + this.getId() + ", projectName=" + this.getProjectName() + ", userIdList=" + String.valueOf(this.getUserIdList()) + ", ownerIdList=" + String.valueOf(this.getOwnerIdList()) + ", description=" + this.getDescription() + ", running=" + this.getRunning() + ", deptId=" + this.getDeptId() + ")";
    }
}

