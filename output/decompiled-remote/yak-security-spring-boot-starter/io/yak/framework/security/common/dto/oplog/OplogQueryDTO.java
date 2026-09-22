/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.oplog;

import io.yak.framework.security.common.dto.PageParamDTO;
import lombok.Generated;

public class OplogQueryDTO
extends PageParamDTO {
    private String operateType;
    private String operatePage;
    private String detail;
    private String operator;
    private String operatorIp;
    private String target;
    private String targetType;
    private String operationMethods;
    private Long startTime;
    private Long endTime;

    @Generated
    public OplogQueryDTO() {
    }

    @Generated
    public String getOperateType() {
        return this.operateType;
    }

    @Generated
    public String getOperatePage() {
        return this.operatePage;
    }

    @Generated
    public String getDetail() {
        return this.detail;
    }

    @Generated
    public String getOperator() {
        return this.operator;
    }

    @Generated
    public String getOperatorIp() {
        return this.operatorIp;
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
    public String getOperationMethods() {
        return this.operationMethods;
    }

    @Generated
    public Long getStartTime() {
        return this.startTime;
    }

    @Generated
    public Long getEndTime() {
        return this.endTime;
    }

    @Generated
    public void setOperateType(String operateType) {
        this.operateType = operateType;
    }

    @Generated
    public void setOperatePage(String operatePage) {
        this.operatePage = operatePage;
    }

    @Generated
    public void setDetail(String detail) {
        this.detail = detail;
    }

    @Generated
    public void setOperator(String operator) {
        this.operator = operator;
    }

    @Generated
    public void setOperatorIp(String operatorIp) {
        this.operatorIp = operatorIp;
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
    public void setOperationMethods(String operationMethods) {
        this.operationMethods = operationMethods;
    }

    @Generated
    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    @Generated
    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    @Override
    @Generated
    public String toString() {
        return "OplogQueryDTO(operateType=" + this.getOperateType() + ", operatePage=" + this.getOperatePage() + ", detail=" + this.getDetail() + ", operator=" + this.getOperator() + ", operatorIp=" + this.getOperatorIp() + ", target=" + this.getTarget() + ", targetType=" + this.getTargetType() + ", operationMethods=" + this.getOperationMethods() + ", startTime=" + this.getStartTime() + ", endTime=" + this.getEndTime() + ")";
    }

    @Override
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof OplogQueryDTO)) {
            return false;
        }
        OplogQueryDTO other = (OplogQueryDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        Long this$startTime = this.getStartTime();
        Long other$startTime = other.getStartTime();
        if (this$startTime == null ? other$startTime != null : !((Object)this$startTime).equals(other$startTime)) {
            return false;
        }
        Long this$endTime = this.getEndTime();
        Long other$endTime = other.getEndTime();
        if (this$endTime == null ? other$endTime != null : !((Object)this$endTime).equals(other$endTime)) {
            return false;
        }
        String this$operateType = this.getOperateType();
        String other$operateType = other.getOperateType();
        if (this$operateType == null ? other$operateType != null : !this$operateType.equals(other$operateType)) {
            return false;
        }
        String this$operatePage = this.getOperatePage();
        String other$operatePage = other.getOperatePage();
        if (this$operatePage == null ? other$operatePage != null : !this$operatePage.equals(other$operatePage)) {
            return false;
        }
        String this$detail = this.getDetail();
        String other$detail = other.getDetail();
        if (this$detail == null ? other$detail != null : !this$detail.equals(other$detail)) {
            return false;
        }
        String this$operator = this.getOperator();
        String other$operator = other.getOperator();
        if (this$operator == null ? other$operator != null : !this$operator.equals(other$operator)) {
            return false;
        }
        String this$operatorIp = this.getOperatorIp();
        String other$operatorIp = other.getOperatorIp();
        if (this$operatorIp == null ? other$operatorIp != null : !this$operatorIp.equals(other$operatorIp)) {
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
        String this$operationMethods = this.getOperationMethods();
        String other$operationMethods = other.getOperationMethods();
        return !(this$operationMethods == null ? other$operationMethods != null : !this$operationMethods.equals(other$operationMethods));
    }

    @Override
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof OplogQueryDTO;
    }

    @Override
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        Long $startTime = this.getStartTime();
        result = result * 59 + ($startTime == null ? 43 : ((Object)$startTime).hashCode());
        Long $endTime = this.getEndTime();
        result = result * 59 + ($endTime == null ? 43 : ((Object)$endTime).hashCode());
        String $operateType = this.getOperateType();
        result = result * 59 + ($operateType == null ? 43 : $operateType.hashCode());
        String $operatePage = this.getOperatePage();
        result = result * 59 + ($operatePage == null ? 43 : $operatePage.hashCode());
        String $detail = this.getDetail();
        result = result * 59 + ($detail == null ? 43 : $detail.hashCode());
        String $operator = this.getOperator();
        result = result * 59 + ($operator == null ? 43 : $operator.hashCode());
        String $operatorIp = this.getOperatorIp();
        result = result * 59 + ($operatorIp == null ? 43 : $operatorIp.hashCode());
        String $target = this.getTarget();
        result = result * 59 + ($target == null ? 43 : $target.hashCode());
        String $targetType = this.getTargetType();
        result = result * 59 + ($targetType == null ? 43 : $targetType.hashCode());
        String $operationMethods = this.getOperationMethods();
        result = result * 59 + ($operationMethods == null ? 43 : $operationMethods.hashCode());
        return result;
    }
}

