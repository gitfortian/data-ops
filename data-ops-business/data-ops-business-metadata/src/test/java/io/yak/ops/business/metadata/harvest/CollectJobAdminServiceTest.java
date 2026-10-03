package io.yak.ops.business.metadata.harvest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metadata.dao.mapper.MdCollectJobMapper;
import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.business.metadata.harvest.CollectJobAdminService.JobView;
import io.yak.ops.business.metadata.harvest.CollectJobAdminService.UpsertCommand;
import io.yak.ops.business.metadata.schedule.MetadataScheduleEngineBridge;
import io.yak.ops.business.metadata.dao.model.MdCollectJobPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.ProviderType;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

/**
 * 任务读写与启用闸门（ticket 116）。
 *
 * <p>这里验的是<b>顺序</b>而不是形状：新建必须停用、启用必须先预演、改作用域要把预演打回去、闹钟跟着
 * 启用状态走。这四条一旦能被"顺手改一下"绕过，ticket 122 那个页面就变成了一个未经预演就能定时改
 * 目录的入口——而采集是唯一会造成不可逆损失的写路径。
 *
 * <p>不连库、不起 Spring：任务表用记参数的假 mapper，闹钟用假 bridge，于是"启用了却没登记闹钟"
 * 这种只在现网表现为"它从来不跑"的错，在这里就会变红。
 */
class CollectJobAdminServiceTest {

  private static final long PROJECT_ID = 42L;
  private static final long JOB_ID = 9L;

  private final MdCollectJobMapper jobMapper = mock(MdCollectJobMapper.class);
  private final MetadataScheduleEngineBridge bridge = mock(MetadataScheduleEngineBridge.class);
  private final CurrentProject currentProject =
      () -> Optional.of(new ProjectContext(PROJECT_ID, null));

  private CollectJobAdminService service;

  @BeforeEach
  void wireService() {
    service = new CollectJobAdminService(jobMapper, currentProject, bridge);
    when(bridge.available()).thenReturn(true);
    // 撞码查重默认全空：只有专门测撞码的用例覆写这里
    when(jobMapper.selectList(any())).thenReturn(List.of());
  }

  @Test
  void aNewJobAlwaysStartsDisabledAndUnrehearsed() {
    service.create(command().build(), "root");

    MdCollectJobPO stored = capturedInsert();
    assertThat(stored.getEnabled()).isFalse();
    assertThat(stored.getDryRunPassed()).isFalse();
    assertThat(stored.getProjectId()).isEqualTo(PROJECT_ID);
    assertThat(stored.getCreatedBy()).isEqualTo("root");
    assertThat(stored.getDeleted()).isFalse();
    // 能默认就不留空：编码由通道与数据源拼出，人不必填
    assertThat(stored.getJobCode()).isEqualTo("harvest-ds7");
    assertThat(stored.getCronExpression()).isEqualTo(CollectJobAdminService.DEFAULT_CRON);
  }

  /** 一期默认采到列级：不采列必须是一个明确决定，而不是"表单里忘了勾"。 */
  @Test
  void columnsAreCollectedUnlessTheFormSaysOtherwise() {
    service.create(command().build(), "root");
    service.create(command().collectColumns(false).build(), "root");

    ArgumentCaptor<MdCollectJobPO> stored = ArgumentCaptor.forClass(MdCollectJobPO.class);
    verify(jobMapper, times(2)).insert(stored.capture());
    assertThat(stored.getAllValues().get(0).getCollectColumns()).isTrue();
    assertThat(stored.getAllValues().get(1).getCollectColumns()).isFalse();
  }

  @Test
  void aReconcileJobKeepsItsTypeAndDropsEveryPhysicalField() {
    service.create(
        command().providerType("REGISTERED").typeName("table").databaseName("shop").build(),
        "root");

    MdCollectJobPO stored = capturedInsert();
    assertThat(stored.getTypeName()).isEqualTo("table");
    assertThat(stored.getJobCode()).isEqualTo("reconcile-table");
    // 留着物理字段，ticket 135 会照着它们再采一遍不该采的东西
    assertThat(stored.getDataSourceId()).isNull();
    assertThat(stored.getDatabaseName()).isNull();
    assertThat(stored.getTablePattern()).isNull();
  }

  @Test
  void aHarvestJobWithoutDataSourceHasNoScopeToCollect() {
    assertCode(
        () -> service.create(command().dataSourceId(null).build(), "root"),
        MetadataErrorCode.COLLECT_JOB_SCOPE_INVALID);
    verify(jobMapper, never()).insert(any(MdCollectJobPO.class));
  }

