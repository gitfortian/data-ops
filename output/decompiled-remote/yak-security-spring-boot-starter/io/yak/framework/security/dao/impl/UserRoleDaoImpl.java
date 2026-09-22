/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.conditions.Wrapper
 *  com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
 *  com.baomidou.mybatisplus.core.toolkit.Wrappers
 *  com.baomidou.mybatisplus.core.toolkit.support.SFunction
 *  lombok.Generated
 *  org.springframework.stereotype.Repository
 */
package io.yak.framework.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import io.yak.framework.security.common.entity.UserRole;
import io.yak.framework.security.common.po.UserRolePO;
import io.yak.framework.security.dao.UserRoleDao;
import io.yak.framework.security.dao.mapper.UserRoleMapper;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.DatabaseNumberUtils;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Generated;
import org.springframework.stereotype.Repository;

@Repository
public class UserRoleDaoImpl
implements UserRoleDao {
    private final UserRoleMapper userRoleMapper;

    private static boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }

    @Override
    public List<Long> selectUserIdListByRoleId(Long roleId) {
        if (roleId == null) {
            return Collections.emptyList();
        }
        return this.selectIdList(UserRolePO::getUserId, UserRolePO::getRoleId, roleId);
    }

    @Override
    public List<Long> selectRoleIdListByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return this.selectIdList(UserRolePO::getRoleId, UserRolePO::getUserId, userId);
    }

    @Override
    public void insertBatch(List<UserRole> userRoleList) {
        if (UserRoleDaoImpl.isEmpty(userRoleList)) {
            return;
        }
        CopyBeanUtil.copyList(userRoleList.stream().filter(Objects::nonNull).collect(Collectors.toList()), UserRolePO.class).forEach(arg_0 -> ((UserRoleMapper)this.userRoleMapper).insert(arg_0));
    }

    @Override
    public int deleteByUserIdOrRoleId(Long userId, Long roleId) {
        if (userId == null && roleId == null) {
            return 0;
        }
        return this.userRoleMapper.delete((Wrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(userId != null, UserRolePO::getUserId, (Object)userId)).eq(roleId != null, UserRolePO::getRoleId, (Object)roleId));
    }

    @Override
    public int selectCountByRoleId(Long roleId) {
        if (roleId == null) {
            return 0;
        }
        Long count = this.userRoleMapper.selectCount((Wrapper)Wrappers.lambdaQuery().eq(UserRolePO::getRoleId, (Object)roleId));
        return Math.toIntExact(count);
    }

    @Override
    public List<UserRolePO> selectByRoleIds(List<Long> roleIds) {
        if (UserRoleDaoImpl.isEmpty(roleIds)) {
            return Collections.emptyList();
        }
        return this.userRoleMapper.selectList((Wrapper)Wrappers.lambdaQuery().in(UserRolePO::getRoleId, roleIds));
    }

    @Override
    public List<UserRolePO> getRoleIdListByUserIds(List<Long> userIds) {
        if (UserRoleDaoImpl.isEmpty(userIds)) {
            return Collections.emptyList();
        }
        return this.userRoleMapper.selectList((Wrapper)Wrappers.lambdaQuery().in(UserRolePO::getUserId, userIds));
    }

    private List<Long> selectIdList(SFunction<UserRolePO, ?> selectedColumn, SFunction<UserRolePO, ?> conditionColumn, Long conditionValue) {
        return this.userRoleMapper.selectObjs((Wrapper)Wrappers.lambdaQuery().select(new SFunction[]{selectedColumn}).eq(conditionColumn, (Object)conditionValue)).stream().map(DatabaseNumberUtils::toLong).distinct().collect(Collectors.toList());
    }

    @Generated
    public UserRoleDaoImpl(UserRoleMapper userRoleMapper) {
        this.userRoleMapper = userRoleMapper;
    }
}

