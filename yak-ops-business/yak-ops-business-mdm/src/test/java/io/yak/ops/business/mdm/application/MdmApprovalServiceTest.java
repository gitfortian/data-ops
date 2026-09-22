package io.yak.ops.business.mdm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.common.PageData;
import io.yak.ops.business.approval.api.ApprovalApi;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.approval.api.ApprovalSubmitCommand;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.mdm.domain.approval.MdmApprovalStatus;
import io.yak.ops.business.mdm.domain.approval.MdmChange;
import io.yak.ops.business.mdm.domain.approval.MdmChangeType;
import io.yak.ops.business.mdm.domain.attribute.MdmAttribute;
import io.yak.ops.business.mdm.domain.attribute.MdmAttributeType;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.record.MdmRecordStatus;
import io.yak.ops.business.mdm.domain.record.MdmRecordVersion;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmAttributeRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmChangeRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmRecordVersionRepository;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 主数据审批单元测试(R4 接审批中心:提单转中心、终态回调生效)。 */
class MdmApprovalServiceTest {

  private MdmChangeRepository changeRepository;
  private MdmRecordVersionRepository versionRepository;
  private MdmEntityService entityService;
  private MdmAttributeRepository attributeRepository;
  private BusinessAuditService auditService;
  private ApprovalApi approvalApi;
  private MdmApprovalService service;

