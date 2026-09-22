package io.yak.ops.business.mdm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.mdm.domain.approval.MdmApprovalStatus;
import io.yak.ops.business.mdm.domain.approval.MdmChange;
import io.yak.ops.business.mdm.domain.approval.MdmChangeType;
import io.yak.ops.business.mdm.domain.record.MdmRecord;
import io.yak.ops.business.mdm.domain.record.MdmRecordStatus;
import io.yak.ops.business.mdm.domain.record.MdmRecordVersion;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmChangeRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmRecordRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmRecordVersionRepository;
import io.yak.ops.business.mdm.notification.MdmNotifier;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 变更单终态生效服务测试(R4,审批中心回调:生效 + 基线/新版双快照)。 */
class MdmChangeEffectServiceTest {

  private MdmChangeRepository changeRepository;
  private MdmRecordRepository recordRepository;
  private MdmRecordVersionRepository versionRepository;
  private BusinessAuditService auditService;
  private MdmNotifier notifier;
  private MdmChangeEffectService service;

  @BeforeEach
  void setUp() {
    changeRepository = Mockito.mock(MdmChangeRepository.class);
    recordRepository = Mockito.mock(MdmRecordRepository.class);
    versionRepository = Mockito.mock(MdmRecordVersionRepository.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    notifier = Mockito.mock(MdmNotifier.class);
    AuditOperationHandle audit = mock(AuditOperationHandle.class);
    Mockito.lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    Mockito.lenient().when(changeRepository.update(any())).thenReturn(true);
    service = new MdmChangeEffectService(
        changeRepository, recordRepository, versionRepository, auditService, notifier);
  }

  private static MdmChange change(
      Long id, MdmChangeType type, MdmApprovalStatus status) {
    return new MdmChange(
        id, 1L, "M001", type, "{\"member_level\":\"gold\"}",
        MdmChange.LEVEL_ONE, status, "tester", null, null, null, 77L,
        LocalDateTime.now(), LocalDateTime.now());
  }

  private static MdmRecord record(int version, String attributes) {
    return new MdmRecord(
        1L, 1L, "M001", attributes, "{}", MdmRecordStatus.ACTIVE, version,
        LocalDateTime.now(), LocalDateTime.now());
  }

  @Test
  void applyApprovedUpdateSnapshotsBeforeAndAfterAndUpdatesRecord() {
    when(changeRepository.findById(10L))
        .thenReturn(Optional.of(change(10L, MdmChangeType.UPDATE, MdmApprovalStatus.PENDING)));
    when(recordRepository.findByMasterId(1L, "M001"))
        .thenReturn(Optional.of(record(3, "{\"member_level\":\"silver\"}")));
    when(recordRepository.update(any())).thenReturn(true);

    MdmChange approved = service.applyApproved(
        10L, "admin", "同意", LocalDateTime.of(2026, 9, 20, 10, 0));

    assertEquals(MdmApprovalStatus.APPROVED, approved.approvalStatus());
    assertEquals("admin", approved.approver());

    ArgumentCaptor<MdmRecord> recordCaptor = ArgumentCaptor.forClass(MdmRecord.class);
    verify(recordRepository).update(recordCaptor.capture());
    MdmRecord updated = recordCaptor.getValue();
    assertEquals(4, updated.version());
    assertEquals("{\"member_level\":\"gold\"}", updated.attributes());

    ArgumentCaptor<MdmRecordVersion> snapshotCaptor =
        ArgumentCaptor.forClass(MdmRecordVersion.class);
    verify(versionRepository, times(2)).insertIfAbsent(snapshotCaptor.capture());
    MdmRecordVersion baseline = snapshotCaptor.getAllValues().get(0);
    assertEquals(3, baseline.version());
    assertNull(baseline.changeId());
    MdmRecordVersion after = snapshotCaptor.getAllValues().get(1);
    assertEquals(4, after.version());
    assertEquals(10L, after.changeId());
    assertEquals("admin", after.operator());
  }

  @Test
  void applyApprovedDeleteMarksRecordDeleted() {
    when(changeRepository.findById(10L))
        .thenReturn(Optional.of(change(10L, MdmChangeType.DELETE, MdmApprovalStatus.PENDING)));
    when(recordRepository.findByMasterId(1L, "M001"))
        .thenReturn(Optional.of(record(2, "{\"a\":1}")));
    when(recordRepository.update(any())).thenReturn(true);

    service.applyApproved(10L, "admin", null, null);

    ArgumentCaptor<MdmRecord> captor = ArgumentCaptor.forClass(MdmRecord.class);
    verify(recordRepository).update(captor.capture());
    assertEquals(MdmRecordStatus.DELETED, captor.getValue().status());
    assertEquals(3, captor.getValue().version());
  }

  /** 生效通知必须带应用后的记录(版本号已递增),而不是改前快照。 */
  @Test
  void applyApprovedNotifiesSubscribersWithAppliedRecord() {
    when(changeRepository.findById(10L))
        .thenReturn(Optional.of(change(10L, MdmChangeType.UPDATE, MdmApprovalStatus.PENDING)));
    when(recordRepository.findByMasterId(1L, "M001"))
        .thenReturn(Optional.of(record(3, "{\"member_level\":\"silver\"}")));
    when(recordRepository.update(any())).thenReturn(true);

    service.applyApproved(10L, "admin", "同意", null);

    ArgumentCaptor<MdmRecord> captor = ArgumentCaptor.forClass(MdmRecord.class);
    verify(notifier).changeApplied(eq(1L), captor.capture(), eq(10L), eq("admin"));
    assertEquals(4, captor.getValue().version());
    assertEquals(MdmRecordStatus.ACTIVE, captor.getValue().status());
  }

  /** 重复回调(49009 重试)不再通知:否则会给人重复推站内信。 */
  @Test
  void repeatedApprovedCallbackDoesNotNotifyAgain() {
    when(changeRepository.findById(10L))
        .thenReturn(Optional.of(change(10L, MdmChangeType.UPDATE, MdmApprovalStatus.APPROVED)));

    assertThrows(MdmException.class, () -> service.applyApproved(10L, "admin", null, null));

    verify(notifier, never()).changeApplied(any(), any(), any(), any());
  }

  @Test
  void applyApprovedRejectsNonPendingCallback() {
    when(changeRepository.findById(10L))
        .thenReturn(Optional.of(change(10L, MdmChangeType.UPDATE, MdmApprovalStatus.APPROVED)));

    MdmException exception = assertThrows(
        MdmException.class, () -> service.applyApproved(10L, "admin", null, null));
    assertEquals(MdmErrorCode.APPROVAL_FAILED, exception.getErrorCode());
    verify(changeRepository, never()).update(any());
    verify(recordRepository, never()).update(any());
  }

  @Test
  void applyApprovedMissingRecordFailsCallback() {
    when(changeRepository.findById(10L))
        .thenReturn(Optional.of(change(10L, MdmChangeType.UPDATE, MdmApprovalStatus.PENDING)));
    when(recordRepository.findByMasterId(1L, "M001")).thenReturn(Optional.empty());

    MdmException exception = assertThrows(
        MdmException.class, () -> service.applyApproved(10L, "admin", null, null));
    assertEquals(MdmErrorCode.APPROVAL_FAILED, exception.getErrorCode());
    verify(changeRepository, never()).update(any());
  }

  @Test
  void applyRejectedRecordsOpinionWithoutTouchingRecord() {
    when(changeRepository.findById(10L))
        .thenReturn(Optional.of(change(10L, MdmChangeType.UPDATE, MdmApprovalStatus.PENDING)));

    MdmChange rejected = service.applyRejected(10L, "admin", "不通过", null);

    assertEquals(MdmApprovalStatus.REJECTED, rejected.approvalStatus());
    assertEquals("不通过", rejected.approvalComment());
    verify(recordRepository, never()).update(any());
    verify(versionRepository, never()).insertIfAbsent(any());
  }

  @Test
  void applyCanceledFlipsToWithdrawn() {
    when(changeRepository.findById(10L))
        .thenReturn(Optional.of(change(10L, MdmChangeType.UPDATE, MdmApprovalStatus.PENDING)));

    MdmChange withdrawn = service.applyCanceled(10L, "tester", "MDM 申请人撤回");

    assertEquals(MdmApprovalStatus.WITHDRAWN, withdrawn.approvalStatus());
    verify(changeRepository).update(any());
  }
}
