package io.yak.ops.business.development.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.development.editor.DevelopmentEditorSettingsService;
import io.yak.ops.common.constant.development.DataDevelopmentPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** SQL editor preference APIs for the data-development workbench. */
@Tag(name = "数据开发编辑器设置接口")
@RestController
@RequestMapping("/api/v1/data-development/editor-settings")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(DataDevelopmentPermissionCode.READ)
public class DevelopmentEditorSettingsController {

  private final DevelopmentEditorSettingsService service;

  public DevelopmentEditorSettingsController(DevelopmentEditorSettingsService service) {
    this.service = service;
  }

  @Operation(summary = "读取当前用户编辑器设置")
  @GetMapping
  public Result<Map<String, Object>> get(Principal principal) {
    return Result.success(service.get(userKey(principal)));
  }

  @Operation(summary = "保存当前用户编辑器设置")
  @RequiresPermission(DataDevelopmentPermissionCode.EDIT)
  @PutMapping
  public Result<Map<String, Object>> save(
      Principal principal,
      @RequestBody Map<String, Object> settings) {
    return Result.success(service.save(userKey(principal), settings));
  }

  private String userKey(Principal principal) {
    return principal == null ? "default" : principal.getName();
  }
}
