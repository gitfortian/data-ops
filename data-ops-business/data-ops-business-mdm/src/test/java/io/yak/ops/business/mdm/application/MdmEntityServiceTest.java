package io.yak.ops.business.mdm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.entity.MdmEntityStatus;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmEntityRepository;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 主数据实体规则单元测试:编码格式/唯一、名称必填、状态流转、删除。 */
class MdmEntityServiceTest {

  private MdmEntityRepository repository;
  private BusinessAuditService auditService;
  private AuditOperationHandle audit;
  private MdmEntityService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(MdmEntityRepository.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    audit = Mockito.mock(AuditOperationHandle.class);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    service = new MdmEntityService(repository, auditService);
  }

  @Test
  void createRejectsInvalidCodeFormat() {
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.create("bad code!", "客户", null, null, "tester"));
    assertEquals(MdmErrorCode.INVALID_CODE, exception.getErrorCode());
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void createRejectsDuplicateCode() {
    when(repository.existsByCode("customer")).thenReturn(true);
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.create("customer", "客户", null, null, "tester"));
    assertEquals(MdmErrorCode.DUPLICATE_CODE, exception.getErrorCode());
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void createRejectsBlankName() {
    when(repository.existsByCode("customer")).thenReturn(false);
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.create("customer", "  ", null, null, "tester"));
    assertEquals(MdmErrorCode.INVALID_NAME, exception.getErrorCode());
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void createPersistsAsDraft() {
    when(repository.existsByCode("customer")).thenReturn(false);
    when(repository.insert(any(), eq("tester")))
        .thenReturn(
            new MdmEntity(
                1L,
                "customer",
                "客户",
                MdmEntityStatus.DRAFT,
                null,
                null,
                "tester",
                LocalDateTime.now(),
                LocalDateTime.now()));
    MdmEntity created = service.create("customer", "客户", null, null, "tester");
    assertEquals(MdmEntityStatus.DRAFT, created.status());
    verify(repository).insert(any(), eq("tester"));
  }

  @Test
  void getThrowsNotFound() {
    when(repository.findById(99L)).thenReturn(Optional.empty());
    MdmException exception = assertThrows(MdmException.class, () -> service.get(99L));
    assertEquals(MdmErrorCode.ENTITY_NOT_FOUND, exception.getErrorCode());
  }

  @Test
  void changeStatusRejectsDraftRollback() {
    when(repository.findById(1L))
        .thenReturn(Optional.of(entity(1L, "customer", MdmEntityStatus.ACTIVE)));
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.changeStatus(1L, MdmEntityStatus.DRAFT));
    assertEquals(MdmErrorCode.INVALID_STATUS, exception.getErrorCode());
    verify(repository, never()).changeStatus(eq(1L), any());
  }

  @Test
  void changeStatusAllowsActivateThenDisable() {
    when(repository.findById(1L))
        .thenReturn(Optional.of(entity(1L, "customer", MdmEntityStatus.ACTIVE)));
    service.changeStatus(1L, MdmEntityStatus.DISABLED);
    verify(repository).changeStatus(1L, MdmEntityStatus.DISABLED);
  }

  private static MdmEntity entity(Long id, String code, MdmEntityStatus status) {
    return new MdmEntity(
        id, code, "客户", status, null, null, "tester", LocalDateTime.now(), LocalDateTime.now());
  }
}
