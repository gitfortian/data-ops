/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.CollectionUtils
 */
package io.yak.framework.security.service.impl;

import io.yak.framework.security.common.entity.UserRole;
import io.yak.framework.security.dao.UserRoleDao;
import io.yak.framework.security.service.PermissionCache;
import io.yak.framework.security.service.UserRoleService;
import io.yak.framework.security.util.CopyBeanUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service(value="yakSecurityUserRoleServiceImpl")
public class UserRoleServiceImpl
implements UserRoleService {
    private final UserRoleDao userRoleDao;
    private final PermissionCache permissionCache;

    public UserRoleServiceImpl(UserRoleDao userRoleDao, PermissionCache permissionCache) {
        this.userRoleDao = userRoleDao;
        this.permissionCache = permissionCache;
    }

    @Override
    public List<Long> getUserIdListByRoleId(Long roleId) {
        if (roleId == null) {
            return new ArrayList<Long>();
        }
        ArrayList userIdList = this.userRoleDao.selectUserIdListByRoleId(roleId);
        return userIdList == null ? new ArrayList() : userIdList;
    }

    @Override
    public List<Long> getRoleIdListByUserId(Long userId) {
        if (userId == null) {
            return new ArrayList<Long>();
        }
        ArrayList roleIdList = this.userRoleDao.selectRoleIdListByUserId(userId);
        return roleIdList == null ? new ArrayList() : roleIdList;
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void updateUserRoleByUserId(Long userId, List<Long> roleIdList) {
        if (userId == null) {
            return;
        }
        this.userRoleDao.deleteByUserIdOrRoleId(userId, null);
        this.permissionCache.invalidateUser(userId);
        List<Long> validRoleIds = this.normalizeIds(roleIdList);
        if (validRoleIds.isEmpty()) {
            return;
        }
        this.userRoleDao.insertBatch(this.buildByUserId(userId, validRoleIds));
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void updateUserRoleByRoleId(Long roleId, List<Long> userIdList) {
        if (roleId == null) {
            return;
        }
        this.permissionCache.invalidateRole(roleId);
        this.userRoleDao.deleteByUserIdOrRoleId(null, roleId);
        List<Long> validUserIds = this.normalizeIds(userIdList);
        if (validUserIds.isEmpty()) {
            return;
        }
        this.userRoleDao.insertBatch(this.buildByRoleId(roleId, validUserIds));
    }

    @Override
    public int getUserRoleCountByRoleId(Long roleId) {
        if (roleId == null || Objects.equals(roleId, 0L)) {
            return 0;
        }
        return this.userRoleDao.selectCountByRoleId(roleId);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public int deleteByUserIdOrRoleId(Long userId, Long roleId) {
        if (userId == null && roleId == null) {
            return 0;
        }
        if (userId != null) {
            this.permissionCache.invalidateUser(userId);
        } else {
            this.permissionCache.invalidateRole(roleId);
        }
        return this.userRoleDao.deleteByUserIdOrRoleId(userId, roleId);
    }

    @Override
    public List<UserRole> getByRoleIds(List<Long> roleIdList) {
        List<Long> validRoleIds = this.normalizeIds(roleIdList);
        if (validRoleIds.isEmpty()) {
            return new ArrayList<UserRole>();
        }
        return CopyBeanUtil.copyList(this.userRoleDao.selectByRoleIds(validRoleIds), UserRole.class);
    }

    @Override
    public List<UserRole> getRoleIdListByUserIds(List<Long> userIdList) {
        List<Long> validUserIds = this.normalizeIds(userIdList);
        if (validUserIds.isEmpty()) {
            return new ArrayList<UserRole>();
        }
        return CopyBeanUtil.copyList(this.userRoleDao.getRoleIdListByUserIds(validUserIds), UserRole.class);
    }

    private List<UserRole> buildByUserId(Long userId, List<Long> roleIdList) {
        ArrayList<UserRole> userRoleList = new ArrayList<UserRole>(roleIdList.size());
        for (Long roleId : roleIdList) {
            userRoleList.add(new UserRole(userId, roleId));
        }
        return userRoleList;
    }

    private List<UserRole> buildByRoleId(Long roleId, List<Long> userIdList) {
        ArrayList<UserRole> userRoleList = new ArrayList<UserRole>(userIdList.size());
        for (Long userId : userIdList) {
            userRoleList.add(new UserRole(userId, roleId));
        }
        return userRoleList;
    }

    private List<Long> normalizeIds(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return new ArrayList<Long>();
        }
        return idList.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }
}

