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

@TableName(value="yak_security_oplog_extra")
public class OplogExtraPO
extends BasePO {
    private String info;
    private Integer type;

    @Generated
    public String getInfo() {
        return this.info;
    }

    @Generated
    public Integer getType() {
        return this.type;
    }

    @Generated
    public void setInfo(String info) {
        this.info = info;
    }

    @Generated
    public void setType(Integer type) {
        this.type = type;
    }

    @Override
    @Generated
    public String toString() {
        return "OplogExtraPO(super=" + super.toString() + ", type=" + this.getType() + ")";
    }

    @Override
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof OplogExtraPO)) {
            return false;
        }
        OplogExtraPO other = (OplogExtraPO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        Integer this$type = this.getType();
        Integer other$type = other.getType();
        if (this$type == null ? other$type != null : !((Object)this$type).equals(other$type)) {
            return false;
        }
        String this$info = this.getInfo();
        String other$info = other.getInfo();
        return !(this$info == null ? other$info != null : !this$info.equals(other$info));
    }

    @Override
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof OplogExtraPO;
    }

    @Override
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        Integer $type = this.getType();
        result = result * 59 + ($type == null ? 43 : ((Object)$type).hashCode());
        String $info = this.getInfo();
        result = result * 59 + ($info == null ? 43 : $info.hashCode());
        return result;
    }
}

