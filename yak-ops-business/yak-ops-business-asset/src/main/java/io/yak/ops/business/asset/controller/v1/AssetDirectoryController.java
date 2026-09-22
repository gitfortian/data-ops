package io.yak.ops.business.asset.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.asset.catalog.DirectoryService;
import io.yak.ops.business.asset.catalog.DirectoryService.DirNode;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.DirectoryMoveDTO;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.DirectoryUpsertDTO;
import io.yak.ops.common.constant.asset.AssetPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 资产目录维护 REST API(ticket 92)。 */
@Tag(name = "数据资产-目录")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/assets/directories")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(AssetPermissionCode.READ)
public class AssetDirectoryController {

  private final DirectoryService directoryService;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "目录树")
  @GetMapping("/tree")
  public Result<List<DirNode>> tree() {
    return Result.success(directoryService.tree());
  }

  @Operation(summary = "新建目录(dirCode 空自动生成)")
  @RequiresPermission(AssetPermissionCode.CREATE)
  @PostMapping
  public Result<DirNode> create(
      @Valid @RequestBody DirectoryUpsertDTO dto, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(directoryService.create(dto.getParentId(), dto.getDirCode(),
        dto.getDirName(), dto.getIconKey(), dto.getDescription(), dto.getSortOrder(), operator));
  }

  @Operation(summary = "编辑目录(名称/图标/描述/排序)")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PutMapping("/{id}")
  public Result<DirNode> update(
      @PathVariable("id") Long id,
      @Valid @RequestBody DirectoryUpsertDTO dto,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(directoryService.update(id, dto.getDirName(), dto.getIconKey(),
        dto.getDescription(), dto.getSortOrder(), operator));
  }

  @Operation(summary = "移动目录(防环,级联改写子树路径)")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PostMapping("/{id}/move")
  public Result<Boolean> move(
      @PathVariable("id") Long id,
      @RequestBody DirectoryMoveDTO dto,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    directoryService.move(id, dto.getTargetParentId(), operator);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "删除目录(builtin/非空不可删)")
  @RequiresPermission(AssetPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    directoryService.delete(id, operator);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "模板初始化:按分层+业务域一键建目录(幂等保护)")
  @RequiresPermission(AssetPermissionCode.CREATE)
  @PostMapping("/init-template")
  public Result<Integer> initTemplate(HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(directoryService.initTemplate(operator));
  }
}
