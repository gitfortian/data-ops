package io.yak.ops.business.semantic.field;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.semantic.repository.SemanticFieldRepository;
import io.yak.ops.business.semantic.repository.SemanticProcessFieldRepository;
import io.yak.ops.business.semantic.repository.SemanticProcessRepository;
import io.yak.ops.business.semantic.repository.SemanticPublicationRevisionService;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** T2 development evidence: project revision is acquired before field read/write. */
class SemanticFieldRevisionLockOrderTest {

  @Test
  void changeStatusAndDeleteAcquireProjectRevisionBeforeFieldLookup() {
    var fields = mock(SemanticFieldRepository.class);
    var processes = mock(SemanticProcessFieldRepository.class);
    var processRepository = mock(SemanticProcessRepository.class);
    var standards = mock(SemanticStandardRepository.class);
    var audit = mock(BusinessAuditService.class);
    var revision = mock(SemanticPublicationRevisionService.class);
    when(fields.findById(801L)).thenReturn(Optional.empty());
    var service = new SemanticFieldService(fields, processes, processRepository,
        standards, audit, List.of(), revision);

    org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
        () -> service.changeStatus(801L, "DISABLED", "tester"));
    var order = inOrder(revision, fields);
    order.verify(revision).advanceBeforeMutation();
    order.verify(fields).findById(801L);

    org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
        () -> service.delete(801L));
    order.verify(revision).advanceBeforeMutation();
    order.verify(fields).findById(801L);
  }
}
