/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.config;

import io.yak.framework.security.common.dto.PageParamDTO;
import lombok.Generated;

public class ConfigQueryDTO
extends PageParamDTO {
    private Long id;
    private String valueGroup;
    private String valueName;
    private Integer status;
    private String memo;
    private String operator;

    @Generated
    public ConfigQueryDTO() {
    }

    @Generated
    public Long getId() {
        return this.id;
    }

    @Generated
    public String getValueGroup() {
        return this.valueGroup;
    }

    @Generated
    public String getValueName() {
        return this.valueName;
    }

    @Generated
    public Integer getStatus() {
        return this.status;
    }

    @Generated
    public String getMemo() {
        return this.memo;
    }

    @Generated
    public String getOperator() {
        return this.operator;
    }

    @Generated
    public void setId(Long id) {
        this.id = id;
    }

    @Generated
    public void setValueGroup(String valueGroup) {
        this.valueGroup = valueGroup;
    }

    @Generated
    public void setValueName(String valueName) {
        this.valueName = valueName;
    }

    @Generated
    public void setStatus(Integer status) {
        this.status = status;
    }

    @Generated
    public void setMemo(String memo) {
        this.memo = memo;
    }

    @Generated
    public void setOperator(String operator) {
        this.operator = operator;
    }

    @Override
    @Generated
    public String toString() {
        return "ConfigQueryDTO(id=" + this.getId() + ", valueGroup=" + this.getValueGroup() + ", valueName=" + this.getValueName() + ", status=" + this.getStatus() + ", memo=" + this.getMemo() + ", operator=" + this.getOperator() + ")";
    }

    @Override
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ConfigQueryDTO)) {
            return false;
        }
        ConfigQueryDTO other = (ConfigQueryDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
            return false;
        }
        Integer this$status = this.getStatus();
        Integer other$status = other.getStatus();
        if (this$status == null ? other$status != null : !((Object)this$status).equals(other$status)) {
            return false;
        }
        String this$valueGroup = this.getValueGroup();
        String other$valueGroup = other.getValueGroup();
        if (this$valueGroup == null ? other$valueGroup != null : !this$valueGroup.equals(other$valueGroup)) {
            return false;
        }
        String this$valueName = this.getValueName();
        String other$valueName = other.getValueName();
        if (this$valueName == null ? other$valueName != null : !this$valueName.equals(other$valueName)) {
            return false;
        }
        String this$memo = this.getMemo();
        String other$memo = other.getMemo();
        if (this$memo == null ? other$memo != null : !this$memo.equals(other$memo)) {
            return false;
        }
        String this$operator = this.getOperator();
        String other$operator = other.getOperator();
        return !(this$operator == null ? other$operator != null : !this$operator.equals(other$operator));
    }

    @Override
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof ConfigQueryDTO;
    }

    @Override
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        Integer $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : ((Object)$status).hashCode());
        String $valueGroup = this.getValueGroup();
        result = result * 59 + ($valueGroup == null ? 43 : $valueGroup.hashCode());
        String $valueName = this.getValueName();
        result = result * 59 + ($valueName == null ? 43 : $valueName.hashCode());
        String $memo = this.getMemo();
        result = result * 59 + ($memo == null ? 43 : $memo.hashCode());
        String $operator = this.getOperator();
        result = result * 59 + ($operator == null ? 43 : $operator.hashCode());
        return result;
    }
}

