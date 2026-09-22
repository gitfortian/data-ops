/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.common;

import io.yak.framework.common.BaseResult;
import io.yak.framework.common.BusinessException;
import io.yak.framework.common.CommonErrorCode;
import io.yak.framework.common.ErrorCode;
import lombok.Generated;

public class Result<T>
extends BaseResult {
    protected T data;

    private Result(Integer code) {
        this.code = code;
    }

    private Result(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public static <T> Result<T> build(boolean success) {
        return success ? Result.success() : Result.fail();
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = Result.success();
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success() {
        return new Result<T>(CommonErrorCode.SUCCESS.getCode(), CommonErrorCode.SUCCESS.getMessage());
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        if (errorCode == null) {
            return Result.fail();
        }
        return new Result<T>(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<T>(code, message);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<T>(CommonErrorCode.COMMON_FAIL.getCode(), message);
    }

    public static <T> Result<T> fail() {
        return Result.fail(CommonErrorCode.COMMON_FAIL);
    }

    public static <T> Result<T> fail(BusinessException exception) {
        if (exception == null) {
            return Result.fail();
        }
        if (exception.getErrorCode() != null) {
            return Result.fail(exception.getErrorCode());
        }
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? Result.fail() : Result.fail(message);
    }

    public static <T> Result<T> buildFrom(Result<?> source) {
        if (source == null) {
            return Result.fail();
        }
        return new Result<T>(source.getCode(), source.getMessage());
    }

    public static <T> Result<T> buildParamIllegal(String message) {
        String detail = message == null ? "" : message.trim();
        return new Result<T>(CommonErrorCode.PARAM_NOT_VALID.getCode(), CommonErrorCode.PARAM_NOT_VALID.getMessage() + (String)(detail.isEmpty() ? "" : "\uff1a" + detail) + "\uff0c\u8bf7\u68c0\u67e5\u540e\u518d\u63d0\u4ea4\uff01");
    }

    public static <T> Result<T> buildNotExist(String message) {
        return new Result<T>(CommonErrorCode.RESOURCE_NOT_EXISTS.getCode(), message);
    }

    public static <T> Result<T> buildDuplicate(String message) {
        return new Result<T>(CommonErrorCode.RESOURCE_DUPLICATION.getCode(), message);
    }

    public boolean succeeded() {
        return CommonErrorCode.SUCCESS.getCode().equals(this.getCode());
    }

    public boolean duplicate() {
        return CommonErrorCode.RESOURCE_DUPLICATION.getCode().equals(this.getCode());
    }

    public boolean failed() {
        return !this.succeeded();
    }

    @Generated
    public T getData() {
        return this.data;
    }

    @Generated
    public void setData(T data) {
        this.data = data;
    }

    @Generated
    public Result() {
    }

    @Override
    @Generated
    public String toString() {
        return "Result(super=" + super.toString() + ", data=" + String.valueOf(this.getData()) + ")";
    }

    @Override
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Result)) {
            return false;
        }
        Result other = (Result)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        T this$data = this.getData();
        T other$data = other.getData();
        return !(this$data == null ? other$data != null : !this$data.equals(other$data));
    }

    @Override
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof Result;
    }

    @Override
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        T $data = this.getData();
        result = result * 59 + ($data == null ? 43 : $data.hashCode());
        return result;
    }
}

