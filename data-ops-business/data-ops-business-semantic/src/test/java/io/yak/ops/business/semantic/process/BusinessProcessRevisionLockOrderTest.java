package io.yak.ops.business.semantic.process;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.semantic.binding.SemanticProcessBindingService;
import io.yak.ops.business.semantic.field.SemanticFieldService;
import io.yak.ops.business.semantic.repository.SemanticDomainRepository;
import io.yak.ops.business.semantic.repository.SemanticProcessRepository;
import io.yak.ops.business.semantic.repository.SemanticPublicationRevisionService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** R1 T2 source-bound process writes lock the Semantic project first. */
class BusinessProcessRevisionLockOrderTest {
  @Test
  void creatingProcessMustLockRevisionBeforeDomainReferenceLookup() {
    var processes = mock(SemanticProcessRepository.class);
    var domains = mock(SemanticDomainRepository.class);
    var revision = mock(SemanticPublicationRevisionService.class);
    when(domains.findById(701L)).thenReturn(Optional.empty());
    var service = new BusinessProcessService(processes, domains,
        mock(SemanticFieldService.class), mock(SemanticProcessBindingService.class),
        mock(BusinessAuditService.class), List.of(), revision);

    assertThrows(RuntimeException.class,
        () -> service.create(701L, "place_order", "下单", "订单级", "FACT",
            null, null, null, "alice"));
    var order = inOrder(revision, domains);
    order.verify(revision).advanceBeforeMutation();
    order.verify(domains).findById(701L);
  }

  @Test
  void deletingProcessMustLockRevisionBeforeReferenceChecking() {
    var processes = mock(SemanticProcessRepository.class);
    var revision = mock(SemanticPublicationRevisionService.class);
    when(processes.findById(701L)).thenReturn(Optional.empty());
    var service = new BusinessProcessService(processes, mock(SemanticDomainRepository.class),
        mock(SemanticFieldService.class), mock(SemanticProcessBindingService.class),
        mock(BusinessAuditService.class), List.of(), revision);

    assertThrows(RuntimeException.class, () -> service.delete(701L));
    var order = inOrder(revision, processes);
    order.verify(revision).advanceBeforeMutation();
    order.verify(processes).findById(701L);
  }
}
