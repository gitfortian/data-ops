package io.yak.ops.business.metadata.harvest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.yak.ops.business.metadata.dao.mapper.MdCollectJobMapper;
import io.yak.ops.business.metadata.dao.mapper.MdCollectRunMapper;
import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.business.metadata.harvest.CollectRunService.RunView;
import io.yak.ops.business.metadata.harvest.CollectRunService.TriggerOutcome;
import io.yak.ops.business.metadata.harvest.MetadataHarvestService.HarvestRequest;
import io.yak.ops.business.metadata.harvest.MetadataHarvestService.HarvestSummary;
import io.yak.ops.business.metadata.dao.model.MdCollectJobPO;
import io.yak.ops.business.metadata.dao.model.MdCollectRunPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.ProviderType;
import io.yak.ops.common.enums.metadata.MetadataEnums.RunStatus;
import io.yak.ops.common.enums.metadata.MetadataEnums.TriggerType;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 三条触发通道的准入与回写（ticket 116）。
 *
 * <p>采集本体在 ticket 114 的测试里已经验过，这里只验"谁能让它跑起来、跑完留下什么"：
 * 停用任务能否手工跑（不能）、对账通道有没有被装成能跑（不该）、一轮在跑时第二轮有没有排队
 * （真实轮次要拒、预演不必）、跑完往任务行上写了什么（只有两列）。
 * 最后这一条尤其要紧：回写用错 {@code updateById} 就会把别人刚改的 cron 悄悄吃掉，
 * 而这种情况<b>测试全绿、现网只在下次没按时跑才发现</b>。
 */
class CollectRunServiceTest {

  private static final long PROJECT_ID = 42L;
  private static final long JOB_ID = 9L;
  private static final long RUN_ID = 777L;

  private final MdCollectJobMapper jobMapper = mock(MdCollectJobMapper.class);
  private final MdCollectRunMapper runMapper = mock(MdCollectRunMapper.class);
  private final MetadataHarvestService harvestService = mock(MetadataHarvestService.class);
  private final CurrentProject currentProject =
      () -> Optional.of(new ProjectContext(PROJECT_ID, null));

  private CollectRunService service;

  @BeforeEach
  void wireService() {
    // LambdaUpdateWrapper.set 会当场把方法引用翻成列名，而这份缓存平时由 Spring 建 MyBatis 会话时灌；
    // 纯单测里不灌，代码还没走到写库就先抛"can not find lambda cache"。
    for (Class<?> po : List.of(MdCollectJobPO.class, MdCollectRunPO.class)) {
      TableInfoHelper.initTableInfo(
          new MapperBuilderAssistant(new MybatisConfiguration(), ""), po);
    }
    service = new CollectRunService(jobMapper, runMapper, harvestService, currentProject);
    when(harvestService.harvest(any())).thenReturn(summary(RunStatus.SUCCESS));
    when(runMapper.selectById(any())).thenReturn(runRow());
  }

  @Test
  void aDisabledJobCanStillBeRunByHand() {
    MdCollectJobPO job = job(false, ProviderType.HARVESTED);
    when(jobMapper.selectOne(any())).thenReturn(job);

    RunView run = service.runNow(JOB_ID, "root");

    HarvestRequest request = capturedRequest();
    assertThat(request.job()).isSameAs(job);
    assertThat(request.triggerType()).isEqualTo(TriggerType.MANUAL);
    assertThat(request.dryRun()).isFalse();
    assertThat(request.operator()).isEqualTo("root");
    assertThat(run.runId()).isEqualTo(RUN_ID);
    assertThat(run.status()).isEqualTo(RunStatus.SUCCESS);
    // 抽屉要能直接渲染，不必再猜"这一轮是谁、什么时候起的"
    assertThat(run.cntTotal()).isEqualTo(3);
    assertThat(run.startedAt()).isNotNull();
    assertThat(run.createdBy()).isEqualTo("root");
  }

