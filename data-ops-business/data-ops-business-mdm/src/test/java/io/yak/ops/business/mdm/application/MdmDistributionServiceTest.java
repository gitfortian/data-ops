package io.yak.ops.business.mdm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.dataservice.query.DataServiceView;
import io.yak.ops.business.mdm.domain.distribution.MdmDistribution;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionMode;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionStatus;
import io.yak.ops.business.mdm.domain.record.MdmRecordStatus;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmDistributionRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmRecordRepository;
import io.yak.ops.business.mdm.notification.MdmNotifier;
import io.yak.ops.business.mdm.schedule.MdmDistributionScheduleEngineBridge;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 主数据分发服务单元测试(ticket 58 + R5:执行=真实发布,未接入通道明确失败)。 */
class MdmDistributionServiceTest {

  private MdmDistributionRepository repository;
  private MdmEntityService entityService;
  private BusinessAuditService auditService;
  private MdmDistributionPublishService publishService;
  private MdmRecordRepository recordRepository;
  private MdmDistributionScheduleEngineBridge scheduleBridge;
  private MdmNotifier notifier;
  private MdmDistributionService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(MdmDistributionRepository.class);
    entityService = Mockito.mock(MdmEntityService.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    publishService = Mockito.mock(MdmDistributionPublishService.class);
    recordRepository = Mockito.mock(MdmRecordRepository.class);
    scheduleBridge = Mockito.mock(MdmDistributionScheduleEngineBridge.class);
    notifier = Mockito.mock(MdmNotifier.class);
    AuditOperationHandle audit = Mockito.mock(AuditOperationHandle.class);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    lenient().when(entityService.get(any())).thenReturn(null);
    service =
        new MdmDistributionService(
            repository, entityService, auditService, publishService, recordRepository,
            scheduleBridge, notifier);
  }

  private static MdmDistribution distribution(
      Long id, Long entityId, String target, MdmDistributionMode mode,
      MdmDistributionStatus status) {
    return new MdmDistribution(
        id, entityId, target, target + " System", mode, "MANUAL", "FULL", status,
        null, 0, 0, "tester", LocalDateTime.now(), LocalDateTime.now());
  }

  // ==== CRUD ====

  @Test
  void createInsertsDistribution() {
    when(repository.existsByTarget(1L, "CRM", "API", null)).thenReturn(false);
    when(repository.insert(any(MdmDistribution.class), any()))
        .thenAnswer(
            invocation -> {
              MdmDistribution d = invocation.getArgument(0);
              return d.withPersisted(10L, "tester", LocalDateTime.now());
            });

    MdmDistribution created =
        service.create(1L, "CRM", "CRM 系统", MdmDistributionMode.API, "MANUAL", "FULL", "tester");

    assertEquals(10L, created.id());
    assertEquals(MdmDistributionStatus.DRAFT, created.status());
    verify(repository).insert(any(MdmDistribution.class), any());
  }

  @Test
  void createRejectsDuplicateTarget() {
    when(repository.existsByTarget(1L, "CRM", "API", null)).thenReturn(true);

    MdmException exception =
        assertThrows(
            MdmException.class,
            () ->
                service.create(
                    1L, "CRM", "CRM", MdmDistributionMode.API, null, null, "tester"));
    assertEquals(MdmErrorCode.DISTRIBUTE_FAILED, exception.getErrorCode());
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void createRejectsEmptyTargetSystem() {
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.create(1L, "", "CRM", MdmDistributionMode.API, null, null, "tester"));
    assertEquals(MdmErrorCode.DISTRIBUTE_FAILED, exception.getErrorCode());
  }

