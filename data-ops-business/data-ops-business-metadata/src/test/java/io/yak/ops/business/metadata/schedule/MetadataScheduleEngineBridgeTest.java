package io.yak.ops.business.metadata.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.schedule.api.ConcurrencyPolicy;
import io.yak.framework.schedule.api.MisfirePolicy;
import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.framework.schedule.api.ScheduleStatus;
import io.yak.ops.business.metadata.dao.mapper.MdCollectJobMapper;
import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.common.bean.po.metadata.MdCollectJobPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.ProviderType;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 采集闹钟的登记形状（ticket 116，plan §3.7）。
 *
 * <p>只验 bridge 自己那一段：引擎缺席时静默、对账通道不登记、启动补齐幂等、单个坏 cron 不连坐。
 * 这四条共同的取向是<b>闹钟不能反过来伤害采集</b>——一次登记失败最多让一个任务不按时跑，
 * 而一个装反了的 shape（namespace 错、payload 少了 projectId）会让每一轮定时都在最深处抛异常。
 */
class MetadataScheduleEngineBridgeTest {

  private static final String NAMESPACE = "yak-ops-metadata";
  private static final long JOB_ID = 9L;
  private static final long PROJECT_ID = 42L;

  private final ScheduleManager manager = mock(ScheduleManager.class);
  private final MdCollectJobMapper jobMapper = mock(MdCollectJobMapper.class);
  private final ObjectProvider<ScheduleManager> provider = mock(ObjectProvider.class);

  private MetadataScheduleEngineBridge bridge;

  @BeforeEach
  void wireBridge() {
    bridge = new MetadataScheduleEngineBridge(provider, jobMapper);
    when(provider.getIfAvailable()).thenReturn(manager);
  }

  @Test
  void theAlarmCarriesOnlyWhereToLookNotWhatToCollect() {
    ScheduleDefinition definition = bridge.definition(job(ProviderType.HARVESTED));

    assertThat(definition.key().namespace()).isEqualTo(NAMESPACE);
    assertThat(definition.key().name()).isEqualTo("9");
    assertThat(definition.trigger().expression()).isEqualTo("0 0 3 * * ?");
    assertThat(definition.trigger().zoneId()).isEqualTo(ZoneId.systemDefault());
    assertThat(definition.target().handler()).isEqualTo("metadataCollectScheduleHandler");
    assertThat(definition.target().payload())
        .containsEntry("projectId", PROJECT_ID)
        .containsEntry("jobId", JOB_ID);
    assertThat(definition.metadata())
        .containsEntry("source", "yak-ops")
        .containsEntry("providerType", "HARVESTED");
    assertThat(definition.policy().concurrencyPolicy()).isEqualTo(ConcurrencyPolicy.FORBID);
    assertThat(definition.policy().misfirePolicy()).isEqualTo(MisfirePolicy.FIRE_ONCE_NOW);
    assertThat(definition.enabled()).isTrue();
  }

  /** 闹钟只负责"到点来看一眼"：作用域留在任务行上，事实源只有一份。 */
  @Test
  void theAlarmDoesNotSnapshotTheScope() {
    MdCollectJobPO job = job(ProviderType.HARVESTED);
    job.setDatabaseName("shop");
    job.setTablePattern("ord_*");

    assertThat(bridge.definition(job).target().payload()).containsOnlyKeys("projectId", "jobId");
  }

  @Test
  void anAbsentSchedulerLeavesRegistrationSilent() {
    when(provider.getIfAvailable()).thenReturn(null);

    assertThat(bridge.available()).isFalse();
    bridge.register(job(ProviderType.HARVESTED));
    bridge.pauseIfPresent(JOB_ID);
    bridge.resumeIfPresent(JOB_ID);
    bridge.deleteIfPresent(JOB_ID);
    bridge.registerEnabledJobs();
    // 到这里为止一条引擎调用都没有：启动绝不能因为"没闹钟"而起不来
    verify(manager, never()).save(any(ScheduleDefinition.class));
    verify(jobMapper, never()).selectList(any());
  }

  @Test
  void registerStoresTheDefinitionUnderTheJobsOwnKey() {
    bridge.register(job(ProviderType.HARVESTED));

    ArgumentCaptor<ScheduleDefinition> captor = ArgumentCaptor.forClass(ScheduleDefinition.class);
    verify(manager).save(captor.capture());
    assertThat(captor.getValue().key()).isEqualTo(new ScheduleKey(NAMESPACE, "9"));
  }