  @Test
  void theReconcileChannelDoesNotPretendItCanRun() {
    when(jobMapper.selectOne(any())).thenReturn(job(true, ProviderType.REGISTERED));

    assertThatThrownBy(() -> service.runNow(JOB_ID, "root"))
        .isInstanceOf(MetadataException.class)
        .extracting(e -> ((MetadataException) e).getErrorCode())
        .isEqualTo(MetadataErrorCode.PROVIDER_UNAVAILABLE);
    // 让它走采集分支会把对账任务采成物理表：宁可不跑，也不跑错
    verify(harvestService, never()).harvest(any());
    verify(jobMapper, never()).update(any(), any());
  }

  @Test
  void aSecondRealRoundIsRefusedWhileOneIsRunning() {
    when(jobMapper.selectOne(any())).thenReturn(job(true, ProviderType.HARVESTED));
    when(runMapper.selectOne(any())).thenReturn(runningRow());

    assertThatThrownBy(() -> service.runNow(JOB_ID, "root"))
        .isInstanceOf(MetadataException.class)
        .extracting(e -> ((MetadataException) e).getErrorCode())
        .isEqualTo(MetadataErrorCode.HARVEST_RUNNING);
    verify(harvestService, never()).harvest(any());
    verify(jobMapper, never()).update(any(), any());
  }

  /**
   * 预演不发写语句，两批预演交错也不会留下脏数据，所以不排队。
   * 拒掉它只会让人学会"预演前先关掉别的任务"，那是更坏的习惯。
   */
  @Test
  void aDryRunDoesNotQueueBehindARunningRound() {
    when(jobMapper.selectOne(any())).thenReturn(job(true, ProviderType.HARVESTED));
    when(runMapper.selectOne(any())).thenReturn(runningRow());

    service.dryRun(JOB_ID, "root");

    verify(runMapper, never()).selectOne(any());
    HarvestRequest request = capturedRequest();
    assertThat(request.dryRun()).isTrue();
    assertThat(request.triggerType()).isEqualTo(TriggerType.DRY_RUN);
  }

  /**
   * 回写只 patch 两列，且<b>整行实体参数必须是 null</b>。
   *
   * <p>{@code updateById(job)} 也能通过"值对不对"的检查，但它的语义是拿 {@code execute} 开头读到的
   * 旧快照覆写整行——并发编辑任务配置时，对方刚改的 cron 会被一次采集回写悄悄吃掉。
   */
  @Test
  void aRoundPatchesTheJobInsteadOfOverwritingIt() {
    when(jobMapper.selectOne(any())).thenReturn(job(true, ProviderType.HARVESTED));

    service.runNow(JOB_ID, "root");

    assertThat(patchedColumns()).containsExactly("last_run_id");
  }

  /** 预演的结果也要留在任务行上——它是启用闸门的凭据，但除了这两列什么都不碰。 */
  @Test
  void aDryRunAlsoWritesItsVerdictOntoTheJob() {
    when(jobMapper.selectOne(any())).thenReturn(job(true, ProviderType.HARVESTED));

    service.dryRun(JOB_ID, "root");

    assertThat(patchedColumns()).containsExactly("last_run_id", "dry_run_passed");
  }

  @SuppressWarnings("unchecked")
  private List<String> patchedColumns() {
    ArgumentCaptor<LambdaUpdateWrapper<MdCollectJobPO>> captor =
        ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    // 实体参数为 null = 这不是整行覆写
    verify(jobMapper).update(isNull(), captor.capture());
    return Arrays.stream(captor.getValue().getSqlSet().split(","))
        .map(column -> column.trim().split("=")[0])
        .toList();
  }

  @Test
  void unknownOrForeignJobIsReportedAsMissing() {
    when(jobMapper.selectOne(any())).thenReturn(null);

    assertThatThrownBy(() -> service.runNow(JOB_ID, "root"))
        .isInstanceOf(MetadataException.class)
        .extracting(e -> ((MetadataException) e).getErrorCode())
        .isEqualTo(MetadataErrorCode.COLLECT_JOB_NOT_FOUND);
    verify(harvestService, never()).harvest(any());
  }

