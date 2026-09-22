/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.conditions.Wrapper
 *  com.baomidou.mybatisplus.core.toolkit.Wrappers
 *  com.baomidou.mybatisplus.core.toolkit.support.SFunction
 *  lombok.Generated
 *  org.springframework.stereotype.Repository
 */
package io.yak.framework.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import io.yak.framework.security.common.entity.RolePermission;
import io.yak.framework.security.common.po.RolePermissionPO;
import io.yak.framework.security.dao.RolePermissionDao;
import io.yak.framework.security.dao.mapper.RolePermissionMapper;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.DatabaseNumberUtils;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Generated;
import org.springframework.stereotype.Repository;

@Repository
public class RolePermissionDaoImpl
implements RolePermissionDao {
    private final RolePermissionMapper rolePermissionMapper;

    @Override
    public void insertBatch(List<RolePermission> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        CopyBeanUtil.copyList(items, RolePermissionPO.class).forEach(arg_0 -> ((RolePermissionMapper)this.rolePermissionMapper).insert(arg_0));
    }

    @Override
    public void deleteByRoleId(Long roleId) {
        if (roleId == null) {
            return;
        }
        this.rolePermissionMapper.delete((Wrapper)Wrappers.lambdaQuery().eq(RolePermissionPO::getRoleId, (Object)roleId));
    }

    @Override
    public void deleteByPermissionId(Long permissionId) {
        if (permissionId == null) {
            return;
        }
        this.rolePermissionMapper.delete((Wrapper)Wrappers.lambdaQuery().eq(RolePermissionPO::getPermissionId, (Object)permissionId));
    }

    @Override
    public List<Long> selectPermissionIdListByRoleId(Long roleId) {
        if (roleId == null) {
            return Collections.emptyList();
        }
        return this.selectPermissionIdListByRoleIdList(Collections.singletonList(roleId));
    }

    @Override
    public List<Long> selectPermissionIdListByRoleIdList(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return this.rolePermissionMapper.selectObjs((Wrapper)Wrappers.lambdaQuery().select(new SFunction[]{RolePermissionPO::getPermissionId}).in(RolePermissionPO::getRoleId, roleIds)).stream().map(DatabaseNumberUtils::toLong).collect(Collectors.toList());
    }

    @Generated
    public RolePermissionDaoImpl(RolePermissionMapper rolePermissionMapper) {
        this.rolePermissionMapper = rolePermissionMapper;
    }
}

