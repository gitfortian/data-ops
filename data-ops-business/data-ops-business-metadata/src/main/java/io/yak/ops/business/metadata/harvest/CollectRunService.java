package io.yak.ops.business.metadata.harvest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.metadata.dao.mapper.MdCollectJobMapper;
import io.yak.ops.business.metadata.dao.mapper.MdCollectRunMapper;
import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.business.metadata.harvest.MetadataHarvestService.HarvestRequest;
import io.yak.ops.business.metadata.harvest.MetadataHarvestService.HarvestSummary;
import io.yak.ops.business.metadata.dao.model.MdCollectJobPO;
import io.yak.ops.business.metadata.dao.model.MdCollectRunPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.ProviderType;
import io.yak.ops.common.enums.metadata.MetadataEnums.RunStatus;
import io.yak.ops.common.enums.metadata.MetadataEnums.TriggerType;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 一轮触发的统一入口（ticket 116）：手工、dry-run、定时三条路都收在这里，
 * 于是"跑一轮"这件事只有一份准入判断、一份 {@code last_run_id} 回写。
 *
 * <p><b>本类刻意不加 {@code @Transactional}</b>。一轮采集的正确结果包含"它失败了"这条事实，
 * 把它和执行包在同一事务里 = 失败时连运行历史一起回滚，而 ticket 115 判 GONE 恰恰要靠这条
 * FAILED 行去证明"上一轮无效、不能拿它的时刻当缺席边界"。写事务的边界在
 * {@link AssetUpsertRepository} 那一批语句上。
 *
 * <p><b>dry-run 与真实轮次不互斥</b>：预演一条写语句都不发，代价只是可能读到别的轮次的中间态，
 * 不值得为它排队；真实轮次之间必须互斥——两批 {@code touchLastCollected} 交错会让在场时间
 * 说假话，而在场时间是 GONE 判据的唯一证据。
 */
@Slf4j
@Service
public class CollectRunService {

  /**
   * 调度轮次的操作人落库值。
   *
   * <p>不能留空：{@code yak_md_collect_run.created_by} 与 {@code yak_md_change.changed_by}
   * 都是 NOT NULL 且无默认值，调度线程没有 HTTP 头可取用户名，留 null 会让第一轮 GONE 的
   * 整条写事务在最深处回滚——现场只看到一个失败的闹钟。
   */
  static final String SCHEDULE_OPERATOR = "system";

  /** RUNNING 行超过这个时长即视为崩溃残留；不放过去，一次 JVM 崩溃会让任务永久不可再跑。 */
  static final int RUNNING_STALE_MINUTES = 120;

  private final MdCollectJobMapper jobMapper;
  private final MdCollectRunMapper runMapper;
  private final MetadataHarvestService harvestService;
  private final CurrentProject currentProject;

  public CollectRunService(
      MdCollectJobMapper jobMapper,
      MdCollectRunMapper runMapper,
      MetadataHarvestService harvestService,
      CurrentProject currentProject) {
    this.jobMapper = jobMapper;
    this.runMapper = runMapper;
    this.harvestService = harvestService;
    this.currentProject = currentProject;
  }

  /**
   * 手工触发（开发/演示主路径，plan §3.7）。
   *
   * <p>停用中的任务照样可以手工跑：{@code COLLECT_JOB_DISABLED} 说的是"定时通道关着"，
   * 不是"这个任务不许执行"——否则改完作用域想验证一次就得先启用，而启用又要求先预演通过。
   */
  public RunView runNow(Long jobId, String operator) {
    return execute(requireJob(jobId), TriggerType.MANUAL, false, operator);
  }

  /** dry-run 预演：写路径一条语句都不发，只把"通过没通过"落到任务行上（plan §0.13）。 */
  public RunView dryRun(Long jobId, String operator) {
    return execute(requireJob(jobId), TriggerType.DRY_RUN, true, operator);
  }

  /**
   * 调度轮次。与手工的差别只在多一道启用校验，且拒绝时<b>不判失败</b>：
   * 闹钟还在引擎里而任务已经停用，是登记与状态没对齐，不是采集本身出了错。
   */
  public TriggerOutcome triggerScheduled(Long jobId) {
    MdCollectJobPO job = requireJob(jobId);
    if (!Boolean.TRUE.equals(job.getEnabled())) {
      log.warn("任务已停用，跳过调度轮次 job={}", job.getJobCode());
      return TriggerOutcome.skipped("任务已停用，未执行：job=" + job.getJobCode());
    }
    RunView run = execute(job, TriggerType.SCHEDULE, false, SCHEDULE_OPERATOR);
    return new TriggerOutcome(
        run.runId(),
        run.status(),
        "采集 " + run.status() + "：seen=" + run.cntTotal()
            + " new=" + run.cntNew()
            + " changed=" + run.cntChanged()
            + " gone=" + run.cntGone());
  }

  /** 运行历史（ticket 122 的抽屉）：最新的在最前，含 SUSPECT/FAILED 的原因原文。 */
  public PageData<RunView> pageRuns(Long jobId, int pageNo, int pageSize) {
    Page<MdCollectRunPO> result =
        runMapper.selectPage(
            new Page<>(pageNo, pageSize),
            new LambdaQueryWrapper<MdCollectRunPO>()
                .eq(MdCollectRunPO::getProjectId, currentProject.requireProjectId())
                .eq(jobId != null, MdCollectRunPO::getJobId, jobId)
                .orderByDesc(MdCollectRunPO::getStartedAt)
                .orderByDesc(MdCollectRunPO::getId));
    return new PageData<>(
        result.getRecords().stream().map(RunView::from).toList(),
        result.getTotal(),
        result.getPages(),
        (int) result.getCurrent(),
        (int) result.getSize());
  }

