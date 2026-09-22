/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value={ElementType.TYPE, ElementType.METHOD})
@Retention(value=RetentionPolicy.RUNTIME)
public @interface YakPermission {
    public String code();

    public String name();

    public String group();

    public String groupCode() default "";

    public String menuCode() default "";

    public String description() default "";
}

