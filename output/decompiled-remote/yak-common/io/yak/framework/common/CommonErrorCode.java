/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.common;

import io.yak.framework.common.ErrorCode;

public enum CommonErrorCode implements ErrorCode
{
    SUCCESS(200, "\u6210\u529f"),
    COMMON_FAIL(999, "\u5931\u8d25"),
    PARAM_NOT_VALID(1001, "\u53c2\u6570\u65e0\u6548"),
    RESOURCE_DUPLICATION(10004, "\u6570\u636e\u5df2\u5b58\u5728"),
    RESOURCE_NOT_EXISTS(10010, "\u8d44\u6e90\u4e0d\u5b58\u5728");

    private final Integer code;
    private final String message;

    private CommonErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public Integer getCode() {
        return this.code;
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}

