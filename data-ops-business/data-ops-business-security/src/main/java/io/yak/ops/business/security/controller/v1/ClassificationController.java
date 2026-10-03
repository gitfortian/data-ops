package io.yak.ops.business.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.security.application.ClassificationService;
import io.yak.ops.business.security.dao.model.DsecClassificationPO;
import io.yak.ops.common.constant.security.SecurityPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 资产分级标签维护(票据 73)。 */
@Tag(name = "数据安全-分级分类接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-security/classifications")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(SecurityPermissionCode.READ)
public class ClassificationController {

  private final ClassificationService service;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "分级标签分页查询")
  @PostMapping("/page")
  public Result<PagingData<DsecClassificationPO>> page(@Valid @RequestBody PageQuery query) {
    return Result.success(PagingData.from(service.page(query.pageNo(), query.pageSize(),
        query.keyword(), query.levelId(), query.categoryId(), query.status())));
  }

  @Operation(summary = "分级标签详情")
  @GetMapping("/{id}")
  public Result<DsecClassificationPO> get(@PathVariable("id") Long id) {
    return Result.success(service.get(id));
  }

  @Operation(summary = "新增/更新分级标签(按对象自然键幂等)")
  @RequiresPermission(SecurityPermissionCode.CREATE)
  @PostMapping
  public Result<DsecClassificationPO> upsert(@Valid @RequestBody UpsertRequest body,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(service.upsert(body.objectType(), body.datasourceId(), body.dbName(),
        body.tableName(), body.columnName(), body.levelId(), body.categoryId(), body.source(),
        body.confidence(), body.discoveryRuleId(), body.status(), body.objectName(), operator));
  }

  @Operation(summary = "候选确认/状态流转")
  @RequiresPermission(SecurityPermissionCode.UPDATE)
  @PostMapping("/{id}/status")
  public Result<DsecClassificationPO> changeStatus(@PathVariable("id") Long id,
      @Valid @RequestBody StatusRequest body) {
    return Result.success(service.changeStatus(id, body.status()));
  }

  @Operation(summary = "删除分级标签")
  @RequiresPermission(SecurityPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return Result.success(Boolean.TRUE);
  }

  public record PageQuery(int pageNo, int pageSize, String keyword, Long levelId, Long categoryId,
      String status) {}

  public record UpsertRequest(@NotBlank String objectType, Long datasourceId, String dbName,
      String tableName, String columnName, Long levelId, Long categoryId, String source,
      Integer confidence, Long discoveryRuleId, String status, String objectName) {}

  public record StatusRequest(@NotBlank String status) {}
}
