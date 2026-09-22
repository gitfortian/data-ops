/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.dao;

import io.yak.framework.security.common.entity.RolePermission;
import java.util.List;

public interface RolePermissionDao {
    public void insertBatch(List<RolePermission> var1);

    public void deleteByRoleId(Long var1);

    public void deleteByPermissionId(Long var1);

    public List<Long> selectPermissionIdListByRoleId(Long var1);

    public List<Long> selectPermissionIdListByRoleIdList(List<Long> var1);
}

