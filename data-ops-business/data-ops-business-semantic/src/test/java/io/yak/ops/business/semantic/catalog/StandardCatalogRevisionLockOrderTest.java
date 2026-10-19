package io.yak.ops.business.semantic.catalog;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.business.semantic.repository.SemanticFieldRepository;
import io.yak.ops.business.semantic.repository.SemanticLayerRepository;
import io.yak.ops.business.semantic.repository.SemanticPublicationRevisionService;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import io.yak.ops.business.semantic.repository.SemanticStandardVersionRepository;
import io.yak.ops.business.semantic.api.StandardReferenceReader;
import io.yak.ops.business.approval.api.ApprovalApi;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** T2 development evidence: project revision lock order precedes a standard row FOR UPDATE. */
class StandardCatalogRevisionLockOrderTest {
  @Test
  void statusAndUpdateAcquireProjectRevisionBeforeIndividualStandardLock() {
    var standards = mock(SemanticStandardRepository.class);
    var versions = mock(SemanticStandardVersionRepository.class);
    var fields = mock(SemanticFieldRepository.class);
    var audit = mock(BusinessAuditService.class);
    var layers = mock(SemanticLayerRepository.class);
    var revision = mock(SemanticPublicationRevisionService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<StandardReferenceReader> readers = mock(ObjectProvider.class);
    when(readers.orderedStream()).thenReturn(Stream.empty());
    @SuppressWarnings("unchecked")
    ObjectProvider<ApprovalApi> approvals = mock(ObjectProvider.class);
    when(standards.findByIdForUpdate(91L)).thenReturn(Optional.empty());

    var service = new StandardCatalogService(standards, versions, fields,
        audit, layers, readers, approvals, revision);
    org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
        () -> service.changeStatus(91L, StandardStatus.DISABLED.name(), "tester"));
    var order = inOrder(revision, standards);
    order.verify(revision).advanceBeforeMutation();
    order.verify(standards).findByIdForUpdate(91L);

    org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
        () -> service.delete(91L, "tester"));
    order.verify(revision).advanceBeforeMutation();
    order.verify(standards).findByIdForUpdate(91L);
  }
}