  private RunView execute(
      MdCollectJobPO job, TriggerType triggerType, boolean dryRun, String operator) {
    if (!ProviderType.HARVESTED.name().equals(job.getProviderType())) {
      // 对账通道的执行体是 ticket 135。这里不装成"能跑"：让它走采集分支会把对账任务采成物理表。
      throw new MetadataException(
          MetadataErrorCode.PROVIDER_UNAVAILABLE,
          "通道 " + job.getProviderType() + " 的执行体尚未落地，暂不可触发 job=" + job.getJobCode());
    }
    if (!dryRun) {
      refuseIfRunning(job);
    }
    HarvestSummary summary =
        harvestService.harvest(new HarvestRequest(job, triggerType, dryRun, operator));
    patchJobAfterRun(job, summary, dryRun);
    return RunView.from(runMapper.selectById(summary.runId()));
  }

  /**
   * 只 patch 这两列，不用 {@code updateById}：后者会把整行按 {@link #execute} 开头读到的快照覆写回去，
   * 并发编辑任务配置时，对方刚改的 {@code cron_expression} 会被一次采集回写悄悄吃掉。
   */
  private void patchJobAfterRun(MdCollectJobPO job, HarvestSummary summary, boolean dryRun) {
    LambdaUpdateWrapper<MdCollectJobPO> patch =
        new LambdaUpdateWrapper<MdCollectJobPO>()
            .eq(MdCollectJobPO::getId, job.getId())
            .set(MdCollectJobPO::getLastRunId, summary.runId());
    if (dryRun) {
      // 最近一次预演没通过就该回到"未通过"：闸门管的是启用这一步，不是记一次成功用一辈子。
      patch.set(MdCollectJobPO::getDryRunPassed, summary.status() == RunStatus.SUCCESS);
    }
    jobMapper.update(null, patch);
  }

  private void refuseIfRunning(MdCollectJobPO job) {
    MdCollectRunPO running =
        runMapper.selectOne(
            new LambdaQueryWrapper<MdCollectRunPO>()
                .eq(MdCollectRunPO::getProjectId, job.getProjectId())
                .eq(MdCollectRunPO::getJobId, job.getId())
                .eq(MdCollectRunPO::getStatus, RunStatus.RUNNING.name())
                .ge(
                    MdCollectRunPO::getStartedAt,
                    LocalDateTime.now().minusMinutes(RUNNING_STALE_MINUTES))
                .last("LIMIT 1"));
    if (running != null) {
      throw new MetadataException(
          MetadataErrorCode.HARVEST_RUNNING,
          "job=" + job.getJobCode() + " 已有轮次 run=" + running.getId() + " 在跑");
    }
  }

  /** 读任务必带 project 条件：调度线程的那一份是 {@code MetadataCollectScheduleHandler} 恢复进来的。 */
  private MdCollectJobPO requireJob(Long jobId) {
    MdCollectJobPO job =
        jobMapper.selectOne(
            new LambdaQueryWrapper<MdCollectJobPO>()
                .eq(MdCollectJobPO::getId, jobId)
                .eq(MdCollectJobPO::getProjectId, currentProject.requireProjectId())
                .eq(MdCollectJobPO::getDeleted, false));
    if (job == null) {
      throw new MetadataException(MetadataErrorCode.COLLECT_JOB_NOT_FOUND, "id=" + jobId);
    }
    return job;
  }

  /** 一轮运行历史的对外形状：四计数 + 状态 + 耗时 + 游标水位，一个字段都不藏。 */
  public record RunView(
      Long runId,
      Long jobId,
      String providerType,
      String triggerType,
      Boolean dryRun,
      RunStatus status,
      Integer cntTotal,
      Integer cntNew,
      Integer cntChanged,
      Integer cntUnchanged,
      Integer cntGone,
      Integer cntPartialFailed,
      String cursorWatermark,
      String scopeSnapshot,
      String errorMessage,
      LocalDateTime startedAt,
      LocalDateTime finishedAt,
      Long durationMs,
      String createdBy) {

    static RunView from(MdCollectRunPO row) {
      return new RunView(
          row.getId(),
          row.getJobId(),
          row.getProviderType(),
          row.getTriggerType(),
          row.getDryRun(),
          row.getStatus() == null ? null : RunStatus.valueOf(row.getStatus()),
          row.getCntTotal(),
          row.getCntNew(),
          row.getCntChanged(),
          row.getCntUnchanged(),
          row.getCntGone(),
          row.getCntPartialFailed(),
          row.getCursorWatermark(),
          row.getScopeSnapshot(),
          row.getErrorMessage(),
          row.getStartedAt(),
          row.getFinishedAt(),
          row.getDurationMs(),
          row.getCreatedBy());
    }
  }

  /**
   * 调度轮次的结论。
   *
   * @param runId 本轮的运行行 id；{@code null} = 本轮什么都没做
   */
  public record TriggerOutcome(Long runId, RunStatus status, String message) {

    static TriggerOutcome skipped(String reason) {
      return new TriggerOutcome(null, null, reason);
    }
  }
}
