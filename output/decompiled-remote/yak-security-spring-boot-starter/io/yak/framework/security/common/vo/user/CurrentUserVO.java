/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.vo.user;

import io.yak.framework.security.common.vo.project.ProjectBriefVO;
import io.yak.framework.security.common.vo.role.RoleBriefVO;
import java.util.ArrayList;
import java.util.List;
import lombok.Generated;

public class CurrentUserVO {
    private Long id;
    private String userName;
    private String realName;
    private Long deptId;
    private String phone;
    private String email;
    private List<RoleBriefVO> roleList = new ArrayList<RoleBriefVO>();
    private List<String> permissionCodes = new ArrayList<String>();
    private List<String> menuCodes;
    private List<ProjectBriefVO> projectList = new ArrayList<ProjectBriefVO>();

    @Generated
    public CurrentUserVO() {
    }

    @Generated
    public Long getId() {
        return this.id;
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
    public Long getDeptId() {
        return this.deptId;
    }

    @Generated
    public String getPhone() {
        return this.phone;
    }

    @Generated
    public String getEmail() {
        return this.email;
    }

    @Generated
    public List<RoleBriefVO> getRoleList() {
        return this.roleList;
    }

    @Generated
    public List<String> getPermissionCodes() {
        return this.permissionCodes;
    }

    @Generated
    public List<String> getMenuCodes() {
        return this.menuCodes;
    }

    @Generated
    public List<ProjectBriefVO> getProjectList() {
        return this.projectList;
    }

    @Generated
    public void setId(Long id) {
        this.id = id;
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
    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    @Generated
    public void setPhone(String phone) {
        this.phone = phone;
    }

    @Generated
    public void setEmail(String email) {
        this.email = email;
    }

    @Generated
    public void setRoleList(List<RoleBriefVO> roleList) {
        this.roleList = roleList;
    }

    @Generated
    public void setPermissionCodes(List<String> permissionCodes) {
        this.permissionCodes = permissionCodes;
    }

    @Generated
    public void setMenuCodes(List<String> menuCodes) {
        this.menuCodes = menuCodes;
    }

    @Generated
    public void setProjectList(List<ProjectBriefVO> projectList) {
        this.projectList = projectList;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof CurrentUserVO)) {
            return false;
        }
        CurrentUserVO other = (CurrentUserVO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
            return false;
        }
        Long this$deptId = this.getDeptId();
        Long other$deptId = other.getDeptId();
        if (this$deptId == null ? other$deptId != null : !((Object)this$deptId).equals(other$deptId)) {
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
        String this$phone = this.getPhone();
        String other$phone = other.getPhone();
        if (this$phone == null ? other$phone != null : !this$phone.equals(other$phone)) {
            return false;
        }
        String this$email = this.getEmail();
        String other$email = other.getEmail();
        if (this$email == null ? other$email != null : !this$email.equals(other$email)) {
            return false;
        }
        List<RoleBriefVO> this$roleList = this.getRoleList();
        List<RoleBriefVO> other$roleList = other.getRoleList();
        if (this$roleList == null ? other$roleList != null : !((Object)this$roleList).equals(other$roleList)) {
            return false;
        }
        List<String> this$permissionCodes = this.getPermissionCodes();
        List<String> other$permissionCodes = other.getPermissionCodes();
        if (this$permissionCodes == null ? other$permissionCodes != null : !((Object)this$permissionCodes).equals(other$permissionCodes)) {
            return false;
        }
        List<String> this$menuCodes = this.getMenuCodes();
        List<String> other$menuCodes = other.getMenuCodes();
        if (this$menuCodes == null ? other$menuCodes != null : !((Object)this$menuCodes).equals(other$menuCodes)) {
            return false;
        }
        List<ProjectBriefVO> this$projectList = this.getProjectList();
        List<ProjectBriefVO> other$projectList = other.getProjectList();
        return !(this$projectList == null ? other$projectList != null : !((Object)this$projectList).equals(other$projectList));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof CurrentUserVO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        Long $deptId = this.getDeptId();
        result = result * 59 + ($deptId == null ? 43 : ((Object)$deptId).hashCode());
        String $userName = this.getUserName();
        result = result * 59 + ($userName == null ? 43 : $userName.hashCode());
        String $realName = this.getRealName();
        result = result * 59 + ($realName == null ? 43 : $realName.hashCode());
        String $phone = this.getPhone();
        result = result * 59 + ($phone == null ? 43 : $phone.hashCode());
        String $email = this.getEmail();
        result = result * 59 + ($email == null ? 43 : $email.hashCode());
        List<RoleBriefVO> $roleList = this.getRoleList();
        result = result * 59 + ($roleList == null ? 43 : ((Object)$roleList).hashCode());
        List<String> $permissionCodes = this.getPermissionCodes();
        result = result * 59 + ($permissionCodes == null ? 43 : ((Object)$permissionCodes).hashCode());
        List<String> $menuCodes = this.getMenuCodes();
        result = result * 59 + ($menuCodes == null ? 43 : ((Object)$menuCodes).hashCode());
        List<ProjectBriefVO> $projectList = this.getProjectList();
        result = result * 59 + ($projectList == null ? 43 : ((Object)$projectList).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "CurrentUserVO(id=" + this.getId() + ", userName=" + this.getUserName() + ", realName=" + this.getRealName() + ", deptId=" + this.getDeptId() + ", phone=" + this.getPhone() + ", email=" + this.getEmail() + ", roleList=" + String.valueOf(this.getRoleList()) + ", permissionCodes=" + String.valueOf(this.getPermissionCodes()) + ", menuCodes=" + String.valueOf(this.getMenuCodes()) + ", projectList=" + String.valueOf(this.getProjectList()) + ")";
    }
}

