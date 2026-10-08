package io.yak.ops.business.semantic.process;

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
import io.yak.ops.business.semantic.binding.SemanticProcessBindingService;
import io.yak.ops.business.semantic.api.BusinessDomain;
import io.yak.ops.business.semantic.api.BusinessProcess;
import io.yak.ops.business.semantic.api.SemanticStructureReferenceReader;
import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.business.semantic.field.SemanticFieldService;
import io.yak.ops.business.semantic.repository.SemanticDomainRepository;
import io.yak.ops.business.semantic.repository.SemanticProcessRepository;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 业务过程规则单元测试:编码唯一、业务域存在性、删除。 */
class BusinessProcessServiceTest {

  private SemanticProcessRepository repository;
  private SemanticDomainRepository domainRepository;
  private SemanticFieldService fieldService;
  private SemanticProcessBindingService bindingService;
  private BusinessAuditService auditService;
  private AuditOperationHandle audit;
  private BusinessProcessService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(SemanticProcessRepository.class);
    domainRepository = Mockito.mock(SemanticDomainRepository.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    audit = Mockito.mock(AuditOperationHandle.class);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    lenient()
        .when(domainRepository.findById(7L))
        .thenReturn(
            Optional.of(
                new BusinessDomain(7L, "trade", "交易", 0L, null, null, 0, "t", null, null)));
    fieldService = Mockito.mock(SemanticFieldService.class);
    bindingService = Mockito.mock(SemanticProcessBindingService.class);
    service = new BusinessProcessService(repository, domainRepository, fieldService, bindingService, auditService);
  }

  @Test
  void createRejectsDuplicateCode() {
    when(repository.existsByCode("place_order")).thenReturn(true);
    SemanticException exception =
        assertThrows(
            SemanticException.class,
            () -> service.create(7L, "place_order", "下单", "单据", "FACT", null, null, null,
                "tester"));
    assertEquals(SemanticErrorCode.DUPLICATE_CODE, exception.getErrorCode());
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void createRejectsMissingDomain() {
    when(repository.existsByCode("place_order")).thenReturn(false);
    SemanticException exception =
        assertThrows(
            SemanticException.class,
            () -> service.create(999L, "place_order", "下单", "单据", "FACT", null, null, null,
                "tester"));
    assertEquals(SemanticErrorCode.NOT_FOUND, exception.getErrorCode());
  }

  @Test
  void createRejectsInvalidBizType() {
    SemanticException exception =
        assertThrows(
            SemanticException.class,
            () -> service.create(7L, "place_order", "下单", "单据", "WRONG", null, null, null,
                "tester"));
    assertEquals(SemanticErrorCode.INVALID_SEARCH, exception.getErrorCode());
  }

  @Test
  void deleteBlockedWhenModelingStillReferencesProcess() {
    long id = 9L;
    when(repository.findById(id)).thenReturn(Optional.of(existingProcess(id)));
    BusinessProcessService guarded = new BusinessProcessService(repository, domainRepository,
        fieldService, bindingService, auditService, List.of(structureReader(3, 0)));

    SemanticException failure = assertThrows(SemanticException.class, () -> guarded.delete(id));

    assertEquals(SemanticErrorCode.PROCESS_REFERENCED, failure.getErrorCode());
    verify(repository, never()).deleteById(id);
  }

  @Test
  void deleteBlockedWhenConsumerReferenceQueryFails() {
    long id = 9L;
    when(repository.findById(id)).thenReturn(Optional.of(existingProcess(id)));
    SemanticStructureReferenceReader unavailable = new SemanticStructureReferenceReader() {
      @Override
      public long countProcessReferences(Long processId) {
        throw new IllegalStateException("Modeling unavailable");
      }

      @Override
      public long countDomainReferences(Long domainId) { return 0; }
    };
    BusinessProcessService guarded = new BusinessProcessService(repository, domainRepository,
        fieldService, bindingService, auditService, List.of(unavailable));

    assertThrows(IllegalStateException.class, () -> guarded.delete(id));
    verify(repository, never()).deleteById(id);
  }

  @Test
  void deleteSucceedsWhenModelingAndLocalBindingsHaveNoReferences() {
    long id = 9L;
    when(repository.findById(id)).thenReturn(Optional.of(existingProcess(id)));
    when(repository.deleteById(id)).thenReturn(true);
    BusinessProcessService guarded = new BusinessProcessService(repository, domainRepository,
        fieldService, bindingService, auditService, List.of(structureReader(0, 0)));

    guarded.delete(id);

    verify(fieldService).assertProcessDeletable(id);
    verify(bindingService).assertProcessDeletable(id);
    verify(repository).deleteById(id);
  }

  private static BusinessProcess existingProcess(long id) {
    return new BusinessProcess(id, "place_order", "下单", 7L, "单据",
        "FACT", null, null, 0, "tester", null, null);
  }

  private static SemanticStructureReferenceReader structureReader(long processRefs, long domainRefs) {
    return new SemanticStructureReferenceReader() {
      @Override
      public long countProcessReferences(Long processId) { return processRefs; }

      @Override
      public long countDomainReferences(Long domainId) { return domainRefs; }
    };
  }

  @Test
  void createInsertsAndAudits() {
    when(repository.existsByCode("place_order")).thenReturn(false);
    when(repository.insert(any(), eq("tester"))).thenAnswer(invocation -> invocation.getArgument(0));
    BusinessProcess created =
        service.create(7L, "place_order", "下单", "单据", "FACT", null, null, null, "tester");
    assertEquals("place_order", created.code());
    Mockito.verify(audit, never()).failure(Mockito.anyString(), Mockito.any(Throwable.class));
  }
}
