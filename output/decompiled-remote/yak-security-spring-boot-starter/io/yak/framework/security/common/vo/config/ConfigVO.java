/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.vo.config;

import java.util.Date;
import lombok.Generated;

public class ConfigVO {
    private Long id;
    private String valueGroup;
    private String valueName;
    private String value;
    private Integer status;
    private String memo;
    private Date createTime;
    private Date updateTime;
    private String operator;

    public ConfigVO() {
    }

    public ConfigVO(Long id, String valueGroup, String valueName, String value, Integer status, String memo, Date createTime, Date updateTime, String operator) {
        this.id = id;
        this.valueGroup = valueGroup;
        this.valueName = valueName;
        this.value = value;
        this.status = status;
        this.memo = memo;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.operator = operator;
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
    public String getValue() {
        return this.value;
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
    public Date getCreateTime() {
        return this.createTime;
    }

    @Generated
    public Date getUpdateTime() {
        return this.updateTime;
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
    public void setValue(String value) {
        this.value = value;
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
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Generated
    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    @Generated
    public void setOperator(String operator) {
        this.operator = operator;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ConfigVO)) {
            return false;
        }
        ConfigVO other = (ConfigVO)o;
        if (!other.canEqual(this)) {
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
        String this$value = this.getValue();
        String other$value = other.getValue();
        if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
            return false;
        }
        String this$memo = this.getMemo();
        String other$memo = other.getMemo();
        if (this$memo == null ? other$memo != null : !this$memo.equals(other$memo)) {
            return false;
        }
        Date this$createTime = this.getCreateTime();
        Date other$createTime = other.getCreateTime();
        if (this$createTime == null ? other$createTime != null : !((Object)this$createTime).equals(other$createTime)) {
            return false;
        }
        Date this$updateTime = this.getUpdateTime();
        Date other$updateTime = other.getUpdateTime();
        if (this$updateTime == null ? other$updateTime != null : !((Object)this$updateTime).equals(other$updateTime)) {
            return false;
        }
        String this$operator = this.getOperator();
        String other$operator = other.getOperator();
        return !(this$operator == null ? other$operator != null : !this$operator.equals(other$operator));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof ConfigVO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        Integer $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : ((Object)$status).hashCode());
        String $valueGroup = this.getValueGroup();
        result = result * 59 + ($valueGroup == null ? 43 : $valueGroup.hashCode());
        String $valueName = this.getValueName();
        result = result * 59 + ($valueName == null ? 43 : $valueName.hashCode());
        String $value = this.getValue();
        result = result * 59 + ($value == null ? 43 : $value.hashCode());
        String $memo = this.getMemo();
        result = result * 59 + ($memo == null ? 43 : $memo.hashCode());
        Date $createTime = this.getCreateTime();
        result = result * 59 + ($createTime == null ? 43 : ((Object)$createTime).hashCode());
        Date $updateTime = this.getUpdateTime();
        result = result * 59 + ($updateTime == null ? 43 : ((Object)$updateTime).hashCode());
        String $operator = this.getOperator();
        result = result * 59 + ($operator == null ? 43 : $operator.hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "ConfigVO(id=" + this.getId() + ", valueGroup=" + this.getValueGroup() + ", valueName=" + this.getValueName() + ", value=" + this.getValue() + ", status=" + this.getStatus() + ", memo=" + this.getMemo() + ", createTime=" + String.valueOf(this.getCreateTime()) + ", updateTime=" + String.valueOf(this.getUpdateTime()) + ", operator=" + this.getOperator() + ")";
    }
}

