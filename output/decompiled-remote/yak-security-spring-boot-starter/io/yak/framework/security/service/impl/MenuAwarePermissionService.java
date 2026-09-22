/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.beans.factory.annotation.Qualifier
 *  org.springframework.context.annotation.Primary
 *  org.springframework.stereotype.Service
 */
package io.yak.framework.security.service.impl;

import io.yak.framework.security.common.dto.permission.PermissionDTO;
import io.yak.framework.security.common.vo.permission.PermissionTreeVO;
import io.yak.framework.security.service.PermissionService;
import io.yak.framework.security.service.RolePermissionService;
import io.yak.framework.security.service.impl.MenuAuthorizationService;
import io.yak.framework.security.service.impl.MenuSelectionCodec;
import io.yak.framework.security.service.impl.PermissionMenuRelationService;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service(value="yakSecurityMenuAwarePermissionService")
public class MenuAwarePermissionService
implements PermissionService {
    private final PermissionService delegate;
    private final RolePermissionService rolePermissionService;
    private final MenuAuthorizationService menuAuthorizationService;
    private final PermissionMenuRelationService permissionMenuRelationService;

    public MenuAwarePermissionService(@Qualifier(value="yakSecurityPermissionServiceImpl") PermissionService delegate, RolePermissionService rolePermissionService, MenuAuthorizationService menuAuthorizationService, PermissionMenuRelationService permissionMenuRelationService) {
        this.delegate = delegate;
        this.rolePermissionService = rolePermissionService;
        this.menuAuthorizationService = menuAuthorizationService;
        this.permissionMenuRelationService = permissionMenuRelationService;
    }

    @Override
    public PermissionTreeVO buildPermissionTreeWithHas(List<Long> permissionIdList) {
        List<Long> normalPermissionIds = MenuSelectionCodec.extractPermissionIds(permissionIdList);
        Set<Long> menuManagedPermissionIds = this.menuAuthorizationService.getMenuBoundPermissionIds();
        normalPermissionIds.removeIf(menuManagedPermissionIds::contains);
        PermissionTreeVO root = this.delegate.buildPermissionTreeWithHas(normalPermissionIds);
        return this.mergeMenuTree(root, MenuSelectionCodec.extractMenuIds(permissionIdList), normalPermissionIds);
    }

    @Override
    public PermissionTreeVO buildPermissionTree() {
        return this.mergeMenuTree(this.delegate.buildPermissionTree(), Collections.emptyList(), Collections.emptyList());
    }

    @Override
    public PermissionTreeVO buildPermissionTreeByRoleId(Long roleId) {
        List<Long> permissionIds = roleId == null ? Collections.emptyList() : this.rolePermissionService.getPermissionIdListByRoleId(roleId);
        List<Long> normalPermissionIds = MenuSelectionCodec.extractPermissionIds(permissionIds);
        Set<Long> menuManagedPermissionIds = this.menuAuthorizationService.getMenuBoundPermissionIds();
        normalPermissionIds.removeIf(menuManagedPermissionIds::contains);
        return this.mergeMenuTree(this.delegate.buildPermissionTreeWithHas(normalPermissionIds), this.menuAuthorizationService.getMenuIdsByRoleId(roleId), normalPermissionIds);
    }

    @Override
    public void savePermission(List<PermissionDTO> permissionDTOList) {
        this.delegate.savePermission(permissionDTOList);
    }

    @Override
    public void deletePermissionById(Long permissionId) {
        this.delegate.deletePermissionById(permissionId);
    }

    private PermissionTreeVO mergeMenuTree(PermissionTreeVO root, Collection<Long> selectedMenuIds, Collection<Long> selectedPermissionIds) {
        Set<String> menuBoundPermissionCodes = this.menuAuthorizationService.getMenuBoundPermissionCodes();
        this.filterMenuBoundPermissionNodes(root, menuBoundPermissionCodes, true);
        PermissionTreeVO menuTree = this.menuAuthorizationService.buildMenuTree(selectedMenuIds);
        return this.permissionMenuRelationService.mergeCapabilityTree(root, menuTree, selectedPermissionIds);
    }

    private boolean filterMenuBoundPermissionNodes(PermissionTreeVO node, Set<String> menuBoundPermissionCodes, boolean root) {
        if (node == null) {
            return false;
        }
        if (menuBoundPermissionCodes.contains(node.getPermissionCode())) {
            return false;
        }
        List<PermissionTreeVO> children = node.getChildList();
        if (children != null) {
            children.removeIf(child -> !this.filterMenuBoundPermissionNodes((PermissionTreeVO)child, menuBoundPermissionCodes, false));
        }
        return root || Boolean.TRUE.equals(node.getLeaf()) || node.getChildList() != null && !node.getChildList().isEmpty();
    }
}

