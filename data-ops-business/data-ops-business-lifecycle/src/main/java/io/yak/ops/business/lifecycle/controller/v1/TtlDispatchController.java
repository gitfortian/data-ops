package io.yak.ops.business.lifecycle.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.lifecycle.controller.v1.dto.LifecycleRequests.RecordQueryDTO;
import io.yak.ops.business.lifecycle.dispatch.TtlDispatchService;
import io.yak.ops.business.lifecycle.dispatch.TtlDispatchService.DispatchOutcome;
import io.yak.ops.business.lifecycle.dao.model.LifecycleDispatchRecordPO;
import io.yak.ops.common.constant.lifecycle.LifecyclePermissionCode;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.TriggerType;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** TTL 下发记录与重试 REST API(ticket 85/86)。 */
@Tag(name = "数据生命周期-下发记录")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lifecycle")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(LifecyclePermissionCode.READ)
public class TtlDispatchController {

  private final TtlDispatchService dispatchService;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "分页查询下发记录")
  @PostMapping("/dispatch-records/page")
  public Result<PagingData<LifecycleDispatchRecordPO>> page(
      @Valid @RequestBody RecordQueryDTO query) {
    PageData<LifecycleDispatchRecordPO> page = dispatchService.pageRecords(
        query.getPageNo(), query.getPageSize(), query.getStatus(), query.getModelId());
    return Result.success(PagingData.from(page));
  }

  @Operation(summary = "重试单条失败记录(重放原语句)")
  @RequiresPermission(LifecyclePermissionCode.UPDATE)
  @PostMapping("/dispatch-records/{id}/retry")
  public Result<DispatchOutcome> retry(
      @PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(dispatchService.retry(id, operator, TriggerType.MANUAL));
  }
}
