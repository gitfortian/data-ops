/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.vo.oplog;

import java.util.Date;
import lombok.Generated;

public class OplogVO {
    private Long id;
    private String operatorIp;
    private String operator;
    private String operatePage;
    private String operateType;
    private String operationMethods;
    private String target;
    private String targetType;
    private String detail;
    private Date createTime;
    private Date updateTime;

    @Generated
    public OplogVO() {
    }

    @Generated
    public Long getId() {
        return this.id;
    }

    @Generated
    public String getOperatorIp() {
        return this.operatorIp;
    }

    @Generated
    public String getOperator() {
        return this.operator;
    }

    @Generated
    public String getOperatePage() {
        return this.operatePage;
    }

    @Generated
    public String getOperateType() {
        return this.operateType;
    }

    @Generated
    public String getOperationMethods() {
        return this.operationMethods;
    }

    @Generated
    public String getTarget() {
        return this.target;
    }

    @Generated
    public String getTargetType() {
        return this.targetType;
    }

    @Generated
    public String getDetail() {
        return this.detail;
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
    public void setId(Long id) {
        this.id = id;
    }

    @Generated
    public void setOperatorIp(String operatorIp) {
        this.operatorIp = operatorIp;
    }

    @Generated
    public void setOperator(String operator) {
        this.operator = operator;
    }

    @Generated
    public void setOperatePage(String operatePage) {
        this.operatePage = operatePage;
    }

    @Generated
    public void setOperateType(String operateType) {
        this.operateType = operateType;
    }

    @Generated
    public void setOperationMethods(String operationMethods) {
        this.operationMethods = operationMethods;
    }

    @Generated
    public void setTarget(String target) {
        this.target = target;
    }

    @Generated
    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    @Generated
    public void setDetail(String detail) {
        this.detail = detail;
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
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof OplogVO)) {
            return false;
        }
        OplogVO other = (OplogVO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
            return false;
        }
        String this$operatorIp = this.getOperatorIp();
        String other$operatorIp = other.getOperatorIp();
        if (this$operatorIp == null ? other$operatorIp != null : !this$operatorIp.equals(other$operatorIp)) {
            return false;
        }
        String this$operator = this.getOperator();
        String other$operator = other.getOperator();
        if (this$operator == null ? other$operator != null : !this$operator.equals(other$operator)) {
            return false;
        }
        String this$operatePage = this.getOperatePage();
        String other$operatePage = other.getOperatePage();
        if (this$operatePage == null ? other$operatePage != null : !this$operatePage.equals(other$operatePage)) {
            return false;
        }
        String this$operateType = this.getOperateType();
        String other$operateType = other.getOperateType();
        if (this$operateType == null ? other$operateType != null : !this$operateType.equals(other$operateType)) {
            return false;
        }
        String this$operationMethods = this.getOperationMethods();
        String other$operationMethods = other.getOperationMethods();
        if (this$operationMethods == null ? other$operationMethods != null : !this$operationMethods.equals(other$operationMethods)) {
            return false;
        }
        String this$target = this.getTarget();
        String other$target = other.getTarget();
        if (this$target == null ? other$target != null : !this$target.equals(other$target)) {
            return false;
        }
        String this$targetType = this.getTargetType();
        String other$targetType = other.getTargetType();
        if (this$targetType == null ? other$targetType != null : !this$targetType.equals(other$targetType)) {
            return false;
        }
        String this$detail = this.getDetail();
        String other$detail = other.getDetail();
        if (this$detail == null ? other$detail != null : !this$detail.equals(other$detail)) {
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

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof OplogVO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        String $operatorIp = this.getOperatorIp();
        result = result * 59 + ($operatorIp == null ? 43 : $operatorIp.hashCode());
        String $operator = this.getOperator();
        result = result * 59 + ($operator == null ? 43 : $operator.hashCode());
        String $operatePage = this.getOperatePage();
        result = result * 59 + ($operatePage == null ? 43 : $operatePage.hashCode());
        String $operateType = this.getOperateType();
        result = result * 59 + ($operateType == null ? 43 : $operateType.hashCode());
        String $operationMethods = this.getOperationMethods();
        result = result * 59 + ($operationMethods == null ? 43 : $operationMethods.hashCode());
        String $target = this.getTarget();
        result = result * 59 + ($target == null ? 43 : $target.hashCode());
        String $targetType = this.getTargetType();
        result = result * 59 + ($targetType == null ? 43 : $targetType.hashCode());
        String $detail = this.getDetail();
        result = result * 59 + ($detail == null ? 43 : $detail.hashCode());
        Date $createTime = this.getCreateTime();
        result = result * 59 + ($createTime == null ? 43 : ((Object)$createTime).hashCode());
        Date $updateTime = this.getUpdateTime();
        result = result * 59 + ($updateTime == null ? 43 : ((Object)$updateTime).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "OplogVO(id=" + this.getId() + ", operatorIp=" + this.getOperatorIp() + ", operator=" + this.getOperator() + ", operatePage=" + this.getOperatePage() + ", operateType=" + this.getOperateType() + ", operationMethods=" + this.getOperationMethods() + ", target=" + this.getTarget() + ", targetType=" + this.getTargetType() + ", detail=" + this.getDetail() + ", createTime=" + String.valueOf(this.getCreateTime()) + ", updateTime=" + String.valueOf(this.getUpdateTime()) + ")";
    }
}

