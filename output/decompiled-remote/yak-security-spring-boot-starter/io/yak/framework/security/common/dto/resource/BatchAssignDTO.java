/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.resource;

import java.util.List;
import lombok.Generated;

public class BatchAssignDTO {
    private List<Long> userIdList;
    private Long projectId;
    private Long resourceTypeId;
    private List<Long> idList;
    private Integer controlLevel;
    private Boolean assignFlag;

    @Generated
    public BatchAssignDTO() {
    }

    @Generated
    public List<Long> getUserIdList() {
        return this.userIdList;
    }

    @Generated
    public Long getProjectId() {
        return this.projectId;
    }

    @Generated
    public Long getResourceTypeId() {
        return this.resourceTypeId;
    }

    @Generated
    public List<Long> getIdList() {
        return this.idList;
    }

    @Generated
    public Integer getControlLevel() {
        return this.controlLevel;
    }

    @Generated
    public Boolean getAssignFlag() {
        return this.assignFlag;
    }

    @Generated
    public void setUserIdList(List<Long> userIdList) {
        this.userIdList = userIdList;
    }

    @Generated
    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    @Generated
    public void setResourceTypeId(Long resourceTypeId) {
        this.resourceTypeId = resourceTypeId;
    }

    @Generated
    public void setIdList(List<Long> idList) {
        this.idList = idList;
    }

    @Generated
    public void setControlLevel(Integer controlLevel) {
        this.controlLevel = controlLevel;
    }

    @Generated
    public void setAssignFlag(Boolean assignFlag) {
        this.assignFlag = assignFlag;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof BatchAssignDTO)) {
            return false;
        }
        BatchAssignDTO other = (BatchAssignDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Long this$projectId = this.getProjectId();
        Long other$projectId = other.getProjectId();
        if (this$projectId == null ? other$projectId != null : !((Object)this$projectId).equals(other$projectId)) {
            return false;
        }
        Long this$resourceTypeId = this.getResourceTypeId();
        Long other$resourceTypeId = other.getResourceTypeId();
        if (this$resourceTypeId == null ? other$resourceTypeId != null : !((Object)this$resourceTypeId).equals(other$resourceTypeId)) {
            return false;
        }
        Integer this$controlLevel = this.getControlLevel();
        Integer other$controlLevel = other.getControlLevel();
        if (this$controlLevel == null ? other$controlLevel != null : !((Object)this$controlLevel).equals(other$controlLevel)) {
            return false;
        }
        Boolean this$assignFlag = this.getAssignFlag();
        Boolean other$assignFlag = other.getAssignFlag();
        if (this$assignFlag == null ? other$assignFlag != null : !((Object)this$assignFlag).equals(other$assignFlag)) {
            return false;
        }
        List<Long> this$userIdList = this.getUserIdList();
        List<Long> other$userIdList = other.getUserIdList();
        if (this$userIdList == null ? other$userIdList != null : !((Object)this$userIdList).equals(other$userIdList)) {
            return false;
        }
        List<Long> this$idList = this.getIdList();
        List<Long> other$idList = other.getIdList();
        return !(this$idList == null ? other$idList != null : !((Object)this$idList).equals(other$idList));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof BatchAssignDTO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $projectId = this.getProjectId();
        result = result * 59 + ($projectId == null ? 43 : ((Object)$projectId).hashCode());
        Long $resourceTypeId = this.getResourceTypeId();
        result = result * 59 + ($resourceTypeId == null ? 43 : ((Object)$resourceTypeId).hashCode());
        Integer $controlLevel = this.getControlLevel();
        result = result * 59 + ($controlLevel == null ? 43 : ((Object)$controlLevel).hashCode());
        Boolean $assignFlag = this.getAssignFlag();
        result = result * 59 + ($assignFlag == null ? 43 : ((Object)$assignFlag).hashCode());
        List<Long> $userIdList = this.getUserIdList();
        result = result * 59 + ($userIdList == null ? 43 : ((Object)$userIdList).hashCode());
        List<Long> $idList = this.getIdList();
        result = result * 59 + ($idList == null ? 43 : ((Object)$idList).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "BatchAssignDTO(userIdList=" + String.valueOf(this.getUserIdList()) + ", projectId=" + this.getProjectId() + ", resourceTypeId=" + this.getResourceTypeId() + ", idList=" + String.valueOf(this.getIdList()) + ", controlLevel=" + this.getControlLevel() + ", assignFlag=" + this.getAssignFlag() + ")";
    }
}

