package io.yak.ops.business.semantic.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.semantic.preset.SemanticPresetService;
import io.yak.ops.common.constant.semantic.SemanticPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Preset-standard initialization (ticket 31): status flag and idempotent copy. */
@Tag(name = "业务语义预置标准接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/semantic/standards/preset")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(SemanticPermissionCode.READ)
public class SemanticPresetController {

  private final SemanticPresetService presetService;
  private final CurrentUserProvider currentUserProvider;

  public record PresetStatusView(boolean initialized) {}

  @Operation(summary = "预置初始化状态（项目内是否已有标准）")
  @GetMapping("/status")
  public Result<PresetStatusView> status() {
    return Result.success(new PresetStatusView(presetService.initialized()));
  }

  @Operation(summary = "初始化预置标准（幂等：已有编码跳过）")
  @RequiresPermission(SemanticPermissionCode.CREATE)
  @PostMapping("/initialize")
  public Result<Integer> initialize(HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(presetService.initialize(operator));
  }
}
