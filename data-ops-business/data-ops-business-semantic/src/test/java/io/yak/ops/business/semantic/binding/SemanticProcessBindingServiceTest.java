package io.yak.ops.business.semantic.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.datasource.connection.DataSourceConnectionTester;
import io.yak.ops.business.semantic.api.BusinessProcess;
import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.business.semantic.repository.SemanticProcessRepository;
import io.yak.ops.business.semantic.repository.SemanticProcessSourceRepository;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SemanticProcessBindingServiceTest {

  private SemanticProcessSourceRepository bindings;
  private SemanticProcessRepository processes;
  private DataSourceConnectionTester connectionTester;
  private BusinessAuditService auditService;
  private AuditOperationHandle audit;
  private SemanticProcessBindingService service;

  @BeforeEach
  void setUp() {
    bindings = mock(SemanticProcessSourceRepository.class);
    processes = mock(SemanticProcessRepository.class);
    connectionTester = mock(DataSourceConnectionTester.class);
    auditService = mock(BusinessAuditService.class);
    audit = mock(AuditOperationHandle.class);
    service = new SemanticProcessBindingService(
        bindings, processes, connectionTester, auditService);
  }

  @Test
  void missingProcessBlocksBothBindAndListWithoutAccessingBindings() {
    SemanticException bindingError = assertThrows(SemanticException.class,
        () -> service.bind(404L, 12L, "orders", "MAIN", null, "alice"));
    assertEquals(SemanticErrorCode.NOT_FOUND, bindingError.getErrorCode());

    SemanticException listError = assertThrows(SemanticException.class,
        () -> service.listByProcess(404L));
    assertEquals(SemanticErrorCode.NOT_FOUND, listError.getErrorCode());

    verify(processes, org.mockito.Mockito.times(2)).findById(404L);
    verify(bindings, never()).listByProcess(any());
    verify(bindings, never()).insert(any(), any());
    verify(connectionTester, never()).testSaved(any());
  }

  @Test
  void existingProcessListsTheOriginalRepositoryBindings() {
    when(processes.findById(7L)).thenReturn(Optional.of(process(7L)));
    ProcessSourceBinding binding = new ProcessSourceBinding(
        8L, 7L, 12L, "orders", "MAIN", null, "alice", null, null);
    when(bindings.listByProcess(7L)).thenReturn(List.of(binding));

    assertEquals(List.of(binding), service.listByProcess(7L));
    verify(processes).findById(7L);
    verify(bindings).listByProcess(7L);
  }

  @Test
  void failedDatasourceConnectivityStillRejectsBinding() {
    when(processes.findById(7L)).thenReturn(Optional.of(process(7L)));
    when(connectionTester.testSaved(12L)).thenReturn(false);
    when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);

    SemanticException error = assertThrows(SemanticException.class,
        () -> service.bind(7L, 12L, "orders", "MAIN", null, "alice"));

    assertEquals(SemanticErrorCode.INVALID_SEARCH, error.getErrorCode());
    verify(bindings, never()).insert(any(), any());
    verify(audit).failure(eq("SEMANTIC_PROCESS_SOURCE_BIND_FAILED"), any(SemanticException.class));
  }

  @Test
  void connectedDatasourceBindsWithDefaultRoleAndOriginalFields() {
    when(processes.findById(7L)).thenReturn(Optional.of(process(7L)));
    when(connectionTester.testSaved(12L)).thenReturn(true);
    when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    when(bindings.insert(any(ProcessSourceBinding.class), eq("alice")))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ProcessSourceBinding result = service.bind(7L, 12L, "orders", null, "a.id=b.id", "alice");

    assertEquals(7L, result.processId());
    assertEquals(12L, result.datasourceId());
    assertEquals("orders", result.sourceTable());
    assertEquals("MAIN", result.tableRole());
    assertEquals("a.id=b.id", result.joinCondition());
    assertEquals("alice", result.createdBy());
    ArgumentCaptor<ProcessSourceBinding> captor =
        ArgumentCaptor.forClass(ProcessSourceBinding.class);
    verify(bindings).insert(captor.capture(), eq("alice"));
    assertEquals(result, captor.getValue());
  }

  @Test
  void unbindRejectsAssociationNotOwnedByTheRequestedProcess() {
    when(processes.findById(7L)).thenReturn(Optional.of(process(7L)));
    when(bindings.listByProcess(7L)).thenReturn(List.of(
        new ProcessSourceBinding(8L, 7L, 12L, "orders", "MAIN", null, "alice", null, null)));

    SemanticException error = assertThrows(SemanticException.class,
        () -> service.unbind(7L, 9L));

    assertEquals(SemanticErrorCode.NOT_FOUND, error.getErrorCode());
    verify(bindings, never()).deleteById(any());
    verify(auditService, never()).start(any());
  }

  private static BusinessProcess process(Long id) {
    return new BusinessProcess(id, "place_order", "下单", 3L, "订单", "FACT",
        null, null, 0, "alice", null, null);
  }
}
