package io.yak.ops.business.modeling.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.modeling.api.ModelingTagApi.CreateRequest;
import io.yak.ops.business.modeling.catalog.ModelTagService;
import io.yak.ops.business.modeling.domain.ModelingTag;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Warehouse modeling tag management. */
@Tag(name = "数据建模标签接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling/tags")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class ModelingTagController {

  private final ModelTagService service;

  @Operation(summary = "查询建模标签列表")
  @GetMapping
  public Result<List<ModelingTag>> list() {
    return Result.success(service.list());
  }

  @Operation(summary = "新建建模标签")
  @RequiresPermission(ModelingPermissionCode.CREATE)
  @PostMapping
  public Result<ModelingTag> create(@Valid @RequestBody CreateRequest request) {
    return Result.success(service.create(request.name()));
  }

  @Operation(summary = "删除建模标签")
  @RequiresPermission(ModelingPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return Result.success(Boolean.TRUE);
  }
}
