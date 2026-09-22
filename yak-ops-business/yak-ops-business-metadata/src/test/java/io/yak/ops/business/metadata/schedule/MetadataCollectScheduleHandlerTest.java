package io.yak.ops.business.metadata.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.yak.framework.schedule.api.ScheduleExecutionContext;
import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.ops.business.metadata.dao.mapper.MdCollectJobMapper;
import io.yak.ops.business.metadata.dao.mapper.MdCollectRunMapper;
import io.yak.ops.business.metadata.harvest.CollectRunService;
import io.yak.ops.business.metadata.harvest.MetadataHarvestService;
import io.yak.ops.business.metadata.harvest.MetadataHarvestService.HarvestRequest;
import io.yak.ops.business.metadata.harvest.MetadataHarvestService.HarvestSummary;
import io.yak.ops.common.bean.po.metadata.MdCollectJobPO;
import io.yak.ops.common.bean.po.metadata.MdCollectRunPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.ProviderType;
import io.yak.ops.common.enums.metadata.MetadataEnums.RunStatus;
import io.yak.ops.common.enums.metadata.MetadataEnums.TriggerType;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextException;
import io.yak.ops.core.project.ProjectContextScope;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 无请求头的调度线程能不能采对地方（ticket 116 验收清单第 4 条，plan §10 测试 4）。
 *
 * <p>这一条值得单独立一个测试类，因为它的失败方式在别的测试里看不见：{@code CurrentProject} 在调度线程
 * 里是空的，而 {@code project_id} 是 NOT NULL——忘了恢复上下文的表现不是"测试报错"，是
 * <b>要么在最深处抛异常、要么写出一批归属不明的目录行</b>。
 *
 * <p>所以这里用<b>真的</b> {@link CollectRunService}，只把采集本体换成"当场读一次当前项目"的假货：
 * 上下文没恢复，读取就会抛 {@code PROJECT_REQUIRED}，测试立刻红。假 {@link ProjectContextScope}
 * 与假 {@link io.yak.ops.core.project.CurrentProject} 共用一个 ThreadLocal，恢复与清理都是真的行为，
 * 于是"跑完没把上下文漏回调度线程"这件事也一并看得见。
 */
class MetadataCollectScheduleHandlerTest {

  private static final long PROJECT_ID = 42L;
  private static final long OTHER_PROJECT_ID = 43L;
  private static final long JOB_ID = 9L;
  private static final long RUN_ID = 777L;

  private final ScopedProject projectScope = new ScopedProject();
  private final MdCollectJobMapper jobMapper = mock(MdCollectJobMapper.class);
  private final MdCollectRunMapper runMapper = mock(MdCollectRunMapper.class);
  private final MetadataHarvestService harvestService = mock(MetadataHarvestService.class);

  private final AtomicReference<Long> projectIdSeenInsideHarvest = new AtomicReference<>();
  private final AtomicReference<HarvestRequest> captured = new AtomicReference<>();

  private MetadataCollectScheduleHandler handler;

  @BeforeEach
  void wireHandler() {
    // 真的 CollectRunService 收尾要 patch 任务行，而 LambdaUpdateWrapper.set 当场解析列名，
    // 这份缓存平时由 Spring 建会话时灌——不灌就轮不到断言。
    for (Class<?> po : List.of(MdCollectJobPO.class, MdCollectRunPO.class)) {
      TableInfoHelper.initTableInfo(
          new MapperBuilderAssistant(new MybatisConfiguration(), ""), po);
    }
    CollectRunService runService =
        new CollectRunService(jobMapper, runMapper, harvestService, projectScope);
    handler = new MetadataCollectScheduleHandler(runService, projectScope);
    when(harvestService.harvest(any()))
        .thenAnswer(
            call -> {
              captured.set(call.getArgument(0));
              // 采集真正落库时读的就是这个：没有上下文，这一行会抛 PROJECT_REQUIRED
              projectIdSeenInsideHarvest.set(projectScope.requireProjectId());
              return new HarvestSummary(
                  RUN_ID, RunStatus.SUCCESS, 3, 1, 1, 0, 0, 0, List.of(), null);
            });
    when(runMapper.selectById(any())).thenReturn(runRow());
  }

  @Test
  void aHeaderlessScheduleThreadRunsTheRoundInsideTheJobsProject() {
    when(jobMapper.selectOne(any())).thenReturn(job(true));
    assertThat(projectScope.isPresent()).isFalse();

    ScheduleExecutionResult result = handler.execute(context(Map.of("projectId", PROJECT_ID, "jobId", JOB_ID)));

    assertThat(result.accepted()).isTrue();
    assertThat(result.businessExecutionId()).isEqualTo(String.valueOf(RUN_ID));
    assertThat(result.message()).contains("SUCCESS");
    assertThat(projectIdSeenInsideHarvest).hasValue(PROJECT_ID);
    assertThat(captured.get().job().getProjectId()).isEqualTo(PROJECT_ID);
    assertThat(captured.get().triggerType()).isEqualTo(TriggerType.SCHEDULE);
    // 调度线程没有"当前用户"，而 created_by / changed_by 都是 NOT NULL
    assertThat(captured.get().operator()).isEqualTo("system");
  }

