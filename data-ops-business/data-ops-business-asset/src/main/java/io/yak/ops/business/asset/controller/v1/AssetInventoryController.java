package io.yak.ops.business.asset.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.ReconcileTriggerDTO;
import io.yak.ops.business.asset.reconcile.AssetReconcileService;
import io.yak.ops.business.asset.reconcile.AssetReconcileService.ChangeView;
import io.yak.ops.common.constant.asset.AssetPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 对账与变更流水 REST API(ticket 95;确认/忽略 ticket 96)。 */
@Tag(name = "数据资产-盘点")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/assets")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(AssetPermissionCode.READ)
public class AssetInventoryController {

  private final AssetReconcileService reconcileService;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "手动触发对账(异步受理;互斥 48012,来源缺失 48011)")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PostMapping("/reconcile")
  public Result<String> reconcile(
      @RequestBody(required = false) ReconcileTriggerDTO dto, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(reconcileService.submit(
        dto == null ? List.of() : dto.getSourceTypes(), operator));
  }

  @Operation(summary = "各来源最近对账状态")
  @GetMapping("/reconcile/status")
  public Result<List<Map<String, Object>>> status() {
    return Result.success(reconcileService.status());
  }

  @Operation(summary = "变更流水分页(handleStatus=OPEN/CONFIRMED/IGNORED)")
  @GetMapping("/changes")
  public Result<PagingData<ChangeView>> changes(
      @RequestParam(value = "handleStatus", required = false) String handleStatus,
      @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
      @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
    return Result.success(PagingData.from(reconcileService.pageChanges(handleStatus,
        Math.max(pageNo, 1), Math.min(Math.max(pageSize, 1), 200))));
  }

  @Operation(summary = "确认变更(快照新值覆盖台账展示字段;已处理 48014)")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PostMapping("/changes/{id}/confirm")
  public Result<ChangeView> confirmChange(@PathVariable("id") Long id,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(reconcileService.confirmChange(id, operator));
  }

  @Operation(summary = "忽略变更(仅关闭流水,不动台账)")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PostMapping("/changes/{id}/ignore")
  public Result<Boolean> ignoreChange(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    reconcileService.ignoreChange(id, operator);
    return Result.success(Boolean.TRUE);
  }
}
