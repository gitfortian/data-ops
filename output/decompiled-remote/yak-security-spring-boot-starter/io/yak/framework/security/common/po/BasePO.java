/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.annotation.IdType
 *  com.baomidou.mybatisplus.annotation.TableId
 *  com.baomidou.mybatisplus.annotation.TableLogic
 *  lombok.Generated
 */
package io.yak.framework.security.common.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import io.yak.framework.security.common.po.AppBasePO;
import java.util.Date;
import lombok.Generated;

public class BasePO
extends AppBasePO {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Date createTime;
    private Date updateTime;
    @TableLogic(value="0", delval="1")
    private int isDelete = 0;

    @Generated
    public Long getId() {
        return this.id;
    }

    @Generated
    public Date getCreateTime() {
        return this.createTime;
    }

    @Generated
    public Date getUpdateTime() {
        return this.updateTime;
    }

    @Generated
    public int getIsDelete() {
        return this.isDelete;
    }

    @Generated
    public void setId(Long id) {
        this.id = id;
    }

    @Generated
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Generated
    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    @Generated
    public void setIsDelete(int isDelete) {
        this.isDelete = isDelete;
    }

    @Generated
    public BasePO() {
    }

    @Override
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof BasePO)) {
            return false;
        }
        BasePO other = (BasePO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        if (this.getIsDelete() != other.getIsDelete()) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
            return false;
        }
        Date this$createTime = this.getCreateTime();
        Date other$createTime = other.getCreateTime();
        if (this$createTime == null ? other$createTime != null : !((Object)this$createTime).equals(other$createTime)) {
            return false;
        }
        Date this$updateTime = this.getUpdateTime();
        Date other$updateTime = other.getUpdateTime();
        return !(this$updateTime == null ? other$updateTime != null : !((Object)this$updateTime).equals(other$updateTime));
    }

    @Override
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof BasePO;
    }

    @Override
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        result = result * 59 + this.getIsDelete();
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        Date $createTime = this.getCreateTime();
        result = result * 59 + ($createTime == null ? 43 : ((Object)$createTime).hashCode());
        Date $updateTime = this.getUpdateTime();
        result = result * 59 + ($updateTime == null ? 43 : ((Object)$updateTime).hashCode());
        return result;
    }

    @Override
    @Generated
    public String toString() {
        return "BasePO(id=" + this.getId() + ", createTime=" + String.valueOf(this.getCreateTime()) + ", updateTime=" + String.valueOf(this.getUpdateTime()) + ", isDelete=" + this.getIsDelete() + ")";
    }
}

