package io.yak.ops.business.semantic.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.semantic.repository.SemanticDomainRepository;
import io.yak.ops.business.semantic.repository.SemanticProcessRepository;
import io.yak.ops.business.semantic.repository.SemanticPublicationRevisionService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** R1 T2 source-bound domain writes must take the project revision first. */
class BusinessDomainRevisionLockOrderTest {
  @Test
  void movingDomainMustLockRevisionBeforeTheTree() {
    var domains = mock(SemanticDomainRepository.class);
    var processes = mock(SemanticProcessRepository.class);
    var revision = mock(SemanticPublicationRevisionService.class);
    when(domains.findById(701L)).thenReturn(Optional.empty());
    var service = new BusinessDomainService(domains, processes,
        mock(BusinessAuditService.class), List.of(), revision);

    assertThrows(RuntimeException.class, () -> service.move(701L, 702L, 1, "alice"));
    var ordered = inOrder(revision, domains);
    ordered.verify(revision).advanceBeforeMutation();
    ordered.verify(domains).lockTree();
  }

  @Test
  void deletingDomainChecksRevisionBeforeItsReferences() {
    var domains = mock(SemanticDomainRepository.class);
    var revision = mock(SemanticPublicationRevisionService.class);
    when(domains.findById(701L)).thenReturn(Optional.empty());
    var service = new BusinessDomainService(domains, mock(SemanticProcessRepository.class),
        mock(BusinessAuditService.class), List.of(), revision);
    assertThrows(RuntimeException.class, () -> service.delete(701L));
    var ordered = inOrder(revision, domains);
    ordered.verify(revision).advanceBeforeMutation();
    ordered.verify(domains).findById(701L);
  }
}
