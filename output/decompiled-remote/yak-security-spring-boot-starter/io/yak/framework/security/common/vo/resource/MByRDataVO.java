/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.vo.resource;

import lombok.Generated;

public class MByRDataVO {
    private Long userId;
    private String userName;
    private String realName;
    private Integer hasLevel;

    @Generated
    public MByRDataVO() {
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
    public Integer getHasLevel() {
        return this.hasLevel;
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
    public void setHasLevel(Integer hasLevel) {
        this.hasLevel = hasLevel;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof MByRDataVO)) {
            return false;
        }
        MByRDataVO other = (MByRDataVO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Long this$userId = this.getUserId();
        Long other$userId = other.getUserId();
        if (this$userId == null ? other$userId != null : !((Object)this$userId).equals(other$userId)) {
            return false;
        }
        Integer this$hasLevel = this.getHasLevel();
        Integer other$hasLevel = other.getHasLevel();
        if (this$hasLevel == null ? other$hasLevel != null : !((Object)this$hasLevel).equals(other$hasLevel)) {
            return false;
        }
        String this$userName = this.getUserName();
        String other$userName = other.getUserName();
        if (this$userName == null ? other$userName != null : !this$userName.equals(other$userName)) {
            return false;
        }
        String this$realName = this.getRealName();
        String other$realName = other.getRealName();
        return !(this$realName == null ? other$realName != null : !this$realName.equals(other$realName));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof MByRDataVO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $userId = this.getUserId();
        result = result * 59 + ($userId == null ? 43 : ((Object)$userId).hashCode());
        Integer $hasLevel = this.getHasLevel();
        result = result * 59 + ($hasLevel == null ? 43 : ((Object)$hasLevel).hashCode());
        String $userName = this.getUserName();
        result = result * 59 + ($userName == null ? 43 : $userName.hashCode());
        String $realName = this.getRealName();
        result = result * 59 + ($realName == null ? 43 : $realName.hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "MByRDataVO(userId=" + this.getUserId() + ", userName=" + this.getUserName() + ", realName=" + this.getRealName() + ", hasLevel=" + this.getHasLevel() + ")";
    }
}

