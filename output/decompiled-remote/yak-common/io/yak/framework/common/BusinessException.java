/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.common;

import io.yak.framework.common.ErrorCode;

public class BusinessException
extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(BusinessException.formatMessage(errorCode), cause);
        this.errorCode = errorCode;
    }

    public BusinessException(String message) {
        super(message);
        this.errorCode = null;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
    }

    public BusinessException(Throwable cause) {
        super(cause);
        this.errorCode = null;
    }

    private static String formatMessage(ErrorCode errorCode) {
        if (errorCode == null) {
            return null;
        }
        return errorCode.getCode() + "-" + errorCode.getMessage();
    }

    public ErrorCode getErrorCode() {
        return this.errorCode;
    }
}