  /** 漏回调度的公共线程池，下一个任务的目录行就会挂到上一个项目上。 */
  @Test
  void theContextIsGoneAgainOnceTheRoundEnds() {
    when(jobMapper.selectOne(any())).thenReturn(job(true));

    handler.execute(context(Map.of("projectId", PROJECT_ID, "jobId", JOB_ID)));

    assertThat(projectScope.isPresent()).isFalse();
  }

  @Test
  void eachRoundIsBoundToItsOwnProjectsJob() {
    when(jobMapper.selectOne(any())).thenReturn(job(true));

    handler.execute(context(Map.of("projectId", OTHER_PROJECT_ID, "jobId", JOB_ID)));

    assertThat(projectIdSeenInsideHarvest).hasValue(OTHER_PROJECT_ID);
  }

  @Test
  void aDisabledJobIsSkippedWithoutLookingLikeAFailedRound() {
    when(jobMapper.selectOne(any())).thenReturn(job(false));

    ScheduleExecutionResult result =
        handler.execute(context(Map.of("projectId", PROJECT_ID, "jobId", JOB_ID)));

    assertThat(result.accepted()).isTrue();
    assertThat(result.businessExecutionId()).isNull();
    assertThat(result.message()).contains("已停用");
    verify(harvestService, never()).harvest(any());
  }

  /** 闹钟 payload 里少了 projectId 是登记侧写错，不是采集失败——要当场响。 */
  @Test
  void anIncompletePayloadFailsBeforeTouchingAnything() {
    assertThatThrownBy(() -> handler.execute(context(Map.of("jobId", JOB_ID))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("projectId");
    verify(harvestService, never()).harvest(any());
  }

  /**
   * 反向证据：同一套装配在没有上下文时读不动任务。
   *
   * <p>没有这一条，上面那个测试可能只是因为 mock 什么都返回而变绿。
   */
  @Test
  void outsideAnyScopeTheSameReadsRefuseToGuessAProject() {
    CollectRunService runService =
        new CollectRunService(jobMapper, runMapper, harvestService, projectScope);

    assertThatThrownBy(() -> runService.pageRuns(null, 1, 10))
        .isInstanceOf(ProjectContextException.class);
    assertThatThrownBy(() -> runService.runNow(JOB_ID, "root"))
        .isInstanceOf(ProjectContextException.class);
  }

  private static ScheduleExecutionContext context(Map<?, ?> payload) {
    Instant now = Instant.now();
    Map<String, Object> values = new HashMap<>();
    payload.forEach((key, value) -> values.put(String.valueOf(key), value));
    return new ScheduleExecutionContext(
        "trigger-test",
        new ScheduleKey("yak-ops-metadata", String.valueOf(JOB_ID)),
        "quartz",
        MetadataScheduleEngineBridge.HANDLER,
        values,
        now,
        now,
        false,
        1);
  }

  private static MdCollectJobPO job(boolean enabled) {
    MdCollectJobPO job = new MdCollectJobPO();
    job.setId(JOB_ID);
    job.setProjectId(PROJECT_ID);
    job.setJobCode("harvest-ds7");
    job.setJobName("shop 库采集");
    job.setProviderType(ProviderType.HARVESTED.name());
    job.setDataSourceId(7L);
    job.setCollectColumns(true);
    job.setCronExpression("0 0 3 * * ?");
    job.setEnabled(enabled);
    job.setDeleted(false);
    return job;
  }

  private static MdCollectRunPO runRow() {
    MdCollectRunPO row = new MdCollectRunPO();
    row.setId(RUN_ID);
    row.setJobId(JOB_ID);
    row.setProjectId(PROJECT_ID);
    row.setProviderType(ProviderType.HARVESTED.name());
    row.setTriggerType(TriggerType.SCHEDULE.name());
    row.setDryRun(false);
    row.setStatus(RunStatus.SUCCESS.name());
    row.setCntTotal(3);
    row.setCntNew(1);
    row.setCntChanged(1);
    row.setCntUnchanged(1);
    row.setCntGone(0);
    row.setCntPartialFailed(0);
    row.setStartedAt(LocalDateTime.now().minusMinutes(1));
    row.setFinishedAt(LocalDateTime.now());
    row.setDurationMs(900L);
    row.setCreatedBy("system");
    return row;
  }

  /** 一个对象同时充当"恢复上下文"和"读上下文"，两者共用一份 ThreadLocal 才是真的那一圈。 */
  private static final class ScopedProject
      implements ProjectContextScope, CurrentProject {

    private final ThreadLocal<ProjectContext> installed = new ThreadLocal<>();

    @Override
    public <T> T call(ProjectContext context, Supplier<T> action) {
      ProjectContext previous = installed.get();
      installed.set(context);
      try {
        return action.get();
      } finally {
        if (previous == null) {
          installed.remove();
        } else {
          installed.set(previous);
        }
      }
    }

    @Override
    public Optional<ProjectContext> current() {
      return Optional.ofNullable(installed.get());
    }
  }
}