  @Test
  void createRejectsNullMode() {
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.create(1L, "CRM", "CRM", null, null, null, "tester"));
    assertEquals(MdmErrorCode.DISTRIBUTE_FAILED, exception.getErrorCode());
  }

  /** 频率决定闹钟存不存在:P0-1.8 要求错值在入口就拒,而不是静默不调度。 */
  @Test
  void createRejectsUnknownFrequency() {
    MdmException exception =
        assertThrows(
            MdmException.class,
            () ->
                service.create(
                    1L, "CRM", "CRM", MdmDistributionMode.API, "WEEKLY", "FULL", "tester"));
    assertEquals(MdmErrorCode.DISTRIBUTE_FAILED, exception.getErrorCode());
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void updateRejectsUnknownFrequency() {
    when(repository.findById(10L))
        .thenReturn(
            Optional.of(
                distribution(10L, 1L, "CRM", MdmDistributionMode.API, MdmDistributionStatus.DRAFT)));

    MdmException exception =
        assertThrows(MdmException.class, () -> service.update(10L, null, null, "每分钟", null));
    assertEquals(MdmErrorCode.DISTRIBUTE_FAILED, exception.getErrorCode());
    verify(repository, never()).update(any());
  }

  @Test
  void listByEntityReturnsAll() {
    when(repository.listByEntity(1L))
        .thenReturn(
            List.of(
                distribution(1L, 1L, "CRM", MdmDistributionMode.API, MdmDistributionStatus.ACTIVE),
                distribution(2L, 1L, "ERP", MdmDistributionMode.FILE, MdmDistributionStatus.DRAFT)));

    List<MdmDistribution> list = service.listByEntity(1L);
    assertEquals(2, list.size());
  }

  // ==== 状态流转 ====

  @Test
  void setStatusDraftToActive() {
    MdmDistribution config =
        distribution(10L, 1L, "CRM", MdmDistributionMode.API, MdmDistributionStatus.DRAFT);
    when(repository.findById(10L)).thenReturn(Optional.of(config));
    when(repository.update(any())).thenReturn(true);

    service.setStatus(10L, MdmDistributionStatus.ACTIVE);

    verify(repository).update(any());
    verify(scheduleBridge).sync(10L);
  }

  @Test
  void setStatusRejectsInvalidTransition() {
    MdmDistribution config =
        distribution(10L, 1L, "CRM", MdmDistributionMode.API, MdmDistributionStatus.DRAFT);
    when(repository.findById(10L)).thenReturn(Optional.of(config));

    MdmException exception =
        assertThrows(
            MdmException.class, () -> service.setStatus(10L, MdmDistributionStatus.DISABLED));
    assertEquals(MdmErrorCode.DISTRIBUTE_FAILED, exception.getErrorCode());
    verify(repository, never()).update(any());
    verify(scheduleBridge, never()).sync(any());
  }

  @Test
  void updateReschedulesAlarmSoFrequencyChangeTakesEffect() {
    MdmDistribution config =
        distribution(10L, 1L, "CRM", MdmDistributionMode.API, MdmDistributionStatus.ACTIVE);
    when(repository.findById(10L)).thenReturn(Optional.of(config));
    when(repository.update(any())).thenReturn(true);

    service.update(10L, "CRM 系统", MdmDistributionMode.API, "DAILY", "FULL");

    verify(repository).update(any());
    verify(scheduleBridge).sync(10L);
  }

  // ==== 分发执行(R5) ====

  @Test
  void executePublishesApiAndRecordsServableCount() {
    MdmDistribution config =
        distribution(10L, 1L, "CRM", MdmDistributionMode.API, MdmDistributionStatus.ACTIVE);
    DataServiceView api = mock(DataServiceView.class);
    when(api.id()).thenReturn(66L);
    when(api.path()).thenReturn("/mdm/customer/10");
    when(repository.findById(10L)).thenReturn(Optional.of(config));
    when(publishService.online(config))
        .thenReturn(new MdmDistributionPublishService.PublishOutcome(api, true));
    when(recordRepository.countByEntity(1L, MdmRecordStatus.ACTIVE)).thenReturn(3060L);
    when(repository.updateResult(any())).thenReturn(true);

    MdmDistributionService.DistributionResult result = service.execute(10L, "tester");

    assertNotNull(result);
    assertEquals(10L, result.id());
    assertEquals("API", result.mode());
    assertEquals(3060, result.count());
    assertEquals(0, result.failCount());
    assertEquals(66L, result.apiId());
    assertEquals("/mdm/customer/10", result.apiPath());
    verify(repository).updateResult(any());
    verify(notifier).distributionPublished(1L, "CRM", 3060, "/mdm/customer/10");
  }

  /** 定时纯刷新(未真的发布/重发布)不发站内信:否则每小时一条噪声会把通知淹掉。 */
  @Test
  void executeWithoutRepublishKeepsSubscribersSilent() {
    MdmDistribution config =
        distribution(10L, 1L, "CRM", MdmDistributionMode.API, MdmDistributionStatus.ACTIVE);
    DataServiceView api = mock(DataServiceView.class);
    when(api.id()).thenReturn(66L);
    when(api.path()).thenReturn("/mdm/customer/10");
    when(repository.findById(10L)).thenReturn(Optional.of(config));
    when(publishService.online(config))
        .thenReturn(new MdmDistributionPublishService.PublishOutcome(api, false));
    when(recordRepository.countByEntity(1L, MdmRecordStatus.ACTIVE)).thenReturn(3060L);
    when(repository.updateResult(any())).thenReturn(true);

    service.execute(10L, null);

    verify(repository).updateResult(any());
    verify(notifier, never()).distributionPublished(any(), any(), Mockito.anyInt(), any());
  }

  /** 未接入通道绝不落"成功时间戳":假成功比明确失败更危险(P0-1.8)。 */
  @Test
  void executeRejectsUnsupportedChannelWithoutWritingResult() {
    MdmDistribution config =
        distribution(10L, 1L, "CRM", MdmDistributionMode.MESSAGE, MdmDistributionStatus.ACTIVE);
    when(repository.findById(10L)).thenReturn(Optional.of(config));

    MdmException exception =
        assertThrows(MdmException.class, () -> service.execute(10L, "tester"));

    assertEquals(MdmErrorCode.DISTRIBUTE_FAILED, exception.getErrorCode());
    verify(repository, never()).updateResult(any());
    verify(publishService, never()).online(any());
  }

  @Test
  void executePropagatesPublishFailureWithoutWritingResult() {
    MdmDistribution config =
        distribution(10L, 1L, "CRM", MdmDistributionMode.API, MdmDistributionStatus.ACTIVE);
    when(repository.findById(10L)).thenReturn(Optional.of(config));
    when(publishService.online(config))
        .thenThrow(new MdmException(MdmErrorCode.DATA_SERVICE_DISABLED, "数据服务模块未启用"));

    MdmException exception =
        assertThrows(MdmException.class, () -> service.execute(10L, "tester"));

    assertEquals(MdmErrorCode.DATA_SERVICE_DISABLED, exception.getErrorCode());
    verify(repository, never()).updateResult(any());
  }

  @Test
  void executeRejectsNonActiveConfig() {
    MdmDistribution config =
        distribution(10L, 1L, "CRM", MdmDistributionMode.API, MdmDistributionStatus.DRAFT);
    when(repository.findById(10L)).thenReturn(Optional.of(config));

    MdmException exception = assertThrows(MdmException.class, () -> service.execute(10L, "tester"));
    assertEquals(MdmErrorCode.DISTRIBUTE_FAILED, exception.getErrorCode());
    verify(repository, never()).updateResult(any());
  }

  // ==== 删除 ====

  @Test
  void deleteRemovesConfigAndAlarm() {
    MdmDistribution config =
        distribution(10L, 1L, "CRM", MdmDistributionMode.API, MdmDistributionStatus.DRAFT);
    when(repository.findById(10L)).thenReturn(Optional.of(config));
    when(repository.deleteById(10L)).thenReturn(true);

    service.delete(10L);

    verify(repository).deleteById(10L);
    verify(scheduleBridge).deleteIfPresent(10L);
  }

  @Test
  void countActiveReturnsAggregation() {
    when(repository.countActiveByEntity(1L)).thenReturn(3L);
    assertEquals(3L, service.countActiveByEntity(1L));
  }
}
