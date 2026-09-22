/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.permission;

import io.yak.framework.security.permission.PermissionDefinition;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@FunctionalInterface
public interface PermissionDefinitionProvider {
    public List<PermissionDefinition> getPermissionDefinitions();

    public static PermissionDefinitionProvider of(PermissionDefinition ... definitions) {
        List values = definitions == null ? Collections.emptyList() : List.copyOf(Arrays.asList(definitions));
        return () -> values;
    }
}

