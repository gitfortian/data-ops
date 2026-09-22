/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.vo.resource;

import io.yak.framework.security.common.vo.dept.DeptBriefVO;
import java.util.List;
import lombok.Generated;

public class MByUVO {
    private Long userId;
    private String userName;
    private String realName;
    private List<DeptBriefVO> deptList;
    private Integer adminResourceCnt;
    private Integer viewResourceCnt;

    @Generated
    public MByUVO() {
    }

    @Generated
    public Long getUserId() {
        return this.userId;
    }

    @Generated
    public String getUserName() {
        return this.userName;
    }

    @Generated
    public String getRealName() {
        return this.realName;
    }

    @Generated
    public List<DeptBriefVO> getDeptList() {
        return this.deptList;
    }

    @Generated
    public Integer getAdminResourceCnt() {
        return this.adminResourceCnt;
    }

    @Generated
    public Integer getViewResourceCnt() {
        return this.viewResourceCnt;
    }

    @Generated
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    @Generated
    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Generated
    public void setRealName(String realName) {
        this.realName = realName;
    }

    @Generated
    public void setDeptList(List<DeptBriefVO> deptList) {
        this.deptList = deptList;
    }

    @Generated
    public void setAdminResourceCnt(Integer adminResourceCnt) {
        this.adminResourceCnt = adminResourceCnt;
    }

    @Generated
    public void setViewResourceCnt(Integer viewResourceCnt) {
        this.viewResourceCnt = viewResourceCnt;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof MByUVO)) {
            return false;
        }
        MByUVO other = (MByUVO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Long this$userId = this.getUserId();
        Long other$userId = other.getUserId();
        if (this$userId == null ? other$userId != null : !((Object)this$userId).equals(other$userId)) {
            return false;
        }
        Integer this$adminResourceCnt = this.getAdminResourceCnt();
        Integer other$adminResourceCnt = other.getAdminResourceCnt();
        if (this$adminResourceCnt == null ? other$adminResourceCnt != null : !((Object)this$adminResourceCnt).equals(other$adminResourceCnt)) {
            return false;
        }
        Integer this$viewResourceCnt = this.getViewResourceCnt();
        Integer other$viewResourceCnt = other.getViewResourceCnt();
        if (this$viewResourceCnt == null ? other$viewResourceCnt != null : !((Object)this$viewResourceCnt).equals(other$viewResourceCnt)) {
            return false;
        }
        String this$userName = this.getUserName();
        String other$userName = other.getUserName();
        if (this$userName == null ? other$userName != null : !this$userName.equals(other$userName)) {
            return false;
        }
        String this$realName = this.getRealName();
        String other$realName = other.getRealName();
        if (this$realName == null ? other$realName != null : !this$realName.equals(other$realName)) {
            return false;
        }
        List<DeptBriefVO> this$deptList = this.getDeptList();
        List<DeptBriefVO> other$deptList = other.getDeptList();
        return !(this$deptList == null ? other$deptList != null : !((Object)this$deptList).equals(other$deptList));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof MByUVO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $userId = this.getUserId();
        result = result * 59 + ($userId == null ? 43 : ((Object)$userId).hashCode());
        Integer $adminResourceCnt = this.getAdminResourceCnt();
        result = result * 59 + ($adminResourceCnt == null ? 43 : ((Object)$adminResourceCnt).hashCode());
        Integer $viewResourceCnt = this.getViewResourceCnt();
        result = result * 59 + ($viewResourceCnt == null ? 43 : ((Object)$viewResourceCnt).hashCode());
        String $userName = this.getUserName();
        result = result * 59 + ($userName == null ? 43 : $userName.hashCode());
        String $realName = this.getRealName();
        result = result * 59 + ($realName == null ? 43 : $realName.hashCode());
        List<DeptBriefVO> $deptList = this.getDeptList();
        result = result * 59 + ($deptList == null ? 43 : ((Object)$deptList).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "MByUVO(userId=" + this.getUserId() + ", userName=" + this.getUserName() + ", realName=" + this.getRealName() + ", deptList=" + String.valueOf(this.getDeptList()) + ", adminResourceCnt=" + this.getAdminResourceCnt() + ", viewResourceCnt=" + this.getViewResourceCnt() + ")";
    }
}