  @Test
  void aReconcileJobWithoutTypeHasNothingToReconcile() {
    assertCode(
        () -> service.create(command().providerType("REGISTERED").typeName(null).build(), "root"),
        MetadataErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void aTooShortCronIsRejectedWithTheShapeItExpected() {
    assertCode(
        () -> service.create(command().cronExpression("0 3 * *").build(), "root"),
        MetadataErrorCode.INVALID_ARGUMENT);
    // 7 段（带年）同样合法：不能把合法的表达式挡在门外
    service.create(command().cronExpression("0 0 3 * * ? 2099").build(), "root");
    assertThat(capturedInsert().getCronExpression()).isEqualTo("0 0 3 * * ? 2099");
  }

  @Test
  void breakerKnobsOutsideTheirDocumentedRangeAreRejected() {
    assertCode(
        () -> service.create(command().collapseThresholdPct(0).build(), "root"),
        MetadataErrorCode.INVALID_ARGUMENT);
    assertCode(
        () -> service.create(command().missingRounds(6).build(), "root"),
        MetadataErrorCode.INVALID_ARGUMENT);

    service.create(command().build(), "root");
    MdCollectJobPO stored = capturedInsert();
    assertThat(stored.getCollapseThresholdPct()).isEqualTo(30);
    assertThat(stored.getMissingRounds()).isEqualTo(2);
  }

  @Test
  void aClashingGeneratedCodeIsNumberedInsteadOfRefused() {
    when(jobMapper.selectList(any())).thenReturn(List.of(taken("harvest-ds7")), List.of());

    JobView view = service.create(command().build(), "root");

    // 自动生成撞了加序号即可：编码是给人看的地址，不是身份
    assertThat(view.jobCode()).isEqualTo("harvest-ds7-2");
    assertThat(capturedInsert().getJobCode()).isEqualTo("harvest-ds7-2");
  }

  @Test
  void anExplicitlyChosenCodeNeverGetsRewritten() {
    when(jobMapper.selectList(any())).thenReturn(List.of(taken("nightly-shop")));

    assertCode(
        () -> service.create(command().jobCode("nightly-shop").build(), "root"),
        MetadataErrorCode.PERSISTENCE_CONFLICT);
    verify(jobMapper, never()).insert(any(MdCollectJobPO.class));
  }

  /** 查重与写入之间总有并发窗口，唯一索引才是真守门人——它报错时必须翻成可读的冲突。 */
  @Test
  void aDuplicateKeyFromTheUniqueIndexBecomesAReadableConflict() {
    when(jobMapper.insert(any(MdCollectJobPO.class)))
        .thenThrow(new DuplicateKeyException("uk_project_job_code"));

    assertCode(
        () -> service.create(command().build(), "root"), MetadataErrorCode.PERSISTENCE_CONFLICT);
  }

  @Test
  void enablingRequiresAPassingRehearsal() {
    when(jobMapper.selectOne(any())).thenReturn(job(true, false));

    assertCode(
        () -> service.changeEnabled(JOB_ID, true, "root"), MetadataErrorCode.DRY_RUN_REQUIRED);
    verify(jobMapper, never()).updateById(any(MdCollectJobPO.class));
    verify(bridge, never()).register(any(MdCollectJobPO.class));
  }

  @Test
  void enablingRegistersAndResumesTheAlarm() {
    when(jobMapper.selectOne(any())).thenReturn(job(true, true));

    JobView enabled = service.changeEnabled(JOB_ID, true, "root");

    assertThat(enabled.enabled()).isTrue();
    ArgumentCaptor<MdCollectJobPO> alarm = ArgumentCaptor.forClass(MdCollectJobPO.class);
    verify(bridge).register(alarm.capture());
    assertThat(alarm.getValue().getEnabled()).isTrue();
    verify(bridge).resumeIfPresent(JOB_ID);
    // 登记存的是 enabled=true 的定义，再显式 resume 一次：闹钟可能本来就存在且处于暂停态
    verify(bridge, never()).pauseIfPresent(JOB_ID);
  }

  @Test
  void disablingOnlyPauses() {
    when(jobMapper.selectOne(any())).thenReturn(job(true, true));

    JobView disabled = service.changeEnabled(JOB_ID, false, "root");

    assertThat(disabled.enabled()).isFalse();
    verify(bridge).pauseIfPresent(JOB_ID);
    verify(bridge, never()).register(any(MdCollectJobPO.class));
    // 停用不该顺手抹掉预演结论：它是"这个作用域跑通过"的历史事实
    assertThat(capturedUpdate().getDryRunPassed()).isTrue();
  }

  /**
   * 调度器缺席时启用只留一条警告。
   *
   * <p>不能因为"没闹钟"就拒绝启用：本平台 Quartz 是内存存储、{@code yak.schedule.enabled=false} 是
   * 常态，拒绝会让用户在开发环境里连任务都建不出来。手工触发仍是通道。
   */
  @Test
  void enablingWithoutASchedulerStillSucceeds() {
    when(bridge.available()).thenReturn(false);
    when(jobMapper.selectOne(any())).thenReturn(job(true, true));

    assertThat(service.changeEnabled(JOB_ID, true, "root").enabled()).isTrue();
    verify(bridge).register(any(MdCollectJobPO.class));
  }

  @Test
  void narrowingTheScopeInvalidatesThePreviousRehearsal() {
    MdCollectJobPO existing = job(true, true);
    existing.setDatabaseName("shop");
    when(jobMapper.selectOne(any())).thenReturn(existing);

    JobView view =
        service.update(JOB_ID, command().databaseName("shop_v2").build(), "root");

    // 作用域是 ticket 115 判"谁有缺席资格"的输入，改过之后旧预演描述的已不是这件事
    assertThat(view.dryRunPassed()).isFalse();
    assertThat(capturedUpdate().getDatabaseName()).isEqualTo("shop_v2");
  }

  @Test
  void renamingOrReschedulingKeepsTheRehearsal() {
    when(jobMapper.selectOne(any())).thenReturn(job(true, true));

    JobView view =
        service.update(
            JOB_ID, command().jobName("改名后的采集").cronExpression("0 30 4 * * ?").build(), "root");

    assertThat(view.dryRunPassed()).isTrue();
    assertThat(capturedUpdate().getJobName()).isEqualTo("改名后的采集");
    assertThat(capturedUpdate().getCronExpression()).isEqualTo("0 30 4 * * ?");
  }

  /** 改完仍在启用的任务要重存闹钟：{@code cron_expression} 可改，跳过就等于新时刻永不生效。 */
  @Test
  void updatingAnEnabledJobRewritesItsAlarm() {
    when(jobMapper.selectOne(any())).thenReturn(job(true, true));

    service.update(JOB_ID, command().cronExpression("0 30 4 * * ?").build(), "root");

    verify(bridge).register(any(MdCollectJobPO.class));
  }

  @Test
  void updatingADisabledJobLeavesTheAlarmsAlone() {
    when(jobMapper.selectOne(any())).thenReturn(job(false, true));

    service.update(JOB_ID, command().build(), "root");

    verify(bridge, never()).register(any(MdCollectJobPO.class));
  }

  @Test
  void theChannelOfAnExistingJobCannotBeSwitched() {
    when(jobMapper.selectOne(any())).thenReturn(job(false, true));

    assertCode(
        () -> service.update(JOB_ID, command().providerType("REGISTERED").build(), "root"),
        MetadataErrorCode.INVALID_ARGUMENT);
    verify(jobMapper, never()).updateById(any(MdCollectJobPO.class));
  }

  /** PUT 是整行替换：通道决定这一行由谁执行，缺了就报错，不猜。 */
  @Test
  void anUpdateMustNameItsChannel() {
    when(jobMapper.selectOne(any())).thenReturn(job(false, true));

    assertCode(
        () -> service.update(JOB_ID, command().providerType(null).build(), "root"),
        MetadataErrorCode.INVALID_ARGUMENT);
  }

  /** 整行替换的唯一例外：编码留空表示"不改"，因为它是身份不是内容。 */
  @Test
  void anUpdateThatOmitsTheCodeKeepsIt() {
    when(jobMapper.selectOne(any())).thenReturn(job(false, true));

    assertThat(service.update(JOB_ID, command().jobCode("  ").build(), "root").jobCode())
        .isEqualTo("harvest-ds7");
  }

  @Test
  void deletingRevokesTheAlarmAndKeepsEveryBitOfHistory() {
    when(jobMapper.selectOne(any())).thenReturn(job(true, true));

    service.delete(JOB_ID, "root");

    MdCollectJobPO stored = capturedUpdate();
    assertThat(stored.getDeleted()).isTrue();
    assertThat(stored.getEnabled()).isTrue();
    assertThat(stored.getLastRunId()).isEqualTo(5L);
    verify(bridge).deleteIfPresent(JOB_ID);
    // 运行历史与变更流水是"这批目录行为什么长成这样"的唯一答案
    verify(jobMapper, never()).delete(any());
    verify(jobMapper, never()).deleteById(any(MdCollectJobPO.class));
  }

  @Test
  void aSoftDeletedOrForeignJobIsMissingOnEveryRead() {
    when(jobMapper.selectOne(any())).thenReturn(null);

    assertCode(() -> service.get(JOB_ID), MetadataErrorCode.COLLECT_JOB_NOT_FOUND);
    assertCode(
        () -> service.changeEnabled(JOB_ID, true, "root"), MetadataErrorCode.COLLECT_JOB_NOT_FOUND);
    assertCode(() -> service.delete(JOB_ID, "root"), MetadataErrorCode.COLLECT_JOB_NOT_FOUND);
  }

  private MdCollectJobPO capturedInsert() {
    ArgumentCaptor<MdCollectJobPO> captor = ArgumentCaptor.forClass(MdCollectJobPO.class);
    verify(jobMapper).insert(captor.capture());
    return captor.getValue();
  }

  private MdCollectJobPO capturedUpdate() {
    ArgumentCaptor<MdCollectJobPO> captor = ArgumentCaptor.forClass(MdCollectJobPO.class);
    verify(jobMapper).updateById(captor.capture());
    return captor.getValue();
  }

  private static void assertCode(ThrowingCallable call, MetadataErrorCode expected) {
    assertThatThrownBy(call)
        .isInstanceOf(MetadataException.class)
        .extracting(e -> ((MetadataException) e).getErrorCode())
        .isEqualTo(expected);
  }

  private static MdCollectJobPO job(boolean enabled, boolean dryRunPassed) {
    MdCollectJobPO po = new MdCollectJobPO();
    po.setId(JOB_ID);
    po.setProjectId(PROJECT_ID);
    po.setJobCode("harvest-ds7");
    po.setJobName("shop 库采集");
    po.setProviderType(ProviderType.HARVESTED.name());
    po.setDataSourceId(7L);
    po.setCollectColumns(true);
    po.setCronExpression(CollectJobAdminService.DEFAULT_CRON);
    po.setEnabled(enabled);
    po.setDryRunPassed(dryRunPassed);
    po.setCollapseThresholdPct(30);
    po.setMissingRounds(2);
    po.setLastRunId(5L);
    po.setDeleted(false);
    return po;
  }

  private static MdCollectJobPO taken(String jobCode) {
    MdCollectJobPO po = job(false, false);
    po.setId(99L);
    po.setJobCode(jobCode);
    return po;
  }

  private static CommandBuilder command() {
    return new CommandBuilder();
  }

  /** 命令有 12 个字段，逐个 new 会让"改了哪一项"这件事淹没在参数里。 */
  private static final class CommandBuilder {

    private String jobCode;
    private String jobName = "shop 库采集";
    private String providerType = ProviderType.HARVESTED.name();
    private String typeName;
    private Long dataSourceId = 7L;
    private String databaseName;
    private String schemaName;
    private String tablePattern;
    private Boolean collectColumns;
    private String cronExpression;
    private Integer collapseThresholdPct;
    private Integer missingRounds;

    CommandBuilder jobCode(String value) {
      jobCode = value;
      return this;
    }

    CommandBuilder jobName(String value) {
      jobName = value;
      return this;
    }

    CommandBuilder providerType(String value) {
      providerType = value;
      return this;
    }

    CommandBuilder typeName(String value) {
      typeName = value;
      return this;
    }

    CommandBuilder dataSourceId(Long value) {
      dataSourceId = value;
      return this;
    }

    CommandBuilder databaseName(String value) {
      databaseName = value;
      return this;
    }

    CommandBuilder collectColumns(Boolean value) {
      collectColumns = value;
      return this;
    }

    CommandBuilder cronExpression(String value) {
      cronExpression = value;
      return this;
    }

    CommandBuilder collapseThresholdPct(Integer value) {
      collapseThresholdPct = value;
      return this;
    }

    CommandBuilder missingRounds(Integer value) {
      missingRounds = value;
      return this;
    }

    UpsertCommand build() {
      return new UpsertCommand(
          jobCode,
          jobName,
          providerType,
          typeName,
          dataSourceId,
          databaseName,
          schemaName,
          tablePattern,
          collectColumns,
          cronExpression,
          collapseThresholdPct,
          missingRounds);
    }
  }
}
