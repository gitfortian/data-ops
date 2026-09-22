/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.vo.project;

import java.util.List;
import lombok.Generated;

public class ProjectDeleteCheckVO {
    private Long projectId;
    private boolean deletable;
    private List<String> resourceNameList;

    public ProjectDeleteCheckVO(Long projectId, List<String> resourceNameList) {
        this.projectId = projectId;
        this.resourceNameList = resourceNameList;
        this.deletable = resourceNameList == null || resourceNameList.isEmpty();
    }

    @Generated
    public Long getProjectId() {
        return this.projectId;
    }

    @Generated
    public boolean isDeletable() {
        return this.deletable;
    }

    @Generated
    public List<String> getResourceNameList() {
        return this.resourceNameList;
    }

    @Generated
    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    @Generated
    public void setDeletable(boolean deletable) {
        this.deletable = deletable;
    }

    @Generated
    public void setResourceNameList(List<String> resourceNameList) {
        this.resourceNameList = resourceNameList;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ProjectDeleteCheckVO)) {
            return false;
        }
        ProjectDeleteCheckVO other = (ProjectDeleteCheckVO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (this.isDeletable() != other.isDeletable()) {
            return false;
        }
        Long this$projectId = this.getProjectId();
        Long other$projectId = other.getProjectId();
        if (this$projectId == null ? other$projectId != null : !((Object)this$projectId).equals(other$projectId)) {
            return false;
        }
        List<String> this$resourceNameList = this.getResourceNameList();
        List<String> other$resourceNameList = other.getResourceNameList();
        return !(this$resourceNameList == null ? other$resourceNameList != null : !((Object)this$resourceNameList).equals(other$resourceNameList));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof ProjectDeleteCheckVO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        result = result * 59 + (this.isDeletable() ? 79 : 97);
        Long $projectId = this.getProjectId();
        result = result * 59 + ($projectId == null ? 43 : ((Object)$projectId).hashCode());
        List<String> $resourceNameList = this.getResourceNameList();
        result = result * 59 + ($resourceNameList == null ? 43 : ((Object)$resourceNameList).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "ProjectDeleteCheckVO(projectId=" + this.getProjectId() + ", deletable=" + this.isDeletable() + ", resourceNameList=" + String.valueOf(this.getResourceNameList()) + ")";
    }
}

