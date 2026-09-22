/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.dao;

import io.yak.framework.security.common.entity.Permission;
import java.util.List;

public interface PermissionDao {
    public List<Permission> selectAllAndAscOrderByLevel();

    public void insertBatch(List<Permission> var1);

    public int deleteById(Long var1);

    public void synchronizeDeclared(List<Permission> var1);
}

