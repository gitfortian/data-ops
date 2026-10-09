package io.yak.framework.security.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.framework.security.common.dto.PageParamDTO;
import io.yak.framework.security.common.dto.account.AccountLoginDTO;
import io.yak.framework.security.common.enums.resource.ControlLevelCode;
import io.yak.framework.security.common.vo.permission.PermissionTreeVO;
import io.yak.framework.security.common.vo.role.RoleVO;
import io.yak.framework.security.common.vo.user.CurrentUserVO;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

class SecurityApiJsonCompatibilityTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void loginJsonKeepsOriginalFieldNamesAndBeanValidation() throws Exception {
    AccountLoginDTO dto = new AccountLoginDTO();
    dto.setUserName("alice");
    dto.setPw("password");
    JsonNode payload = mapper.readTree(mapper.writeValueAsString(dto));
    assertThat(payload.get("userName").asText()).isEqualTo("alice");
    assertThat(payload.get("pw").asText()).isEqualTo("password");
    assertThat(payload.has("password")).isFalse();
    assertThat(AccountLoginDTO.class.getDeclaredField("pw")
        .isAnnotationPresent(NotBlank.class)).isTrue();
    assertThat(AccountLoginDTO.class.getDeclaredField("userName")
        .isAnnotationPresent(NotBlank.class)).isTrue();
  }

  @Test
  void paginationDefaultsAndEnumsHaveUnchangedValues() {
    PageParamDTO dto = new PageParamDTO();
    assertThat(dto.getPage()).isEqualTo(1);
    assertThat(dto.getSize()).isEqualTo(10);
    assertThat(ControlLevelCode.getByType(2)).isEqualTo(ControlLevelCode.ADMIN);
    assertThat(ControlLevelCode.getByType(-7)).isNull();
  }

  @Test
  void rolePermissionTreePreservesNullExclusionAndNestedJson() throws Exception {
    RoleVO role = new RoleVO();
    role.setRoleName("auditor");
    JsonNode withoutTree = mapper.readTree(mapper.writeValueAsString(role));
    assertThat(withoutTree.has("permissionTreeVO")).isFalse();

    PermissionTreeVO tree = new PermissionTreeVO();
    tree.setPermissionCode("security:user:read");
    tree.setMenuCode("system-users");
    role.setPermissionTreeVO(tree);
    JsonNode withTree = mapper.readTree(mapper.writeValueAsString(role));
    assertThat(withTree.path("permissionTreeVO").path("permissionCode").asText())
        .isEqualTo("security:user:read");
    assertThat(withTree.path("permissionTreeVO").path("menuCode").asText())
        .isEqualTo("system-users");
  }

  @Test
  void currentUserJsonRetainsProjectPermissionAndNullableMenuContract() throws Exception {
    CurrentUserVO current = new CurrentUserVO();
    current.setId(42L);
    current.setUserName("alice");
    JsonNode payload = mapper.readTree(mapper.writeValueAsString(current));
    assertThat(payload.path("id").asLong()).isEqualTo(42L);
    assertThat(payload.path("roleList").isArray()).isTrue();
    assertThat(payload.path("projectList").isArray()).isTrue();
    assertThat(payload.path("permissionCodes").isArray()).isTrue();
    assertThat(payload.has("menuCodes")).isTrue();
    assertThat(payload.path("menuCodes").isNull()).isTrue();
  }

  @Test
  void legacyFqcnAndPublicAccessorSignaturesStayStable() throws Exception {
    assertThat(AccountLoginDTO.class.getName())
        .isEqualTo("io.yak.framework.security.common.dto.account.AccountLoginDTO");
    assertThat(AccountLoginDTO.class.getMethod("getPw").getReturnType())
        .isEqualTo(String.class);
    assertThat(RoleVO.class.getMethod("getPermissionTreeVO").getReturnType())
        .isEqualTo(PermissionTreeVO.class);
    assertThat(CurrentUserVO.class.getMethod("getProjectList").getReturnType())
        .isEqualTo(java.util.List.class);
  }
}
