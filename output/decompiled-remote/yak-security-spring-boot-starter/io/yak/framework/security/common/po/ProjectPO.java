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

@TableName(value="yak_security_project")
public class ProjectPO
extends BasePO {
    private String projectName;
    private String projectCode;
    private String description;
    private Boolean running;
    private Long deptId;

    @Generated
    public String getProjectName() {
        return this.projectName;
    }

    @Generated
    public String getProjectCode() {
        return this.projectCode;
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
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    @Generated
    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
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

    @Override
    @Generated
    public String toString() {
        return "ProjectPO(super=" + super.toString() + ", projectName=" + this.getProjectName() + ", projectCode=" + this.getProjectCode() + ", running=" + this.getRunning() + ", deptId=" + this.getDeptId() + ")";
    }

    @Override
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ProjectPO)) {
            return false;
        }
        ProjectPO other = (ProjectPO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (!super.equals(o)) {
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
        String this$projectCode = this.getProjectCode();
        String other$projectCode = other.getProjectCode();
        if (this$projectCode == null ? other$projectCode != null : !this$projectCode.equals(other$projectCode)) {
            return false;
        }
        String this$description = this.getDescription();
        String other$description = other.getDescription();
        return !(this$description == null ? other$description != null : !this$description.equals(other$description));
    }

    @Override
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof ProjectPO;
    }

    @Override
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        Boolean $running = this.getRunning();
        result = result * 59 + ($running == null ? 43 : ((Object)$running).hashCode());
        Long $deptId = this.getDeptId();
        result = result * 59 + ($deptId == null ? 43 : ((Object)$deptId).hashCode());
        String $projectName = this.getProjectName();
        result = result * 59 + ($projectName == null ? 43 : $projectName.hashCode());
        String $projectCode = this.getProjectCode();
        result = result * 59 + ($projectCode == null ? 43 : $projectCode.hashCode());
        String $description = this.getDescription();
        result = result * 59 + ($description == null ? 43 : $description.hashCode());
        return result;
    }
}

