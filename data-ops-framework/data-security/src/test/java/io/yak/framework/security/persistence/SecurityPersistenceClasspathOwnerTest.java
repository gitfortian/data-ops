package io.yak.framework.security.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * Same legacy binary names must be resolved from Platform Persistence only:
 * Starter must not produce a second PO, Mapper or type handler class.
 */
class SecurityPersistenceClasspathOwnerTest {
  @Test
  void allThirtySixMovedClassesHaveOneCompiledResourceAndOldNames() throws Exception {
    String[] originalFqcn = {
        "io.yak.framework.security.common.po.AppBasePO",
        "io.yak.framework.security.common.po.BasePO",
        "io.yak.framework.security.common.po.ConfigPO",
        "io.yak.framework.security.common.po.DeptPO",
        "io.yak.framework.security.common.po.MenuPO",
        "io.yak.framework.security.common.po.MessagePO",
        "io.yak.framework.security.common.po.OplogExtraPO",
        "io.yak.framework.security.common.po.OplogPO",
        "io.yak.framework.security.common.po.PermissionPO",
        "io.yak.framework.security.common.po.ProjectPO",
        "io.yak.framework.security.common.po.ResourceTypePO",
        "io.yak.framework.security.common.po.RoleMenuPO",
        "io.yak.framework.security.common.po.RolePO",
        "io.yak.framework.security.common.po.RolePermissionPO",
        "io.yak.framework.security.common.po.UserPO",
        "io.yak.framework.security.common.po.UserProjectPO",
        "io.yak.framework.security.common.po.UserResourcePO",
        "io.yak.framework.security.common.po.UserRolePO",
        "io.yak.framework.security.config.NumericBooleanTypeHandler",
        "io.yak.framework.security.config.YakSecurityMetaObjectHandler",
        "io.yak.framework.security.dao.mapper.ConfigMapper",
        "io.yak.framework.security.dao.mapper.DeptMapper",
        "io.yak.framework.security.dao.mapper.MenuMapper",
        "io.yak.framework.security.dao.mapper.MessageMapper",
        "io.yak.framework.security.dao.mapper.OplogExtraMapper",
        "io.yak.framework.security.dao.mapper.OplogMapper",
        "io.yak.framework.security.dao.mapper.PermissionMapper",
        "io.yak.framework.security.dao.mapper.ProjectMapper",
        "io.yak.framework.security.dao.mapper.ResourceTypeMapper",
        "io.yak.framework.security.dao.mapper.RoleMapper",
        "io.yak.framework.security.dao.mapper.RoleMenuMapper",
        "io.yak.framework.security.dao.mapper.RolePermissionMapper",
        "io.yak.framework.security.dao.mapper.UserMapper",
        "io.yak.framework.security.dao.mapper.UserProjectMapper",
        "io.yak.framework.security.dao.mapper.UserResourceMapper",
        "io.yak.framework.security.dao.mapper.UserRoleMapper"
    };
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    for (String fqcn : originalFqcn) {
      assertThat(Class.forName(fqcn, false, loader).getName()).isEqualTo(fqcn);
      java.util.Enumeration<URL> resources =
          loader.getResources(fqcn.replace('.', '/') + ".class");
      assertThat(Collections.list(resources))
          .as("Security classpath owner for " + fqcn)
          .hasSize(1);
    }
  }
}
