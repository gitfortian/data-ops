package io.yak.ops.business.mdm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.common.PageData;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.entity.MdmEntityStatus;
import io.yak.ops.business.mdm.domain.record.MdmRecord;
import io.yak.ops.business.mdm.domain.record.MdmRecordStatus;
import io.yak.ops.business.mdm.domain.subscription.MdmSubscription;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmRecordRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmSubscriptionRepository;
import io.yak.ops.business.mdm.notification.MdmUserDirectory;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 主数据服务单元测试:查询 API + 订阅管理(ticket 59)。 */
class MdmServiceServiceTest {

  private MdmRecordRepository recordRepository;
  private MdmSubscriptionRepository subscriptionRepository;
  private MdmEntityService entityService;
  private BusinessAuditService auditService;
  private MdmUserDirectory userDirectory;
  private MdmServiceService service;

  @BeforeEach
  void setUp() {
    recordRepository = Mockito.mock(MdmRecordRepository.class);
    subscriptionRepository = Mockito.mock(MdmSubscriptionRepository.class);
    entityService = Mockito.mock(MdmEntityService.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    userDirectory = Mockito.mock(MdmUserDirectory.class);
    AuditOperationHandle audit = Mockito.mock(AuditOperationHandle.class);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    lenient().when(entityService.get(any())).thenReturn(
        new MdmEntity(
            1L, "customer", "客户", MdmEntityStatus.ACTIVE, "root", null,
            "root", null, null));
    service = new MdmServiceService(
        recordRepository, subscriptionRepository, entityService, auditService, userDirectory);
  }

  private static MdmRecord record(Long id, String masterId) {
    return new MdmRecord(
        id, 1L, masterId, "{\"name\":\"test\"}", "{}", MdmRecordStatus.ACTIVE, 1,
        LocalDateTime.now(), LocalDateTime.now());
  }

  private static MdmSubscription subscription(
      Long id, Long entityId, String code, String status) {
    return new MdmSubscription(
        id, entityId, code, code + " System", "EVENT", status,
        "tester", LocalDateTime.now(), LocalDateTime.now());
  }

  // ==== 查询 API ====

  @Test
  void getRecordByMasterIdReturnsActiveRecord() {
    when(recordRepository.findByMasterId(1L, "M001"))
        .thenReturn(Optional.of(record(1L, "M001")));

    MdmServiceService.RecordView view = service.getRecordByMasterId(1L, "M001");

    assertNotNull(view);
    assertEquals("M001", view.masterId());
    assertEquals("ACTIVE", view.status());
  }

  @Test
  void getRecordByMasterIdThrowsWhenNotFound() {
    when(recordRepository.findByMasterId(1L, "M999"))
        .thenReturn(Optional.empty());

    MdmException exception = assertThrows(
        MdmException.class, () -> service.getRecordByMasterId(1L, "M999"));
    assertEquals(MdmErrorCode.RECORD_NOT_FOUND, exception.getErrorCode());
  }

  @Test
  void getRecordByMasterIdRejectsBlankMasterId() {
    MdmException exception = assertThrows(
        MdmException.class, () -> service.getRecordByMasterId(1L, ""));
    assertEquals(MdmErrorCode.RECORD_NOT_FOUND, exception.getErrorCode());
  }

  @Test
  void searchRecordsDelegatesToRepository() {
    PageData<MdmRecord> page = PageData.of(
        List.of(record(1L, "M001"), record(2L, "M002")), 2L, 1L, 20L);
    when(recordRepository.page(
            eq(1L), eq(1), eq(20), eq("test"), eq(MdmRecordStatus.ACTIVE), eq(List.of())))
        .thenReturn(page);

    PageData<MdmServiceService.RecordView> result =
        service.searchRecords(1L, "test", 1, 20);

    assertEquals(2, result.records().size());
    assertEquals(2L, result.total());
  }

  // ==== 订阅 CRUD ====

  @Test
  void createSubscriptionInsertsActive() {
    when(subscriptionRepository.existsBySubscriber(1L, "CRM", null)).thenReturn(false);
    when(subscriptionRepository.insert(any(MdmSubscription.class), any()))
        .thenAnswer(
            invocation -> {
              MdmSubscription s = invocation.getArgument(0);
              return s.withPersisted(10L, "tester", LocalDateTime.now());
            });

    MdmSubscription created = service.createSubscription(
        1L, "CRM", "CRM 系统", "EVENT", "tester");

    assertEquals(10L, created.id());
    assertEquals(MdmSubscription.STATUS_ACTIVE, created.status());
    verify(subscriptionRepository).insert(any(), any());
  }

  @Test
  void createSubscriptionRejectsDuplicate() {
    when(subscriptionRepository.existsBySubscriber(1L, "CRM", null)).thenReturn(true);

    MdmException exception = assertThrows(
        MdmException.class,
        () -> service.createSubscription(1L, "CRM", "CRM", "EVENT", "tester"));
    assertEquals(MdmErrorCode.RECORD_NOT_FOUND, exception.getErrorCode());
    verify(subscriptionRepository, never()).insert(any(), any());
  }

  @Test
  void createSubscriptionRejectsEmptyCode() {
    MdmException exception = assertThrows(
        MdmException.class,
        () -> service.createSubscription(1L, "", "CRM", "EVENT", "tester"));
    assertEquals(MdmErrorCode.RECORD_NOT_FOUND, exception.getErrorCode());
  }

  /** WEBHOOK 依赖外部 HTTP 出口,一期不接入就明确拒绝,不能收下这条永不推送的订阅。 */
  @Test
  void createSubscriptionRejectsWebhookUntilChannelExists() {
    MdmException exception = assertThrows(
        MdmException.class,
        () -> service.createSubscription(1L, "CRM", "CRM", "WEBHOOK", "tester"));

    assertEquals(MdmErrorCode.NOTIFY_MODE_UNSUPPORTED, exception.getErrorCode());
    verify(subscriptionRepository, never()).insert(any(), any());
  }

  @Test
  void createSubscriptionNormalizesNotifyModeAndDefaultsToEvent() {
    when(subscriptionRepository.existsBySubscriber(1L, "CRM", null)).thenReturn(false);
    when(subscriptionRepository.insert(any(MdmSubscription.class), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    MdmSubscription lowercase = service.createSubscription(
        1L, "CRM", "CRM 系统", " event ", "tester");
    MdmSubscription omitted = service.createSubscription(1L, "ERP", "ERP", null, "tester");

    assertEquals(MdmSubscription.NOTIFY_MODE_EVENT, lowercase.notifyMode());
    assertEquals(MdmSubscription.NOTIFY_MODE_EVENT, omitted.notifyMode());
  }

  @Test
  void listSubscriptionsReturnsAll() {
    when(subscriptionRepository.listByEntity(1L)).thenReturn(List.of(
        subscription(1L, 1L, "CRM", MdmSubscription.STATUS_ACTIVE),
        subscription(2L, 1L, "ERP", MdmSubscription.STATUS_DISABLED)));

    assertEquals(2, service.listSubscriptions(1L).size());
  }

  @Test
  void setSubscriptionStatusUpdates() {
    MdmSubscription sub = subscription(10L, 1L, "CRM", MdmSubscription.STATUS_ACTIVE);
    when(subscriptionRepository.findById(10L)).thenReturn(Optional.of(sub));
    when(subscriptionRepository.update(any())).thenReturn(true);

    service.setSubscriptionStatus(10L, MdmSubscription.STATUS_DISABLED);

    verify(subscriptionRepository).update(any());
  }

  @Test
  void deleteSubscriptionRemoves() {
    MdmSubscription sub = subscription(10L, 1L, "CRM", MdmSubscription.STATUS_ACTIVE);
    when(subscriptionRepository.findById(10L)).thenReturn(Optional.of(sub));
    when(subscriptionRepository.deleteById(10L)).thenReturn(true);

    service.deleteSubscription(10L);

    verify(subscriptionRepository).deleteById(10L);
  }

  @Test
  void countActiveSubscriptionsReturnsAggregation() {
    when(subscriptionRepository.countActiveByEntity(1L)).thenReturn(5L);
    assertEquals(5L, service.countActiveSubscriptions(1L));
  }
}
