package io.yak.ops.business.mdm.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.framework.schedule.api.ScheduleStatus;
import io.yak.ops.business.mdm.dao.mapper.MdmDistributionMapper;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionMode;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionStatus;
import io.yak.ops.business.mdm.dao.model.MdmDistributionPO;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 分发闹钟登记单元测试(R5,review P0-1.8):DAILY/HOURLY 必须真的变成引擎里的定义,
 * 而 MANUAL/停用/未接入通道必须不产生闹钟;引擎缺席时启动绝不能被拖垮。
 */
class MdmDistributionScheduleEngineBridgeTest {

  private static final String NAMESPACE = "yak-ops-mdm";
  private static final long DISTRIBUTION_ID = 10L;
  private static final long PROJECT_ID = 1L;

  private final ScheduleManager manager = mock(ScheduleManager.class);
  private final MdmDistributionMapper mapper = mock(MdmDistributionMapper.class);

  @SuppressWarnings("unchecked")
  private final ObjectProvider<ScheduleManager> provider = mock(ObjectProvider.class);

  private MdmDistributionScheduleEngineBridge bridge;

  @BeforeEach
  void setUp() {
    when(provider.getIfAvailable()).thenReturn(manager);
    bridge = new MdmDistributionScheduleEngineBridge(provider, mapper);
  }

  private static MdmDistributionPO config(
      MdmDistributionMode mode, MdmDistributionStatus status, String frequency) {
    MdmDistributionPO po = new MdmDistributionPO();
    po.setId(DISTRIBUTION_ID);
    po.setProjectId(PROJECT_ID);
    po.setEntityId(1L);
    po.setTargetSystem("CRM");
    po.setDistributeMode(mode.name());
    po.setDistributeFreq(frequency);
    po.setStatus(status.name());
    return po;
  }

  private static ScheduleSnapshot snapshot() {
    return new ScheduleSnapshot(
        null, "quartz", "ext-10", ScheduleStatus.ENABLED, Instant.now(), null);
  }

  @Test
  void dailyApiConfigRegistersAlarmWithBusinessRowAsSourceOfTruth() {
    ScheduleDefinition definition =
        bridge.definition(
            config(MdmDistributionMode.API, MdmDistributionStatus.ACTIVE, "DAILY"));

    assertEquals(new ScheduleKey(NAMESPACE, "10"), definition.key());
    assertEquals("0 0 2 * * ?", definition.trigger().expression());
    assertEquals(ZoneId.systemDefault(), definition.trigger().zoneId());
    assertEquals("mdmDistributionScheduleHandler", definition.target().handler());
    assertEquals(
        java.util.Map.of("projectId", PROJECT_ID, "distributionId", DISTRIBUTION_ID),
        definition.target().payload());
    assertTrue(definition.enabled());
  }

  @Test
  void hourlyUsesItsOwnCronAndManualHasNone() {
    assertEquals(
        "0 0 * * * ?",
        bridge.definition(
                config(MdmDistributionMode.API, MdmDistributionStatus.ACTIVE, "HOURLY"))
            .trigger()
            .expression());
    assertNull(
        MdmDistributionScheduleEngineBridge.cron(
            config(MdmDistributionMode.API, MdmDistributionStatus.ACTIVE, "MANUAL")
                .getDistributeFreq()));
  }

  @Test
  void onlyActiveApiWithFrequencyIsSchedulable() {
    assertTrue(
        MdmDistributionScheduleEngineBridge.schedulable(
            config(MdmDistributionMode.API, MdmDistributionStatus.ACTIVE, "DAILY")));
    assertFalse(
        MdmDistributionScheduleEngineBridge.schedulable(
            config(MdmDistributionMode.API, MdmDistributionStatus.ACTIVE, "MANUAL")));
    assertFalse(
        MdmDistributionScheduleEngineBridge.schedulable(
            config(MdmDistributionMode.API, MdmDistributionStatus.DISABLED, "DAILY")));
    assertFalse(
        MdmDistributionScheduleEngineBridge.schedulable(
            config(MdmDistributionMode.MESSAGE, MdmDistributionStatus.ACTIVE, "DAILY")));
    assertFalse(MdmDistributionScheduleEngineBridge.schedulable(null));
  }

  @Test
  void syncUpsertsCurrentRowSoFrequencyChangeTakesEffect() {
    when(mapper.selectById(DISTRIBUTION_ID))
        .thenReturn(config(MdmDistributionMode.API, MdmDistributionStatus.ACTIVE, "HOURLY"));

    bridge.sync(DISTRIBUTION_ID);

    ArgumentCaptor<ScheduleDefinition> captor = ArgumentCaptor.forClass(ScheduleDefinition.class);
    verify(manager).save(captor.capture());
    assertEquals("0 0 * * * ?", captor.getValue().trigger().expression());
  }

  @Test
  void syncRemovesAlarmOnceConfigStopsBeingSchedulable() {
    when(mapper.selectById(DISTRIBUTION_ID))
        .thenReturn(config(MdmDistributionMode.API, MdmDistributionStatus.DISABLED, "DAILY"));
    when(manager.get(new ScheduleKey(NAMESPACE, "10"))).thenReturn(Optional.of(snapshot()));

    bridge.sync(DISTRIBUTION_ID);

    verify(manager).delete(new ScheduleKey(NAMESPACE, "10"));
    verify(manager, never()).save(any(ScheduleDefinition.class));
  }

  @Test
  void absentEngineKeepsStartupAndWritesSilent() {
    when(provider.getIfAvailable()).thenReturn(null);

    assertFalse(bridge.available());
    bridge.sync(DISTRIBUTION_ID);
    bridge.deleteIfPresent(DISTRIBUTION_ID);
    bridge.registerActiveConfigs();

    verify(manager, never()).save(any(ScheduleDefinition.class));
    verify(manager, never()).delete(any(ScheduleKey.class));
    verify(mapper, never()).selectList(any());
  }

  @Test
  void startupRescanSkipsExistingAlarmsAndSurvivesOneBadRegistration() {
    when(mapper.selectList(any()))
        .thenReturn(
            List.of(
                config(MdmDistributionMode.API, MdmDistributionStatus.ACTIVE, "DAILY"),
                withId(config(MdmDistributionMode.API, MdmDistributionStatus.ACTIVE, "HOURLY"), 11L),
                withId(config(MdmDistributionMode.API, MdmDistributionStatus.ACTIVE, "DAILY"), 12L)));
    when(manager.get(new ScheduleKey(NAMESPACE, "10"))).thenReturn(Optional.of(snapshot()));
    when(manager.save(any(ScheduleDefinition.class)))
        .thenThrow(new IllegalStateException("cron 无法解析"))
        .thenAnswer(call -> snapshot());

    bridge.registerActiveConfigs();

    // 10 号引擎里已有定义→跳过;11 号登记抛错只 warn;12 号照常登记——坏配置不连坐。
    ArgumentCaptor<ScheduleDefinition> captor = ArgumentCaptor.forClass(ScheduleDefinition.class);
    verify(manager, times(2)).save(captor.capture());
    assertEquals(new ScheduleKey(NAMESPACE, "11"), captor.getAllValues().get(0).key());
    assertEquals(new ScheduleKey(NAMESPACE, "12"), captor.getAllValues().get(1).key());
  }

  private static MdmDistributionPO withId(MdmDistributionPO po, long id) {
    po.setId(id);
    return po;
  }
}
