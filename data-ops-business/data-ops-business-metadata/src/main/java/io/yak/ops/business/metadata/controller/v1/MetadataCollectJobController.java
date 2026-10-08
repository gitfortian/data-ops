package io.yak.ops.business.metadata.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.metadata.controller.v1.dto.CollectJobRequests.EnabledDTO;
import io.yak.ops.business.metadata.controller.v1.dto.CollectJobRequests.JobQueryDTO;
import io.yak.ops.business.metadata.controller.v1.dto.CollectJobRequests.JobUpsertDTO;
import io.yak.ops.business.metadata.controller.v1.dto.CollectJobRequests.RunQueryDTO;
import io.yak.ops.business.metadata.harvest.CollectJobAdminService;
import io.yak.ops.business.metadata.harvest.CollectJobAdminService.JobView;
import io.yak.ops.business.metadata.harvest.CollectJobAdminService.UpsertCommand;
import io.yak.ops.business.metadata.harvest.CollectRunService;
import io.yak.ops.business.metadata.harvest.MetadataPresenceService;
import io.yak.ops.business.metadata.harvest.CollectRunService.RunView;
import io.yak.ops.common.constant.metadata.MetadataPermissionCode;
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

/**
 * 采集/对账任务与运行历史 API（ticket 116）。
 *
 * <p><b>{@code POST …/run} 是开发/演示主路径</b>：本平台调度器是 Quartz 内存存储，重启后既不补跑
 * 也不保留"该跑没跑"的记录（plan §3.7），所以定时通道之外必须有一条立刻见效的手工通道。
 * 手工触发不看 {@code enabled}——改完作用域想验证一次，不该被迫先启用（而启用又要求先预演通过）。
 *
 * <p>一轮采集是同步执行的：调用方直接拿到这一轮的四个计数。作用域收窄到单库单 schema 时这是最快的
 * 反馈；ticket 122 的页面因此要在按钮上把"本轮在跑"显示出来，而不是把它做成异步任务再查状态。
 */
@Tag(name = "元数据-采集任务")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/metadata")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MetadataPermissionCode.READ)
public class MetadataCollectJobController {

  private final CollectJobAdminService jobService;
  private final CollectRunService runService;
  private final MetadataPresenceService presenceService;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "任务分页（物理采集与投影对账同一张列表，只按 providerType 分面）")
  @PostMapping("/collect-jobs/page")
  public Result<PagingData<JobView>> page(@Valid @RequestBody JobQueryDTO query) {
    PageData<JobView> page =
        jobService.page(
            query.getPageNo(),
            query.getPageSize(),
            query.getProviderType(),
            query.getEnabled(),
            query.getKeyword());
    return Result.success(PagingData.from(page));
  }

  @Operation(summary = "当前实际生效的物理采集 GONE 判定策略（全局，不是任务级）")
  @GetMapping("/collect-jobs/effective-presence-policy")
  public Result<MetadataPresenceService.EffectivePolicy> effectivePresencePolicy() {
    return Result.success(presenceService.effectivePolicy());
  }

  @Operation(summary = "任务详情")
  @GetMapping("/collect-jobs/{id}")
  public Result<JobView> get(@PathVariable("id") Long id) {
    return Result.success(jobService.get(id));
  }

  @Operation(summary = "新建任务（一律落成停用，启用前必须先 dry-run 通过）")
  @RequiresPermission(MetadataPermissionCode.CREATE)
  @PostMapping("/collect-jobs")
  public Result<JobView> create(
      @Valid @RequestBody JobUpsertDTO dto, HttpServletRequest httpRequest) {
    return Result.success(jobService.create(toCommand(dto), operator(httpRequest)));
  }

  @Operation(summary = "修改任务（整行替换；作用域一变，dry-run 结论即失效）")
  @RequiresPermission(MetadataPermissionCode.UPDATE)
  @PutMapping("/collect-jobs/{id}")
  public Result<JobView> update(
      @PathVariable("id") Long id,
      @Valid @RequestBody JobUpsertDTO dto,
      HttpServletRequest httpRequest) {
    return Result.success(jobService.update(id, toCommand(dto), operator(httpRequest)));
  }

  @Operation(summary = "启用/停用（启用要求已有通过的 dry-run 预演）")
  @RequiresPermission(MetadataPermissionCode.UPDATE)
  @PostMapping("/collect-jobs/{id}/enabled")
  public Result<JobView> changeEnabled(
      @PathVariable("id") Long id,
      @Valid @RequestBody EnabledDTO dto,
      HttpServletRequest httpRequest) {
    return Result.success(
        jobService.changeEnabled(id, Boolean.TRUE.equals(dto.getEnabled()), operator(httpRequest)));
  }

  @Operation(summary = "删除任务（软删 + 撤闹钟；运行历史与变更流水保留）")
  @RequiresPermission(MetadataPermissionCode.DELETE)
  @DeleteMapping("/collect-jobs/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
    jobService.delete(id, operator(httpRequest));
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "dry-run 预演：只算不写，通过则任务可启用（plan §0.13）")
  @RequiresPermission(MetadataPermissionCode.CREATE)
  @PostMapping("/collect-jobs/{id}/dry-run")
  public Result<RunView> dryRun(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
    return Result.success(runService.dryRun(id, operator(httpRequest)));
  }

  @Operation(summary = "立即采集（定时通道之外的开发/演示主路径）")
  @RequiresPermission(MetadataPermissionCode.CREATE)
  @PostMapping("/collect-jobs/{id}/run")
  public Result<RunView> run(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
    return Result.success(runService.runNow(id, operator(httpRequest)));
  }

  @Operation(summary = "运行历史分页（四计数 + 状态 + 耗时 + 熔断原因）")
  @PostMapping("/collect-runs/page")
  public Result<PagingData<RunView>> runs(@Valid @RequestBody RunQueryDTO query) {
    PageData<RunView> page =
        runService.pageRuns(query.getJobId(), query.getPageNo(), query.getPageSize());
    return Result.success(PagingData.from(page));
  }

  private String operator(HttpServletRequest httpRequest) {
    return currentUserProvider.getCurrentUser(httpRequest);
  }

  private static UpsertCommand toCommand(JobUpsertDTO dto) {
    return new UpsertCommand(
        dto.getJobCode(),
        dto.getJobName(),
        dto.getProviderType(),
        dto.getTypeName(),
        dto.getDataSourceId(),
        dto.getDatabaseName(),
        dto.getSchemaName(),
        dto.getTablePattern(),
        dto.getCollectColumns(),
        dto.getCronExpression(),
        dto.getCollapseThresholdPct(),
        dto.getMissingRounds());
  }
}
