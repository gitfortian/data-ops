package io.yak.ops.business.semantic.domain;

import io.yak.ops.business.semantic.api.BusinessDomain;

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
import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.business.semantic.repository.SemanticDomainRepository;
import io.yak.ops.business.semantic.repository.SemanticProcessRepository;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 业务域树规则单元测试:编码唯一、子域阻断删除、移动环检测。 */
class BusinessDomainServiceTest {

  private SemanticDomainRepository repository;
  private SemanticProcessRepository processRepository;
  private BusinessAuditService auditService;
  private AuditOperationHandle audit;
  private BusinessDomainService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(SemanticDomainRepository.class);
    processRepository = Mockito.mock(SemanticProcessRepository.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    audit = Mockito.mock(AuditOperationHandle.class);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    lenient().when(processRepository.existsByDomain(any())).thenReturn(false);
    service = new BusinessDomainService(repository, processRepository, auditService);
  }

  @Test
  void createRejectsDuplicateCode() {
    when(repository.existsByCode("trade")).thenReturn(true);
    SemanticException exception =
        assertThrows(
            SemanticException.class,
            () -> service.create(null, "trade", "交易", null, null, null, "tester"));
    assertEquals(SemanticErrorCode.DUPLICATE_CODE, exception.getErrorCode());
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void createRejectsMissingParent() {
    when(repository.existsByCode("pay")).thenReturn(false);
    when(repository.findById(99L)).thenReturn(Optional.empty());
    SemanticException exception =
        assertThrows(
            SemanticException.class,
            () -> service.create(99L, "pay", "支付", null, null, null, "tester"));
    assertEquals(SemanticErrorCode.NOT_FOUND, exception.getErrorCode());
  }

  @Test
  void deleteBlockedWhenChildrenExist() {
    when(repository.findById(5L)).thenReturn(Optional.of(existingDomain()));
    when(repository.existsByParent(5L)).thenReturn(true);
    SemanticException exception =
        assertThrows(SemanticException.class, () -> service.delete(5L));
    assertEquals(SemanticErrorCode.DOMAIN_REFERENCED, exception.getErrorCode());
    verify(repository, never()).deleteById(eq(5L));
  }

  @Test
  void deleteBlockedWhenProcessesExist() {
    when(repository.findById(5L)).thenReturn(Optional.of(existingDomain()));
    when(repository.existsByParent(5L)).thenReturn(false);
    when(processRepository.existsByDomain(5L)).thenReturn(true);
    SemanticException exception =
        assertThrows(SemanticException.class, () -> service.delete(5L));
    assertEquals(SemanticErrorCode.DOMAIN_REFERENCED, exception.getErrorCode());
    verify(repository, never()).deleteById(eq(5L));
  }

  @Test
  void moveRejectsDescendantTarget() {
    // 树:1(根) -> 2 -> 3;把 1 移到 3 之下形成环。
    BusinessDomain node1 = new BusinessDomain(1L, "root", "根", 0L, null, null, 0, "t", null, null);
    BusinessDomain node2 = new BusinessDomain(2L, "a", "A", 1L, null, null, 0, "t", null, null);
    BusinessDomain node3 = new BusinessDomain(3L, "b", "B", 2L, null, null, 0, "t", null, null);
    when(repository.findById(1L)).thenReturn(Optional.of(node1));
    when(repository.findById(3L)).thenReturn(Optional.of(node3));
    when(repository.findAll()).thenReturn(List.of(node1, node2, node3));
    SemanticException exception =
        assertThrows(SemanticException.class, () -> service.move(1L, 3L, null, "tester"));
    assertEquals(SemanticErrorCode.INVALID_MOVE, exception.getErrorCode());
    verify(repository, never()).move(any(), any(), org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void treeAssemblesChildrenUnderParents() {
    BusinessDomain root = new BusinessDomain(1L, "trade", "交易", 0L, null, null, 0, "t", null, null);
    BusinessDomain child = new BusinessDomain(2L, "pay", "支付", 1L, null, null, 0, "t", null, null);
    when(repository.findAll()).thenReturn(List.of(root, child));
    List<BusinessDomainService.DomainNode> tree = service.tree();
    assertEquals(1, tree.size());
    assertEquals("trade", tree.get(0).code());
    assertEquals(1, tree.get(0).children().size());
    assertEquals("pay", tree.get(0).children().get(0).code());
  }

  private BusinessDomain existingDomain() {
    return new BusinessDomain(5L, "trade", "交易", 0L, null, null, 0, "tester", null, null);
  }
}
