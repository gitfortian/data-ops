/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.extend.impl;

import io.yak.framework.security.extend.OperationLogExtend;

public class NoOpOperationLogExtend
implements OperationLogExtend {
    @Override
    public void record(String operator, String operation, String target, String detail) {
    }
}

