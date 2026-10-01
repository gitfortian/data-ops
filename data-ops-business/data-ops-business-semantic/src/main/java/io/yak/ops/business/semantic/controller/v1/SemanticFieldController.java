package io.yak.ops.business.semantic.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.semantic.api.SemanticFieldApi;
import io.yak.ops.business.semantic.controller.v1.converter.StandardFieldViewConverter;
import io.yak.ops.business.semantic.controller.v1.vo.StandardFieldVO;
import io.yak.ops.business.semantic.field.SemanticFieldService;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.common.constant.semantic.SemanticPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Standard field library management (ticket 35 + 评审补充). */
@Tag(name = "业务语义标准字段接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/semantic/fields")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(SemanticPermissionCode.READ)
public class SemanticFieldController {

  private final SemanticFieldService service;
  private final StandardFieldViewConverter viewConverter;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "创建标准字段")
  @RequiresPermission(SemanticPermissionCode.CREATE)
  @PostMapping
  public Result<StandardFieldVO> create(
      @Valid @RequestBody SemanticFieldApi.CreateRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(viewConverter.toView(service.create(request, operator)));
  }

  @Operation(summary = "分页查询标准字段（可按角色筛选）")
  @PostMapping("/page")
  public Result<PagingData<StandardFieldVO>> page(
      @Valid @RequestBody SemanticFieldApi.FieldQuery query) {
    var page = service.page(query.pageNo(), query.pageSize(), query.role(), query.keyword());
    // 引用名称服务端解析(2026-09-16):列表直接给出"名称（编码）",前端不再依赖标准字典
    java.util.Set<Long> refIds = new java.util.HashSet<>();
    for (StandardField field : page.records()) {
      addIfPresent(refIds, field.stdTypeId());
      addIfPresent(refIds, field.stdUnitId());
      addIfPresent(refIds, field.stdCaliberId());
      addIfPresent(refIds, field.stdSecurityId());
    }
    return Result.success(viewConverter.page(page, service.standardLabels(refIds)));
  }

  private static void addIfPresent(java.util.Set<Long> ids, Long id) {
    if (id != null) {
      ids.add(id);
    }
  }

  @Operation(summary = "标准字段详情")
  @GetMapping("/{id}")
  public Result<StandardFieldVO> get(@PathVariable("id") Long id) {
    return Result.success(viewConverter.toView(service.get(id)));
  }

  @Operation(summary = "编辑标准字段（编码不可改；version 乐观锁）")
  @RequiresPermission(SemanticPermissionCode.UPDATE)
  @PutMapping("/{id}")
  public Result<StandardFieldVO> update(
      @PathVariable("id") Long id,
      @Valid @RequestBody SemanticFieldApi.UpdateRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(
        viewConverter.toView(
            service.update(
                new SemanticFieldApi.UpdateRequest(
                    id,
                    request.version(),
                    request.name(),
                    request.role(),
                    request.dataType(),
                    request.stdTypeId(),
                    request.stdUnitId(),
                    request.stdCaliberId(),
                    request.stdCodeSetCode(),
                    request.stdSecurityId(),
                    request.businessDesc()),
                operator)));
  }

  @Operation(summary = "启用/停用标准字段（停用不出现在字段集/绑定/派生）")
  @RequiresPermission(SemanticPermissionCode.UPDATE)
  @PostMapping("/{id}/status")
  public Result<StandardFieldVO> changeStatus(
      @PathVariable("id") Long id, @Valid @RequestBody SemanticFieldApi.StatusRequest request,
      HttpServletRequest httpRequest) {
    return Result.success(viewConverter.toView(
        service.changeStatus(id, request.status(), currentUserProvider.getCurrentUser(httpRequest))));
  }

  @Operation(summary = "删除标准字段（被过程引用时阻断）")
  @RequiresPermission(SemanticPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return Result.success(Boolean.TRUE);
  }
}