  @BeforeEach
  void setUp() {
    changeRepository = Mockito.mock(MdmChangeRepository.class);
    versionRepository = Mockito.mock(MdmRecordVersionRepository.class);
    entityService = Mockito.mock(MdmEntityService.class);
    attributeRepository = Mockito.mock(MdmAttributeRepository.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    approvalApi = Mockito.mock(ApprovalApi.class);
    AuditOperationHandle audit = mock(AuditOperationHandle.class);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    lenient().when(entityService.get(anyLong())).thenReturn(entity());
    lenient().when(attributeRepository.listByEntity(anyLong())).thenReturn(List.of());
    lenient().when(changeRepository.listByMaster(anyLong(), anyString())).thenReturn(List.of());
    lenient().when(changeRepository.update(any())).thenReturn(true);
    service = new MdmApprovalService(
        changeRepository, versionRepository, entityService,
        attributeRepository, auditService, approvalApi);
  }

  private static MdmEntity entity() {
    return new MdmEntity(1L, "customer", "客户", null, null, null, "tester", null, null);
  }

  private static MdmChange change(
      Long id, MdmChangeType type, MdmApprovalStatus status, Long instanceId) {
    return new MdmChange(
        id, 1L, "M001", type, "{\"member_level\":\"gold\"}",
        MdmChange.LEVEL_ONE, status, "tester", null, null, null, instanceId,
        LocalDateTime.now(), LocalDateTime.now());
  }

  // ==== 提单:落 change + 发起中心单 + 关联 instanceId ====

  @Test
  void submitRoutesToApprovalCenterAndAttachesInstance() {
    when(changeRepository.insert(any(MdmChange.class)))
        .thenAnswer(invocation ->
            ((MdmChange) invocation.getArgument(0)).withPersisted(10L, LocalDateTime.now()));
    when(approvalApi.submit(any(ApprovalSubmitCommand.class)))
        .thenAnswer(invocation -> instance(77L, invocation.getArgument(0)));

    MdmChange created = service.submit(
        1L, "M001", MdmChangeType.UPDATE, "{\"member_level\":\"gold\"}", 1, "tester");

    assertEquals(10L, created.id());
    assertEquals(77L, created.instanceId());
    assertEquals(MdmApprovalStatus.PENDING, created.approvalStatus());
    ArgumentCaptor<ApprovalSubmitCommand> captor =
        ArgumentCaptor.forClass(ApprovalSubmitCommand.class);
    verify(approvalApi).submit(captor.capture());
    ApprovalSubmitCommand cmd = captor.getValue();
    assertEquals(ApprovalFlowCodes.MDM_CHANGE, cmd.flowCode());
    assertEquals("MDM_CHANGE", cmd.bizType());
    assertEquals("10", cmd.bizId());
    assertEquals("tester", cmd.applicant());
    org.junit.jupiter.api.Assertions.assertTrue(
        cmd.payloadJson().contains("\"masterId\":\"M001\""));
    ArgumentCaptor<MdmChange> updated = ArgumentCaptor.forClass(MdmChange.class);
    verify(changeRepository).update(updated.capture());
    assertEquals(77L, updated.getValue().instanceId());
  }

  private static ApprovalInstanceView instance(Long id, ApprovalSubmitCommand cmd) {
    return new ApprovalInstanceView(
        id, cmd.flowCode(), "主数据变更", cmd.bizType(), cmd.bizId(), cmd.title(),
        cmd.payloadJson(), cmd.applicant(), "PENDING", 1, LocalDateTime.now(), null);
  }

  @Test
  void submitFailsWhenInstanceAssociationFails() {
    when(changeRepository.insert(any(MdmChange.class)))
        .thenAnswer(invocation ->
            ((MdmChange) invocation.getArgument(0)).withPersisted(10L, LocalDateTime.now()));
    when(approvalApi.submit(any(ApprovalSubmitCommand.class)))
        .thenAnswer(invocation -> instance(77L, invocation.getArgument(0)));
    when(changeRepository.update(any())).thenReturn(false);

    MdmException exception = assertThrows(
        MdmException.class,
        () -> service.submit(1L, "M001", MdmChangeType.UPDATE, "{\"a\":1}", 1, "tester"));
    assertEquals(MdmErrorCode.APPROVAL_FAILED, exception.getErrorCode());
  }

  // ==== 提单校验 ====

  @Test
  void submitRejectsCreateAndMergeTypes() {
    for (MdmChangeType type : List.of(MdmChangeType.CREATE, MdmChangeType.MERGE)) {
      MdmException exception = assertThrows(
          MdmException.class,
          () -> service.submit(1L, "M001", type, "{\"a\":1}", 1, "tester"));
      assertEquals(MdmErrorCode.CHANGE_TYPE_UNSUPPORTED, exception.getErrorCode());
    }
    verify(changeRepository, never()).insert(any());
  }

  @Test
  void submitRejectsPkModification() {
    when(attributeRepository.listByEntity(1L)).thenReturn(List.of(
        attribute("cust_id", MdmAttributeType.PK),
        attribute("member_level", MdmAttributeType.ATTR)));

    MdmException exception = assertThrows(
        MdmException.class,
        () -> service.submit(1L, "M001", MdmChangeType.UPDATE, "{\"cust_id\":\"X9\"}", 1, "tester"));
    assertEquals(MdmErrorCode.PK_CHANGE_FORBIDDEN, exception.getErrorCode());
    verify(changeRepository, never()).insert(any());
  }

  private static MdmAttribute attribute(String code, MdmAttributeType type) {
    return new MdmAttribute(
        null, 1L, code, code, type, "STRING", null, null, null, null,
        false, null, 0, MdmAttribute.STATUS_ENABLED, "tester", null, null);
  }

  @Test
  void submitRejectsWhenChangeInFlight() {
    when(changeRepository.listByMaster(1L, "M001"))
        .thenReturn(List.of(change(9L, MdmChangeType.UPDATE, MdmApprovalStatus.PENDING, 66L)));

    MdmException exception = assertThrows(
        MdmException.class,
        () -> service.submit(1L, "M001", MdmChangeType.UPDATE, "{\"a\":1}", 1, "tester"));
    assertEquals(MdmErrorCode.CHANGE_IN_FLIGHT, exception.getErrorCode());
    verify(changeRepository, never()).insert(any());
  }

  // ==== 撤回:动作转中心 ====

  @Test
  void withdrawDelegatesCancellationToCenter() {
    when(changeRepository.findById(10L))
        .thenReturn(Optional.of(change(10L, MdmChangeType.UPDATE, MdmApprovalStatus.PENDING, 77L)));

    service.withdraw(10L, "tester");

    verify(approvalApi).cancel(eq(77L), eq("tester"), anyString());
    verify(changeRepository, never()).update(any());
  }

  @Test
  void withdrawRequiresLinkedInstance() {
    when(changeRepository.findById(10L))
        .thenReturn(Optional.of(change(10L, MdmChangeType.UPDATE, MdmApprovalStatus.PENDING, null)));

    MdmException exception = assertThrows(
        MdmException.class, () -> service.withdraw(10L, "tester"));
    assertEquals(MdmErrorCode.APPROVAL_FAILED, exception.getErrorCode());
    verify(approvalApi, never()).cancel(any(), any(), any());
  }

  // ==== 查询 ====

  @Test
  void pageDelegatesToRepository() {
    PageData<MdmChange> page = PageData.of(
        List.of(change(1L, MdmChangeType.UPDATE, MdmApprovalStatus.PENDING, 77L)), 1L, 1L, 20L);
    when(changeRepository.page(null, null, null, 1, 20)).thenReturn(page);

    assertEquals(1, service.page(null, null, null, 1, 20).records().size());
  }

  @Test
  void listVersionsDelegatesToVersionRepository() {
    when(versionRepository.listByMaster(1L, "M001")).thenReturn(List.of(
        new MdmRecordVersion(null, 1L, "M001", 3, "{\"a\":1}", MdmRecordStatus.ACTIVE,
            null, "tester", LocalDateTime.now()),
        new MdmRecordVersion(null, 1L, "M001", 4, "{\"a\":2}", MdmRecordStatus.ACTIVE,
            10L, "admin", LocalDateTime.now())));

    List<MdmRecordVersion> versions = service.listVersions(1L, "M001");
    assertEquals(2, versions.size());
    assertEquals(3, versions.get(0).version());
    assertEquals(4, versions.get(1).version());
  }

  @Test
  void countPendingReturnsAggregation() {
    when(changeRepository.countPending(1L)).thenReturn(3L);
    assertEquals(3L, service.countPending(1L));
  }
}