  @Test
  void aScheduledRoundOfADisabledJobSkipsWithoutFailing() {
    when(jobMapper.selectOne(any())).thenReturn(job(false, ProviderType.HARVESTED));

    TriggerOutcome outcome = service.triggerScheduled(JOB_ID);

    assertThat(outcome.runId()).isNull();
    assertThat(outcome.message()).contains("已停用");
    verify(harvestService, never()).harvest(any());
  }

  /**
   * 调度轮次的操作人只能由代码给：{@code yak_md_collect_run.created_by} 与
   * {@code yak_md_change.changed_by} 都是 NOT NULL，留 null 会让第一轮 GONE 的写事务在最深处回滚。
   */
  @Test
  void aScheduledRoundCarriesTheSystemOperatorAndReportsItsCounts() {
    when(jobMapper.selectOne(any())).thenReturn(job(true, ProviderType.HARVESTED));

    TriggerOutcome outcome = service.triggerScheduled(JOB_ID);

    HarvestRequest request = capturedRequest();
    assertThat(request.operator()).isEqualTo("system");
    assertThat(request.triggerType()).isEqualTo(TriggerType.SCHEDULE);
    assertThat(outcome.runId()).isEqualTo(RUN_ID);
    assertThat(outcome.status()).isEqualTo(RunStatus.SUCCESS);
    assertThat(outcome.message())
        .isEqualTo("采集 SUCCESS：seen=3 new=1 changed=1 gone=1");
  }

  private HarvestRequest capturedRequest() {
    ArgumentCaptor<HarvestRequest> captor = ArgumentCaptor.forClass(HarvestRequest.class);
    verify(harvestService).harvest(captor.capture());
    return captor.getValue();
  }

  private static MdCollectJobPO job(boolean enabled, ProviderType provider) {
    MdCollectJobPO job = new MdCollectJobPO();
    job.setId(JOB_ID);
    job.setProjectId(PROJECT_ID);
    job.setJobCode("harvest-ds7");
    job.setJobName("shop 库采集");
    job.setProviderType(provider.name());
    job.setDataSourceId(7L);
    job.setCollectColumns(true);
    job.setEnabled(enabled);
    job.setCronExpression("0 0 3 * * ?");
    return job;
  }

  private static MdCollectRunPO runningRow() {
    MdCollectRunPO row = new MdCollectRunPO();
    row.setId(RUN_ID);
    row.setJobId(JOB_ID);
    row.setProjectId(PROJECT_ID);
    row.setStatus(RunStatus.RUNNING.name());
    row.setStartedAt(LocalDateTime.now().minusMinutes(5));
    return row;
  }

  private static MdCollectRunPO runRow() {
    MdCollectRunPO row = new MdCollectRunPO();
    row.setId(RUN_ID);
    row.setJobId(JOB_ID);
    row.setProjectId(PROJECT_ID);
    row.setProviderType(ProviderType.HARVESTED.name());
    row.setTriggerType(TriggerType.MANUAL.name());
    row.setDryRun(false);
    row.setStatus(RunStatus.SUCCESS.name());
    row.setCntTotal(3);
    row.setCntNew(1);
    row.setCntChanged(1);
    row.setCntUnchanged(0);
    row.setCntGone(1);
    row.setCntPartialFailed(0);
    row.setStartedAt(LocalDateTime.now().minusMinutes(1));
    row.setFinishedAt(LocalDateTime.now());
    row.setDurationMs(1_200L);
    row.setCreatedBy("root");
    return row;
  }

  private static HarvestSummary summary(RunStatus status) {
    return new HarvestSummary(
        RUN_ID, status, 3, 1, 1, 0, 0, 1, List.of(), null);
  }
}
