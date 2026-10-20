package io.yak.ops.business.semantic.binding;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.datasource.connection.DataSourceConnectionTester;
import io.yak.ops.business.semantic.repository.SemanticProcessRepository;
import io.yak.ops.business.semantic.repository.SemanticProcessSourceRepository;
import io.yak.ops.business.semantic.repository.SemanticPublicationRevisionService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** SOURCE_LINK must obey the same Semantic project-revision-first protocol. */
class SemanticProcessBindingRevisionTest {
  private final SemanticProcessSourceRepository bindings =
      mock(SemanticProcessSourceRepository.class);
  private final SemanticProcessRepository processes = mock(SemanticProcessRepository.class);
  private final DataSourceConnectionTester connectivity = mock(DataSourceConnectionTester.class);
  private final BusinessAuditService audit = mock(BusinessAuditService.class);
  private final SemanticPublicationRevisionService revision =
      mock(SemanticPublicationRevisionService.class);

  private SemanticProcessBindingService service() {
    return new SemanticProcessBindingService(bindings, processes, connectivity, audit, revision);
  }

  @Test
  void bindLocksProjectRevisionBeforeAnyProcessOrDataSourceRead() {
    when(processes.findById(701L)).thenReturn(Optional.empty());
    assertThrows(RuntimeException.class,
        () -> service().bind(701L, 45L, "trade_order_detail", "DETAIL", null, "alice"));
    var order = inOrder(revision, processes);
    order.verify(revision).advanceBeforeMutation();
    order.verify(processes).findById(701L);
    verify(bindings, never()).insert(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    verify(connectivity, never()).testSaved(45L);
  }

  @Test
  void unbindLocksProjectRevisionBeforeCheckingExistingLink() {
    when(processes.findById(701L)).thenReturn(Optional.empty());
    assertThrows(RuntimeException.class, () -> service().unbind(701L, 88L));
    var order = inOrder(revision, processes);
    order.verify(revision).advanceBeforeMutation();
    order.verify(processes).findById(701L);
    verify(bindings, never()).deleteById(88L);
  }

  @Test
  void unavailableRevisionNeverReachesBindingRepository() {
    when(revision.advanceBeforeMutation()).thenThrow(
        new IllegalStateException("project business transaction unavailable"));
    assertThrows(IllegalStateException.class,
        () -> service().bind(701L, 45L, "trade_order_detail", "DETAIL", null, "alice"));
    assertThrows(IllegalStateException.class, () -> service().unbind(701L, 88L));
    verify(processes, never()).findById(701L);
    verify(bindings, never()).deleteById(88L);
  }
}
