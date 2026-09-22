/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.project;

import java.util.List;
import lombok.Generated;

public class ProjectUserAssignDTO {
    private List<Long> userIdList;

    @Generated
    public ProjectUserAssignDTO() {
    }

    @Generated
    public List<Long> getUserIdList() {
        return this.userIdList;
    }

    @Generated
    public void setUserIdList(List<Long> userIdList) {
        this.userIdList = userIdList;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ProjectUserAssignDTO)) {
            return false;
        }
        ProjectUserAssignDTO other = (ProjectUserAssignDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        List<Long> this$userIdList = this.getUserIdList();
        List<Long> other$userIdList = other.getUserIdList();
        return !(this$userIdList == null ? other$userIdList != null : !((Object)this$userIdList).equals(other$userIdList));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof ProjectUserAssignDTO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        List<Long> $userIdList = this.getUserIdList();
        result = result * 59 + ($userIdList == null ? 43 : ((Object)$userIdList).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "ProjectUserAssignDTO(userIdList=" + String.valueOf(this.getUserIdList()) + ")";
    }
}

