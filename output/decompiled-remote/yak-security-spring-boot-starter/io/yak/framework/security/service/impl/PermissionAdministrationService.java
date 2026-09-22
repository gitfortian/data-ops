/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.conditions.Wrapper
 *  com.baomidou.mybatisplus.core.toolkit.Wrappers
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 */
package io.yak.framework.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.framework.security.common.po.PermissionPO;
import io.yak.framework.security.dao.mapper.PermissionMapper;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.service.PermissionCache;
import io.yak.framework.security.service.RolePermissionService;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionAdministrationService {
    private static final Long ROOT_PERMISSION_ID = 0L;
    private final PermissionMapper permissionMapper;
    private final RolePermissionService rolePermissionService;
    private final PermissionCache permissionCache;

    public PermissionAdministrationService(PermissionMapper permissionMapper, RolePermissionService rolePermissionService, PermissionCache permissionCache) {
        this.permissionMapper = permissionMapper;
        this.rolePermissionService = rolePermissionService;
        this.permissionCache = permissionCache;
    }

    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void deletePermission(Long permissionId) {
        if (permissionId == null || Objects.equals(ROOT_PERMISSION_ID, permissionId)) {
            throw new YakSecurityException("\u6743\u9650 ID \u4e0d\u6b63\u786e");
        }
        PermissionPO permission = (PermissionPO)this.permissionMapper.selectById(permissionId);
        if (permission == null) {
            throw new YakSecurityException("\u6743\u9650\u4e0d\u5b58\u5728");
        }
        if (Boolean.TRUE.equals(permission.getDeclared())) {
            throw new YakSecurityException("\u58f0\u660e\u5f0f\u6743\u9650\u7531\u540e\u7aef\u6ce8\u518c\u7ef4\u62a4\uff0c\u4e0d\u80fd\u624b\u5de5\u5220\u9664");
        }
        Long childCount = this.permissionMapper.selectCount((Wrapper)Wrappers.lambdaQuery().eq(PermissionPO::getParentId, (Object)permissionId));
        if (childCount != null && childCount > 0L) {
            throw new YakSecurityException("\u8be5\u6743\u9650\u5305\u542b\u5b50\u6743\u9650\uff0c\u4e0d\u80fd\u5220\u9664");
        }
        this.rolePermissionService.deleteRolePermissionByPermissionId(permissionId);
        if (this.permissionMapper.deleteById(permissionId) != 1) {
            throw new YakSecurityException("\u6743\u9650\u5220\u9664\u5931\u8d25");
        }
        this.permissionCache.invalidateAll();
    }
}

