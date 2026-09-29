package io.yak.ops.business.development.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.development.api.DevelopmentDirectoryApi.CreateRequest;
import io.yak.ops.business.development.api.DevelopmentDirectoryApi.MoveRequest;
import io.yak.ops.business.development.api.DevelopmentDirectoryApi.RenameRequest;
import io.yak.ops.business.development.directory.DevelopmentDirectoryService;
import io.yak.ops.business.development.domain.DevelopmentDirectory;
import io.yak.ops.common.constant.development.DataDevelopmentPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Hierarchical directory management for data development. */
@Tag(name = "数据开发目录接口")
@RestController
@RequestMapping("/api/v1/data-development/directories")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(DataDevelopmentPermissionCode.READ)
public class DevelopmentDirectoryController {

  private final DevelopmentDirectoryService service;

  public DevelopmentDirectoryController(DevelopmentDirectoryService service) {
    this.service = service;
  }

  @Operation(summary = "查询数据开发目录")
  @GetMapping
  public Result<List<DevelopmentDirectory>> list() {
    return Result.success(service.list());
  }

  @Operation(summary = "新建数据开发目录")
  @RequiresPermission(DataDevelopmentPermissionCode.EDIT)
  @PostMapping
  public Result<DevelopmentDirectory> create(@Valid @RequestBody CreateRequest request) {
    return Result.success(service.create(request.parentId(), request.name()));
  }

  @Operation(summary = "重命名数据开发目录")
  @RequiresPermission(DataDevelopmentPermissionCode.EDIT)
  @PutMapping("/{id}/name")
  public Result<DevelopmentDirectory> rename(
      @PathVariable("id") Long id,
      @Valid @RequestBody RenameRequest request) {
    return Result.success(service.rename(id, request.name()));
  }

  @Operation(summary = "移动数据开发目录")
  @RequiresPermission(DataDevelopmentPermissionCode.EDIT)
  @PutMapping("/{id}/parent")
  public Result<DevelopmentDirectory> move(
      @PathVariable("id") Long id,
      @Valid @RequestBody MoveRequest request) {
    return Result.success(service.move(id, request.parentId()));
  }

  @Operation(summary = "删除空数据开发目录")
  @RequiresPermission(DataDevelopmentPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return Result.success(Boolean.TRUE);
  }
}
