/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.entity;

import lombok.Generated;

public class OplogExtra {
    private Long id;
    private String info;
    private Integer type;

    @Generated
    public OplogExtra() {
    }

    @Generated
    public Long getId() {
        return this.id;
    }

    @Generated
    public String getInfo() {
        return this.info;
    }

    @Generated
    public Integer getType() {
        return this.type;
    }

    @Generated
    public void setId(Long id) {
        this.id = id;
    }

    @Generated
    public void setInfo(String info) {
        this.info = info;
    }

    @Generated
    public void setType(Integer type) {
        this.type = type;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof OplogExtra)) {
            return false;
        }
        OplogExtra other = (OplogExtra)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
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

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof OplogExtra;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        Integer $type = this.getType();
        result = result * 59 + ($type == null ? 43 : ((Object)$type).hashCode());
        String $info = this.getInfo();
        result = result * 59 + ($info == null ? 43 : $info.hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "OplogExtra(id=" + this.getId() + ", info=" + this.getInfo() + ", type=" + this.getType() + ")";
    }
}

