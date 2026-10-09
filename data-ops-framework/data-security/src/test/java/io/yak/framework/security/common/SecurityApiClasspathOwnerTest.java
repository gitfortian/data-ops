package io.yak.framework.security.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;

/** Old Security Starter consumers must see one compiled class per legacy API FQCN. */
class SecurityApiClasspathOwnerTest {
  @Test
  void allSeventyFiveCompatibilityTypesHaveOneCompiledOwner() throws Exception {
    String[] classes = {
      "io.yak.framework.security.common.constant.Constants",
      "io.yak.framework.security.common.constant.FieldConstant",
      "io.yak.framework.security.common.constant.OplogConstant",
      "io.yak.framework.security.common.dto.PageParamDTO",
      "io.yak.framework.security.common.dto.account.AccountLoginDTO",
      "io.yak.framework.security.common.dto.config.ConfigDTO",
      "io.yak.framework.security.common.dto.config.ConfigQueryDTO",
      "io.yak.framework.security.common.dto.dept.DeptDTO",
      "io.yak.framework.security.common.dto.dept.DeptSaveDTO",
      "io.yak.framework.security.common.dto.message.MessageBatchReadDTO",
      "io.yak.framework.security.common.dto.message.MessageDTO",
      "io.yak.framework.security.common.dto.message.MessagePageQueryDTO",
      "io.yak.framework.security.common.dto.message.MessageReadDTO",
      "io.yak.framework.security.common.dto.oplog.OplogDTO",
      "io.yak.framework.security.common.dto.oplog.OplogQueryDTO",
      "io.yak.framework.security.common.dto.permission.PermissionDTO",
      "io.yak.framework.security.common.dto.project.ProjectBriefQueryDTO",
      "io.yak.framework.security.common.dto.project.ProjectQueryDTO",
      "io.yak.framework.security.common.dto.project.ProjectSaveDTO",
      "io.yak.framework.security.common.dto.project.ProjectStatusDTO",
      "io.yak.framework.security.common.dto.project.ProjectUserAssignDTO",
      "io.yak.framework.security.common.dto.resource.AssignToManyUserDTO",
      "io.yak.framework.security.common.dto.resource.AssignToOneUserDTO",
      "io.yak.framework.security.common.dto.resource.BatchAssignDTO",
      "io.yak.framework.security.common.dto.resource.ControlLevelQueryDTO",
      "io.yak.framework.security.common.dto.resource.MByRDataQueryDTO",
      "io.yak.framework.security.common.dto.resource.MByRQueryDTO",
      "io.yak.framework.security.common.dto.resource.MByUDataQueryDTO",
      "io.yak.framework.security.common.dto.resource.MByUQueryDTO",
      "io.yak.framework.security.common.dto.resource.ResourceDTO",
      "io.yak.framework.security.common.dto.resource.ResourceViewControlDTO",
      "io.yak.framework.security.common.dto.resource.UserResourceQueryDTO",
      "io.yak.framework.security.common.dto.resource.type.ResourceTypeQueryDTO",
      "io.yak.framework.security.common.dto.role.RoleAssignDTO",
      "io.yak.framework.security.common.dto.role.RoleQueryDTO",
      "io.yak.framework.security.common.dto.role.RoleSaveDTO",
      "io.yak.framework.security.common.dto.user.UserBriefQueryDTO",
      "io.yak.framework.security.common.dto.user.UserDTO",
      "io.yak.framework.security.common.dto.user.UserPasswordResetDTO",
      "io.yak.framework.security.common.dto.user.UserQueryDTO",
      "io.yak.framework.security.common.enums.ConfigStatusEnum",
      "io.yak.framework.security.common.enums.message.MessageCode",
      "io.yak.framework.security.common.enums.oplog.OplogCode",
      "io.yak.framework.security.common.enums.project.ProjectUserCode",
      "io.yak.framework.security.common.enums.resource.ControlLevelCode",
      "io.yak.framework.security.common.enums.resource.HasLevelCode",
      "io.yak.framework.security.common.enums.resource.ShowLevelCode",
      "io.yak.framework.security.common.enums.user.UserCheckType",
      "io.yak.framework.security.common.vo.config.ConfigVO",
      "io.yak.framework.security.common.vo.dept.DeptBriefVO",
      "io.yak.framework.security.common.vo.dept.DeptDeleteCheckVO",
      "io.yak.framework.security.common.vo.dept.DeptTreeVO",
      "io.yak.framework.security.common.vo.dept.DeptVO",
      "io.yak.framework.security.common.vo.message.MessagePageVO",
      "io.yak.framework.security.common.vo.message.MessageVO",
      "io.yak.framework.security.common.vo.oplog.OplogOptionsVO",
      "io.yak.framework.security.common.vo.oplog.OplogVO",
      "io.yak.framework.security.common.vo.permission.PermissionTreeVO",
      "io.yak.framework.security.common.vo.project.ProjectBriefVO",
      "io.yak.framework.security.common.vo.project.ProjectBriefVOWithUser",
      "io.yak.framework.security.common.vo.project.ProjectDeleteCheckVO",
      "io.yak.framework.security.common.vo.project.ProjectVO",
      "io.yak.framework.security.common.vo.resource.MByRDataVO",
      "io.yak.framework.security.common.vo.resource.MByRVO",
      "io.yak.framework.security.common.vo.resource.MByUDataVO",
      "io.yak.framework.security.common.vo.resource.MByUVO",
      "io.yak.framework.security.common.vo.resource.ResourceTypeVO",
      "io.yak.framework.security.common.vo.role.AssignInfoVO",
      "io.yak.framework.security.common.vo.role.RoleBriefVO",
      "io.yak.framework.security.common.vo.role.RoleDeleteCheckVO",
      "io.yak.framework.security.common.vo.role.RoleVO",
      "io.yak.framework.security.common.vo.user.CurrentUserVO",
      "io.yak.framework.security.common.vo.user.UserBasicVO",
      "io.yak.framework.security.common.vo.user.UserBriefVO",
      "io.yak.framework.security.common.vo.user.UserVO"
};
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    for (String fqcn : classes) {
      Class<?> type = Class.forName(fqcn, false, loader);
      assertThat(type.getName()).isEqualTo(fqcn);
      Enumeration<URL> resources = loader.getResources(fqcn.replace('.', '/') + ".class");
      assertThat(Collections.list(resources))
          .as("Unique classpath owner for old binary name " + fqcn)
          .hasSize(1);
    }
  }
}
