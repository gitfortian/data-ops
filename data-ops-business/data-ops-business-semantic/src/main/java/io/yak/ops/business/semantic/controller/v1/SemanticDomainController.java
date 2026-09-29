package io.yak.ops.business.semantic.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.semantic.api.SemanticDomainApi;
import io.yak.ops.business.semantic.domain.BusinessDomainService;
import io.yak.ops.common.constant.semantic.SemanticPermissionCode;
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

/** Business-semantic domain tree (ticket 33). */
@Tag(name = "业务语义业务域接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/semantic/domains")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(SemanticPermissionCode.READ)
public class SemanticDomainController {

  private final BusinessDomainService service;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "全量业务域树")
  @GetMapping("/tree")
  public Result<List<BusinessDomainService.DomainNode>> tree() {
    return Result.success(service.tree());
  }

  @Operation(summary = "创建业务域")
  @RequiresPermission(SemanticPermissionCode.CREATE)
  @PostMapping
  public Result<Boolean> create(
      @Valid @RequestBody SemanticDomainApi.CreateRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    service.create(
        request.parentId(),
        request.code(),
        request.name(),
        request.owner(),
        request.description(),
        request.sortOrder(),
        operator);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "编辑业务域（编码不可改）")
  @RequiresPermission(SemanticPermissionCode.UPDATE)
  @PutMapping("/{id}")
  public Result<Boolean> update(
      @PathVariable("id") Long id,
      @Valid @RequestBody SemanticDomainApi.UpdateRequest request) {
    service.update(id, request.name(), request.owner(), request.description(), request.sortOrder());
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "拖拽改父/排序（禁止移到自己的子孙之下）")
  @RequiresPermission(SemanticPermissionCode.UPDATE)
  @PostMapping("/{id}/move")
  public Result<Boolean> move(
      @PathVariable("id") Long id, @RequestBody SemanticDomainApi.MoveRequest request) {
    service.move(id, request.parentId(), request.sortOrder());
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "删除业务域（存在子域时阻断）")
  @RequiresPermission(SemanticPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return Result.success(Boolean.TRUE);
  }
}
