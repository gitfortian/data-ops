/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.beans.factory.annotation.Qualifier
 *  org.springframework.context.annotation.Primary
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 */
package io.yak.framework.security.service.impl;

import io.yak.framework.security.service.RolePermissionService;
import io.yak.framework.security.service.impl.MenuAuthorizationService;
import io.yak.framework.security.service.impl.MenuSelectionCodec;
import io.yak.framework.security.service.impl.PermissionMenuRelationService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Primary
@Service(value="yakSecurityMenuAwareRolePermissionService")
public class MenuAwareRolePermissionService
implements RolePermissionService {
    private final RolePermissionService delegate;
    private final MenuAuthorizationService menuAuthorizationService;
    private final PermissionMenuRelationService permissionMenuRelationService;

    public MenuAwareRolePermissionService(@Qualifier(value="yakSecurityRolePermissionServiceImpl") RolePermissionService delegate, MenuAuthorizationService menuAuthorizationService, PermissionMenuRelationService permissionMenuRelationService) {
        this.delegate = delegate;
        this.menuAuthorizationService = menuAuthorizationService;
        this.permissionMenuRelationService = permissionMenuRelationService;
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void saveRolePermission(Long roleId, List<Long> permissionIdList) {
        List<Long> permissions = this.normalPermissions(permissionIdList);
        this.delegate.saveRolePermission(roleId, permissions);
        this.menuAuthorizationService.saveRoleMenus(roleId, this.effectiveMenuIds(permissionIdList, permissions));
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void updateRolePermission(Long roleId, List<Long> permissionIdList) {
        List<Long> permissions = this.normalPermissions(permissionIdList);
        this.delegate.updateRolePermission(roleId, permissions);
        this.menuAuthorizationService.updateRoleMenus(roleId, this.effectiveMenuIds(permissionIdList, permissions));
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void deleteRolePermissionByRoleId(Long roleId) {
        this.delegate.deleteRolePermissionByRoleId(roleId);
        this.menuAuthorizationService.deleteRoleMenus(roleId);
    }

    @Override
    public void deleteRolePermissionByPermissionId(Long permissionId) {
        this.delegate.deleteRolePermissionByPermissionId(permissionId);
    }

    @Override
    public List<Long> getPermissionIdListByRoleId(Long roleId) {
        ArrayList<Long> result = new ArrayList<Long>(this.delegate.getPermissionIdListByRoleId(roleId));
        this.reconcileMenuManagedPermissions(result, this.menuAuthorizationService.getRequiredPermissionIdsByRoleId(roleId));
        return result;
    }

    @Override
    public List<Long> getPermissionIdListByRoleIdList(List<Long> roleIdList) {
        ArrayList<Long> result = new ArrayList<Long>(this.delegate.getPermissionIdListByRoleIdList(roleIdList));
        this.reconcileMenuManagedPermissions(result, this.menuAuthorizationService.getRequiredPermissionIdsByRoleIds(roleIdList));
        return result;
    }

    private List<Long> effectiveMenuIds(Collection<Long> submittedIds, Collection<Long> normalPermissionIds) {
        LinkedHashSet<Long> result = new LinkedHashSet<Long>(MenuSelectionCodec.extractMenuIds(submittedIds));
        result.addAll(this.permissionMenuRelationService.inferMenuIds(normalPermissionIds));
        return new ArrayList<Long>(result);
    }

    private List<Long> normalPermissions(Collection<Long> submittedIds) {
        List<Long> result = MenuSelectionCodec.extractPermissionIds(submittedIds);
        Set<Long> menuManagedPermissionIds = this.menuAuthorizationService.getMenuBoundPermissionIds();
        result.removeIf(menuManagedPermissionIds::contains);
        return result;
    }

    private void reconcileMenuManagedPermissions(List<Long> permissionIds, Collection<Long> impliedPermissionIds) {
        Set<Long> menuManagedPermissionIds = this.menuAuthorizationService.getMenuBoundPermissionIds();
        permissionIds.removeIf(menuManagedPermissionIds::contains);
        LinkedHashSet<Long> unique = new LinkedHashSet<Long>(permissionIds);
        if (impliedPermissionIds != null) {
            unique.addAll(impliedPermissionIds);
        }
        permissionIds.clear();
        permissionIds.addAll(unique);
    }
}

