package io.yak.ops.business.modeling.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.modeling.api.ModelingDirectoryApi.CreateRequest;
import io.yak.ops.business.modeling.api.ModelingDirectoryApi.MoveRequest;
import io.yak.ops.business.modeling.api.ModelingDirectoryApi.RenameRequest;
import io.yak.ops.business.modeling.catalog.ModelDirectoryService;
import io.yak.ops.business.modeling.domain.ModelingDirectory;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
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

/** Warehouse modeling directory tree management. */
@Tag(name = "数据建模目录接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling/directories")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class ModelingDirectoryController {

  private final ModelDirectoryService service;

  @Operation(summary = "查询建模目录列表")
  @GetMapping
  public Result<List<ModelingDirectory>> list() {
    return Result.success(service.list());
  }

  @Operation(summary = "新建建模目录")
  @RequiresPermission(ModelingPermissionCode.CREATE)
  @PostMapping
  public Result<ModelingDirectory> create(@Valid @RequestBody CreateRequest request) {
    return Result.success(service.create(request.parentId(), request.name()));
  }

  @Operation(summary = "重命名建模目录")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  @PutMapping("/{id}/name")
  public Result<Boolean> rename(
      @PathVariable("id") Long id, @Valid @RequestBody RenameRequest request) {
    service.rename(id, request.name());
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "移动建模目录")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  @PutMapping("/{id}/parent")
  public Result<Boolean> move(
      @PathVariable("id") Long id, @Valid @RequestBody MoveRequest request) {
    service.move(id, request.parentId());
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "删除空建模目录")
  @RequiresPermission(ModelingPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return Result.success(Boolean.TRUE);
  }
}
