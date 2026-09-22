/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.transaction.annotation.Transactional
 */
package io.yak.framework.security.permission;

import io.yak.framework.security.common.entity.Permission;
import io.yak.framework.security.dao.PermissionDao;
import io.yak.framework.security.permission.PermissionDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

public class PermissionRegistrationService {
    private final PermissionDao permissionDao;

    public PermissionRegistrationService(PermissionDao permissionDao) {
        this.permissionDao = permissionDao;
    }

    @Transactional(transactionManager="yakSecurityTransactionManager")
    public void synchronize(Collection<PermissionDefinition> definitions) {
        LinkedHashMap<String, Permission> desired = new LinkedHashMap<String, Permission>();
        for (PermissionDefinition group : definitions) {
            PermissionRegistrationService.putUnique(desired, PermissionRegistrationService.permission(group.getCode(), group.getName(), null, null, false, 1));
            for (PermissionDefinition.Item item : group.getPermissions()) {
                Permission permission = PermissionRegistrationService.permission(item.getCode(), item.getName(), item.getDescription(), item.getMenuCode(), true, 2);
                permission.setParentCode(group.getCode());
                PermissionRegistrationService.putUnique(desired, permission);
            }
        }
        this.permissionDao.synchronizeDeclared(new ArrayList<Permission>(desired.values()));
    }

    private static Permission permission(String code, String name, String description, String menuCode, boolean leaf, int level) {
        Permission permission = new Permission();
        permission.setPermissionCode(code);
        permission.setPermissionName(name);
        permission.setDescription(description);
        permission.setMenuCode(menuCode);
        permission.setLeaf(leaf);
        permission.setLevel(level);
        permission.setActive(true);
        permission.setDeclared(true);
        return permission;
    }

    private static void putUnique(Map<String, Permission> permissions, Permission permission) {
        Permission previous = permissions.putIfAbsent(permission.getPermissionCode(), permission);
        if (!(previous == null || previous.getPermissionName().equals(permission.getPermissionName()) && previous.getLeaf() == permission.getLeaf())) {
            throw new IllegalStateException("Conflicting permission declaration: " + permission.getPermissionCode());
        }
    }
}

