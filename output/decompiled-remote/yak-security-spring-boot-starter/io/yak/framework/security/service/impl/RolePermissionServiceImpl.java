/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.CollectionUtils
 */
package io.yak.framework.security.service.impl;

import io.yak.framework.security.common.entity.RolePermission;
import io.yak.framework.security.dao.RolePermissionDao;
import io.yak.framework.security.service.PermissionCache;
import io.yak.framework.security.service.RolePermissionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service(value="yakSecurityRolePermissionServiceImpl")
public class RolePermissionServiceImpl
implements RolePermissionService {
    private final RolePermissionDao rolePermissionDao;
    private final PermissionCache permissionCache;

    public RolePermissionServiceImpl(RolePermissionDao rolePermissionDao, PermissionCache permissionCache) {
        this.rolePermissionDao = rolePermissionDao;
        this.permissionCache = permissionCache;
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void saveRolePermission(Long roleId, List<Long> permissionIdList) {
        if (roleId == null) {
            return;
        }
        this.permissionCache.invalidateRole(roleId);
        List<Long> validPermissionIds = this.normalizeIds(permissionIdList);
        if (validPermissionIds.isEmpty()) {
            return;
        }
        List<RolePermission> rolePermissionList = this.buildRolePermissionList(roleId, validPermissionIds);
        this.rolePermissionDao.insertBatch(rolePermissionList);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void updateRolePermission(Long roleId, List<Long> permissionIdList) {
        if (roleId == null) {
            return;
        }
        this.permissionCache.invalidateRole(roleId);
        this.rolePermissionDao.deleteByRoleId(roleId);
        List<Long> validPermissionIds = this.normalizeIds(permissionIdList);
        if (validPermissionIds.isEmpty()) {
            return;
        }
        this.rolePermissionDao.insertBatch(this.buildRolePermissionList(roleId, validPermissionIds));
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void deleteRolePermissionByRoleId(Long roleId) {
        if (roleId == null) {
            return;
        }
        this.permissionCache.invalidateRole(roleId);
        this.rolePermissionDao.deleteByRoleId(roleId);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void deleteRolePermissionByPermissionId(Long permissionId) {
        if (permissionId == null) {
            return;
        }
        this.permissionCache.invalidateAll();
        this.rolePermissionDao.deleteByPermissionId(permissionId);
    }

    @Override
    public List<Long> getPermissionIdListByRoleId(Long roleId) {
        if (roleId == null) {
            return new ArrayList<Long>();
        }
        ArrayList permissionIdList = this.rolePermissionDao.selectPermissionIdListByRoleId(roleId);
        return permissionIdList == null ? new ArrayList() : permissionIdList;
    }

    @Override
    public List<Long> getPermissionIdListByRoleIdList(List<Long> roleIdList) {
        List<Long> validRoleIds = this.normalizeIds(roleIdList);
        if (validRoleIds.isEmpty()) {
            return new ArrayList<Long>();
        }
        List<Long> permissionIdList = this.rolePermissionDao.selectPermissionIdListByRoleIdList(validRoleIds);
        if (CollectionUtils.isEmpty(permissionIdList)) {
            return new ArrayList<Long>();
        }
        return permissionIdList.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    private List<RolePermission> buildRolePermissionList(Long roleId, List<Long> permissionIdList) {
        ArrayList<RolePermission> rolePermissionList = new ArrayList<RolePermission>(permissionIdList.size());
        for (Long permissionId : permissionIdList) {
            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setPermissionId(permissionId);
            rolePermissionList.add(rolePermission);
        }
        return rolePermissionList;
    }

    private List<Long> normalizeIds(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return new ArrayList<Long>();
        }
        return idList.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }
}

