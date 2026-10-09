package io.yak.framework.security.common.entity;

import io.yak.framework.security.common.dto.user.UserProjectDTO;
import io.yak.framework.security.common.entity.project.Project;
import io.yak.framework.security.common.entity.project.ProjectBrief;
import io.yak.framework.security.common.entity.role.Role;
import io.yak.framework.security.common.entity.role.RoleBrief;
import java.util.Date;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A8.2h original Java FQCN + constructor + bean inheritance survives jar move. */
class ProjectRoleModelRelocationTest {
  @Test
  void projectAndRoleEntityInheritanceAndLombokPropertiesRemain() throws Exception {
    assertEquals("io.yak.framework.security.common.entity.BaseEntity", BaseEntity.class.getName());
    assertSame(BaseEntity.class, Project.class.getSuperclass());
    assertSame(BaseEntity.class, Role.class.getSuperclass());
    Project project = new Project();
    project.setId(7L);
    project.setProjectName("production");
    project.setProjectCode("p777");
    project.setRunning(true);
    project.setDeptId(12L);
    project.setCreateTime(new Date(1700000000000L));
    assertEquals(Long.valueOf(7), project.getId());
    assertEquals("p777", project.getProjectCode());
    assertEquals(Long.valueOf(12), project.getDeptId());
    Role role = new Role();
    role.setId(8L);
    role.setRoleCode("auditor");
    role.setRoleName("Auditor");
    role.setLastReviser("operator");
    assertEquals("auditor", role.getRoleCode());
    assertEquals("operator", role.getLastReviser());
    assertSame(Project.class, Class.forName("io.yak.framework.security.common.entity.project.Project"));
    assertSame(Role.class, Class.forName("io.yak.framework.security.common.entity.role.Role"));
  }

  @Test
  void briefAndQueryDtoFqcnsKeepTheirPreviousBeanShape() {
    ProjectBrief project = new ProjectBrief();
    project.setId(42L);
    project.setProjectName("Ops");
    project.setProjectCode("p42");
    assertEquals("p42", project.getProjectCode());
    RoleBrief role = new RoleBrief();
    role.setId(17L);
    role.setRoleName("Editor");
    assertEquals("Editor", role.getRoleName());
    UserProjectDTO dto = new UserProjectDTO();
    dto.setId(10L);
    dto.setUserId(11L);
    dto.setUserType(1);
    dto.setProjectId(12L);
    dto.setIsDelete(false);
    assertEquals(Long.valueOf(11), dto.getUserId());
    assertEquals(Integer.valueOf(1), dto.getUserType());
    assertEquals(Boolean.FALSE, dto.getIsDelete());
  }
}
