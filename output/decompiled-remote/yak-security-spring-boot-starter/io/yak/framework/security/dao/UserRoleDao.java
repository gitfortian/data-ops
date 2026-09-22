/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.dao;

import io.yak.framework.security.common.entity.UserRole;
import io.yak.framework.security.common.po.UserRolePO;
import java.util.List;

public interface UserRoleDao {
    public List<Long> selectUserIdListByRoleId(Long var1);

    public List<Long> selectRoleIdListByUserId(Long var1);

    public void insertBatch(List<UserRole> var1);

    public int deleteByUserIdOrRoleId(Long var1, Long var2);

    public int selectCountByRoleId(Long var1);

    public List<UserRolePO> selectByRoleIds(List<Long> var1);

    public List<UserRolePO> getRoleIdListByUserIds(List<Long> var1);
}

