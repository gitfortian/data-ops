package io.yak.framework.security.permission;

import io.yak.framework.security.common.entity.Permission;
import io.yak.framework.security.dao.PermissionDao;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/** Transactional adapter for the Platform-owned declaration plan and existing PermissionDao. */
public class PermissionRegistrationService {
  private final PermissionDao permissionDao;

  public PermissionRegistrationService(PermissionDao permissionDao) {
    this.permissionDao = permissionDao;
  }

  @Transactional(transactionManager = "yakSecurityTransactionManager")
  public void synchronize(Collection<PermissionDefinition> definitions) {
    List<Permission> desired = new ArrayList<>();
    for (PermissionDeclarationPlan.Entry entry : PermissionDeclarationPlan.from(definitions).entries()) {
      Permission permission = new Permission();
      permission.setPermissionCode(entry.code());
      permission.setPermissionName(entry.name());
      permission.setDescription(entry.description());
      permission.setMenuCode(entry.menuCode());
      permission.setParentCode(entry.parentCode());
      permission.setLeaf(entry.leaf());
      permission.setLevel(entry.level());
      permission.setActive(true);
      permission.setDeclared(true);
      desired.add(permission);
    }
    permissionDao.synchronizeDeclared(desired);
  }
}
