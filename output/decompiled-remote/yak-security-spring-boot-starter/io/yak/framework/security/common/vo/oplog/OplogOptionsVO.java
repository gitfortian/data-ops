/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.vo.oplog;

import java.util.ArrayList;
import java.util.List;
import lombok.Generated;

public class OplogOptionsVO {
    private List<String> operateTypes = new ArrayList<String>();
    private List<String> operatePages = new ArrayList<String>();
    private List<String> operationMethods = new ArrayList<String>();
    private List<String> targetTypes = new ArrayList<String>();

    @Generated
    public OplogOptionsVO() {
    }

    @Generated
    public List<String> getOperateTypes() {
        return this.operateTypes;
    }

    @Generated
    public List<String> getOperatePages() {
        return this.operatePages;
    }

    @Generated
    public List<String> getOperationMethods() {
        return this.operationMethods;
    }

    @Generated
    public List<String> getTargetTypes() {
        return this.targetTypes;
    }

    @Generated
    public void setOperateTypes(List<String> operateTypes) {
        this.operateTypes = operateTypes;
    }

    @Generated
    public void setOperatePages(List<String> operatePages) {
        this.operatePages = operatePages;
    }

    @Generated
    public void setOperationMethods(List<String> operationMethods) {
        this.operationMethods = operationMethods;
    }

    @Generated
    public void setTargetTypes(List<String> targetTypes) {
        this.targetTypes = targetTypes;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof OplogOptionsVO)) {
            return false;
        }
        OplogOptionsVO other = (OplogOptionsVO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        List<String> this$operateTypes = this.getOperateTypes();
        List<String> other$operateTypes = other.getOperateTypes();
        if (this$operateTypes == null ? other$operateTypes != null : !((Object)this$operateTypes).equals(other$operateTypes)) {
            return false;
        }
        List<String> this$operatePages = this.getOperatePages();
        List<String> other$operatePages = other.getOperatePages();
        if (this$operatePages == null ? other$operatePages != null : !((Object)this$operatePages).equals(other$operatePages)) {
            return false;
        }
        List<String> this$operationMethods = this.getOperationMethods();
        List<String> other$operationMethods = other.getOperationMethods();
        if (this$operationMethods == null ? other$operationMethods != null : !((Object)this$operationMethods).equals(other$operationMethods)) {
            return false;
        }
        List<String> this$targetTypes = this.getTargetTypes();
        List<String> other$targetTypes = other.getTargetTypes();
        return !(this$targetTypes == null ? other$targetTypes != null : !((Object)this$targetTypes).equals(other$targetTypes));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof OplogOptionsVO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        List<String> $operateTypes = this.getOperateTypes();
        result = result * 59 + ($operateTypes == null ? 43 : ((Object)$operateTypes).hashCode());
        List<String> $operatePages = this.getOperatePages();
        result = result * 59 + ($operatePages == null ? 43 : ((Object)$operatePages).hashCode());
        List<String> $operationMethods = this.getOperationMethods();
        result = result * 59 + ($operationMethods == null ? 43 : ((Object)$operationMethods).hashCode());
        List<String> $targetTypes = this.getTargetTypes();
        result = result * 59 + ($targetTypes == null ? 43 : ((Object)$targetTypes).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "OplogOptionsVO(operateTypes=" + String.valueOf(this.getOperateTypes()) + ", operatePages=" + String.valueOf(this.getOperatePages()) + ", operationMethods=" + String.valueOf(this.getOperationMethods()) + ", targetTypes=" + String.valueOf(this.getTargetTypes()) + ")";
    }
}

