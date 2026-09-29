package io.yak.ops.business.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.security.api.AccessDecision;
import io.yak.ops.business.security.application.AccessDecisionService;
import io.yak.ops.common.constant.security.SecurityPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 数据级访问裁决(票据 75/77),供查询/开发链路调用。 */
@Tag(name = "数据安全-访问裁决接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-security/access")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(SecurityPermissionCode.READ)
public class AccessDecisionController {

  private final AccessDecisionService service;

  @Operation(summary = "访问裁决(返回是否放行/是否脱敏)")
  @PostMapping("/decide")
  public Result<AccessDecision> decide(@Valid @RequestBody DecideRequest body) {
    return Result.success(
        service.decide(body.actor(), body.roles(), body.objectKey(), body.action()));
  }

  public record DecideRequest(@NotBlank String actor, List<String> roles,
      @NotBlank String objectKey, @NotBlank String action) {}
}
