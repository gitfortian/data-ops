package io.yak.ops.business.lifecycle.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.lifecycle.controller.v1.dto.LifecycleRequests.PolicyQueryDTO;
import io.yak.ops.business.lifecycle.controller.v1.dto.LifecycleRequests.PolicyUpsertDTO;
import io.yak.ops.business.lifecycle.controller.v1.dto.LifecycleRequests.StatusDTO;
import io.yak.ops.business.lifecycle.policy.TtlPolicyService;
import io.yak.ops.business.lifecycle.policy.TtlPolicyService.LayerTemplateView;
import io.yak.ops.business.lifecycle.policy.TtlPolicyService.PolicyView;
import io.yak.ops.business.lifecycle.policy.TtlPolicyService.UpsertCommand;
import io.yak.ops.business.lifecycle.policy.TtlPolicyVersionService;
import io.yak.ops.common.constant.lifecycle.LifecyclePermissionCode;
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

/** TTL 策略管理 REST API(ticket 81/82)。 */
@Tag(name = "数据生命周期-策略管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lifecycle")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(LifecyclePermissionCode.READ)
public class TtlPolicyController {

  private final TtlPolicyService policyService;
  private final TtlPolicyVersionService policyVersionService;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "分页查询策略")
  @PostMapping("/policies/page")
  public Result<PagingData<PolicyView>> page(@Valid @RequestBody PolicyQueryDTO query) {
    PageData<PolicyView> page = policyService.page(query.getPageNo(), query.getPageSize(),
        query.getScopeType(), query.getLayerCode(), query.getKeyword());
    return Result.success(PagingData.from(page));
  }

  @Operation(summary = "策略详情")
  @GetMapping("/policies/{id}")
  public Result<PolicyView> get(@PathVariable("id") Long id) {
    return Result.success(policyService.get(id));
  }

  @Operation(summary = "新建策略")
  @RequiresPermission(LifecyclePermissionCode.CREATE)
  @PostMapping("/policies")
  public Result<PolicyView> create(
      @Valid @RequestBody PolicyUpsertDTO dto, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(policyService.create(toCommand(dto), operator));
  }

  @Operation(summary = "更新策略")
  @RequiresPermission(LifecyclePermissionCode.UPDATE)
  @PutMapping("/policies/{id}")
  public Result<PolicyView> update(
      @PathVariable("id") Long id,
      @Valid @RequestBody PolicyUpsertDTO dto,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(policyService.update(id, toCommand(dto), operator));
  }

  @Operation(summary = "删除策略")
  @RequiresPermission(LifecyclePermissionCode.DELETE)
  @DeleteMapping("/policies/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    policyService.delete(id, operator);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "启用/停用策略")
  @RequiresPermission(LifecyclePermissionCode.UPDATE)
  @PostMapping("/policies/{id}/status")
  public Result<PolicyView> changeStatus(
      @PathVariable("id") Long id,
      @Valid @RequestBody StatusDTO dto,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(policyService.changeStatus(id, dto.getStatus(), operator));
  }

  @Operation(summary = "初始化分层默认策略(幂等,仅补缺失层)")
  @RequiresPermission(LifecyclePermissionCode.CREATE)
  @PostMapping("/policies/initialize-layer-defaults")
  public Result<Integer> initializeLayerDefaults(HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(policyService.initializeLayerDefaults(operator));
  }

  @Operation(summary = "分层默认模板(新建策略预填用)")
  @GetMapping("/policies/layer-template")
  public Result<List<LayerTemplateView>> layerTemplate() {
    return Result.success(policyService.layerTemplate());
  }

  @Operation(summary = "发布策略(草稿→新版本;内容未变则幂等)")
  @RequiresPermission(LifecyclePermissionCode.UPDATE)
  @PostMapping("/policies/{id}/publish")
  public Result<TtlPolicyVersionService.PublishResult> publish(
      @PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(policyVersionService.publish(id, operator));
  }

  @Operation(summary = "下线策略(退出生效,保留发布指针可再上线)")
  @RequiresPermission(LifecyclePermissionCode.UPDATE)
  @PostMapping("/policies/{id}/offline")
  public Result<Boolean> offline(
      @PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    policyVersionService.offline(id, operator);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "版本历史列表(倒序)")
  @GetMapping("/policies/{id}/versions")
  public Result<List<TtlPolicyVersionService.VersionSummary>> listVersions(
      @PathVariable("id") Long id) {
    return Result.success(policyVersionService.versions(id));
  }

  @Operation(summary = "单版快照内容(diff 数据源)")
  @GetMapping("/policies/{id}/versions/{versionNo}")
  public Result<TtlPolicyVersionService.VersionDetailView> getVersion(
      @PathVariable("id") Long id, @PathVariable("versionNo") int versionNo) {
    return Result.success(policyVersionService.versionDetail(id, versionNo));
  }

  @Operation(summary = "回滚到指定版本(追加式:恢复草稿并立即发布)")
  @RequiresPermission(LifecyclePermissionCode.UPDATE)
  @PostMapping("/policies/{id}/versions/{versionNo}/rollback")
  public Result<TtlPolicyVersionService.PublishResult> rollback(
      @PathVariable("id") Long id, @PathVariable("versionNo") int versionNo,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(policyVersionService.rollback(id, versionNo, operator));
  }

  private static UpsertCommand toCommand(PolicyUpsertDTO dto) {
    return new UpsertCommand(dto.getPolicyCode(), dto.getPolicyName(), dto.getScopeType(),
        dto.getLayerCode(), dto.getPartitionGranularity(), dto.getHotDays(), dto.getColdDays(),
        dto.getDestroyDays(), dto.getRemark());
  }
}