  /**
   * 对账通道今天跑不了（执行体是 ticket 135）。现在就给它登记闹钟，等于每天定时撞进一条
   * "通道未落地"的失败，把一个排期事实伪装成运行故障。
   */
  @Test
  void aReconcileJobGetsNoAlarmYet() {
    bridge.register(job(ProviderType.REGISTERED));

    verify(manager, never()).save(any(ScheduleDefinition.class));
  }

  @Test
  void lifecycleCallsOnlyTouchAlarmsThatActuallyExist() {
    when(manager.get(new ScheduleKey(NAMESPACE, "9"))).thenReturn(Optional.empty());
    bridge.pauseIfPresent(JOB_ID);
    bridge.resumeIfPresent(JOB_ID);
    bridge.deleteIfPresent(JOB_ID);
    verify(manager, never()).pause(any(ScheduleKey.class));
    verify(manager, never()).resume(any(ScheduleKey.class));
    verify(manager, never()).delete(any(ScheduleKey.class));

    when(manager.get(new ScheduleKey(NAMESPACE, "9"))).thenReturn(Optional.of(snapshot()));
    bridge.pauseIfPresent(JOB_ID);
    bridge.resumeIfPresent(JOB_ID);
    bridge.deleteIfPresent(JOB_ID);
    verify(manager).pause(new ScheduleKey(NAMESPACE, "9"));
    verify(manager).resume(new ScheduleKey(NAMESPACE, "9"));
    verify(manager).delete(new ScheduleKey(NAMESPACE, "9"));
  }

  /** 没有 id 的任务行不该拼出一个 {@code name=null} 的闹钟键。 */
  @Test
  void aJobWithoutAnIdIsLeftAlone() {
    bridge.pauseIfPresent(null);
    bridge.deleteIfPresent(null);
    bridge.register(null);

    verify(manager, never()).pause(any(ScheduleKey.class));
    verify(manager, never()).delete(any(ScheduleKey.class));
  }

  @Test
  void startupRebuildScansEveryProjectAndSkipsExistingAlarms() {
    when(jobMapper.selectList(any()))
        .thenReturn(
            List.of(
                job(ProviderType.HARVESTED),
                withId(job(ProviderType.HARVESTED), 10L),
                withId(job(ProviderType.REGISTERED), 11L)));
    when(manager.get(new ScheduleKey(NAMESPACE, "9"))).thenReturn(Optional.of(snapshot()));

    bridge.registerEnabledJobs();

    // 内存存储重启后既不补跑也不保留登记，"任务行是启用状态、引擎里没闹钟"是重启后的默认现场。
    // 三个任务里只该存一次：9 号已有定义，11 号是还没落地的对账通道。
    ArgumentCaptor<ScheduleDefinition> captor = ArgumentCaptor.forClass(ScheduleDefinition.class);
    verify(manager, times(1)).save(captor.capture());
    assertThat(captor.getValue().key()).isEqualTo(new ScheduleKey(NAMESPACE, "10"));
  }

  /** 单个任务登记失败只跳过它自己：一个坏 cron 不该让其余任务的闹钟都不再登记。 */
  @Test
  void oneBadCronDoesNotStopTheRestOfTheRoster() {
    when(jobMapper.selectList(any()))
        .thenReturn(
            List.of(job(ProviderType.HARVESTED), withId(job(ProviderType.HARVESTED), 10L)));
    when(manager.get(any(ScheduleKey.class))).thenReturn(Optional.empty());
    when(manager.save(any(ScheduleDefinition.class)))
        .thenThrow(new MetadataException(MetadataErrorCode.INVALID_ARGUMENT, "cron 无法解析"))
        .thenAnswer(call -> snapshot());

    bridge.registerEnabledJobs();

    verify(manager, times(2)).save(any(ScheduleDefinition.class));
  }

  private static ScheduleSnapshot snapshot() {
    return new ScheduleSnapshot(
        null, "quartz", "ext-9", ScheduleStatus.ENABLED, Instant.now(), null);
  }

  private static MdCollectJobPO job(ProviderType provider) {
    MdCollectJobPO job = new MdCollectJobPO();
    job.setId(JOB_ID);
    job.setProjectId(PROJECT_ID);
    job.setJobCode("harvest-ds7");
    job.setJobName("shop 库采集");
    job.setProviderType(provider.name());
    job.setDataSourceId(7L);
    job.setCronExpression("0 0 3 * * ?");
    job.setEnabled(true);
    job.setDeleted(false);
    return job;
  }

  private static MdCollectJobPO withId(MdCollectJobPO job, long id) {
    job.setId(id);
    return job;
  }
}
