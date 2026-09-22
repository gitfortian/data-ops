/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.handlers.MetaObjectHandler
 *  org.apache.ibatis.reflection.MetaObject
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.util.StringUtils;

public final class YakSecurityMetaObjectHandler
implements MetaObjectHandler {
    private final String applicationName;

    public YakSecurityMetaObjectHandler(String applicationName) {
        if (!StringUtils.hasText((String)applicationName)) {
            throw new IllegalArgumentException("applicationName must not be blank");
        }
        this.applicationName = applicationName;
    }

    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "appName", String.class, this.applicationName);
    }

    public void updateFill(MetaObject metaObject) {
    }
}

