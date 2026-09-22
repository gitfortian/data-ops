/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.project;

import io.yak.framework.security.common.dto.PageParamDTO;
import lombok.Generated;

public class ProjectQueryDTO
extends PageParamDTO {
    private String projectName;
    private String projectCode;
    private String chargeUsername;
    private Long deptId;
    private Boolean running;

    @Generated
    public ProjectQueryDTO() {
    }

    @Generated
    public String getProjectName() {
        return this.projectName;
    }

    @Generated
    public String getProjectCode() {
        return this.projectCode;
    }

    @Generated
    public String getChargeUsername() {
        return this.chargeUsername;
    }

    @Generated
    public Long getDeptId() {
        return this.deptId;
    }

    @Generated
    public Boolean getRunning() {
        return this.running;
    }

    @Generated
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    @Generated
    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    @Generated
    public void setChargeUsername(String chargeUsername) {
        this.chargeUsername = chargeUsername;
    }

    @Generated
    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    @Generated
    public void setRunning(Boolean running) {
        this.running = running;
    }

    @Override
    @Generated
    public String toString() {
        return "ProjectQueryDTO(projectName=" + this.getProjectName() + ", projectCode=" + this.getProjectCode() + ", chargeUsername=" + this.getChargeUsername() + ", deptId=" + this.getDeptId() + ", running=" + this.getRunning() + ")";
    }

    @Override
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ProjectQueryDTO)) {
            return false;
        }
        ProjectQueryDTO other = (ProjectQueryDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        Long this$deptId = this.getDeptId();
        Long other$deptId = other.getDeptId();
        if (this$deptId == null ? other$deptId != null : !((Object)this$deptId).equals(other$deptId)) {
            return false;
        }
        Boolean this$running = this.getRunning();
        Boolean other$running = other.getRunning();
        if (this$running == null ? other$running != null : !((Object)this$running).equals(other$running)) {
            return false;
        }
        String this$projectName = this.getProjectName();
        String other$projectName = other.getProjectName();
        if (this$projectName == null ? other$projectName != null : !this$projectName.equals(other$projectName)) {
            return false;
        }
        String this$projectCode = this.getProjectCode();
        String other$projectCode = other.getProjectCode();
        if (this$projectCode == null ? other$projectCode != null : !this$projectCode.equals(other$projectCode)) {
            return false;
        }
        String this$chargeUsername = this.getChargeUsername();
        String other$chargeUsername = other.getChargeUsername();
        return !(this$chargeUsername == null ? other$chargeUsername != null : !this$chargeUsername.equals(other$chargeUsername));
    }

    @Override
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof ProjectQueryDTO;
    }

    @Override
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        Long $deptId = this.getDeptId();
        result = result * 59 + ($deptId == null ? 43 : ((Object)$deptId).hashCode());
        Boolean $running = this.getRunning();
        result = result * 59 + ($running == null ? 43 : ((Object)$running).hashCode());
        String $projectName = this.getProjectName();
        result = result * 59 + ($projectName == null ? 43 : $projectName.hashCode());
        String $projectCode = this.getProjectCode();
        result = result * 59 + ($projectCode == null ? 43 : $projectCode.hashCode());
        String $chargeUsername = this.getChargeUsername();
        result = result * 59 + ($chargeUsername == null ? 43 : $chargeUsername.hashCode());
        return result;
    }
}

