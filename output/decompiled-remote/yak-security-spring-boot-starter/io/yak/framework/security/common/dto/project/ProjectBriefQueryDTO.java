/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.project;

import io.yak.framework.security.common.dto.PageParamDTO;
import io.yak.framework.security.common.dto.resource.MByRQueryDTO;
import lombok.Generated;

public class ProjectBriefQueryDTO
extends PageParamDTO {
    private String projectName;

    public ProjectBriefQueryDTO(MByRQueryDTO queryDTO) {
        this.setPage(queryDTO.getPage());
        this.setSize(queryDTO.getSize());
        this.projectName = queryDTO.getName();
    }

    @Generated
    public String getProjectName() {
        return this.projectName;
    }

    @Generated
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    @Override
    @Generated
    public String toString() {
        return "ProjectBriefQueryDTO(projectName=" + this.getProjectName() + ")";
    }

    @Override
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ProjectBriefQueryDTO)) {
            return false;
        }
        ProjectBriefQueryDTO other = (ProjectBriefQueryDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        String this$projectName = this.getProjectName();
        String other$projectName = other.getProjectName();
        return !(this$projectName == null ? other$projectName != null : !this$projectName.equals(other$projectName));
    }

    @Override
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof ProjectBriefQueryDTO;
    }

    @Override
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        String $projectName = this.getProjectName();
        result = result * 59 + ($projectName == null ? 43 : $projectName.hashCode());
        return result;
    }
}

