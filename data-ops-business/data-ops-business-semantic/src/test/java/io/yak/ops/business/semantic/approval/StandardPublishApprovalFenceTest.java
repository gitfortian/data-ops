package io.yak.ops.business.semantic.approval;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.approval.api.ApprovalDecision;
import io.yak.ops.business.semantic.catalog.StandardCatalogService;
import org.junit.jupiter.api.Test;

/** The STANDARD_PUBLISH callback must not acquire standard locks ahead of revision. */
class StandardPublishApprovalFenceTest {

  @Test
  void delegatesFrozenVersionToSingleTransactionalRevisionFirstServiceCall() {
    var service = mock(StandardCatalogService.class);
    var decision = mock(ApprovalDecision.class);
    when(decision.payloadJson()).thenReturn("{\"standardId\":91,\"standardVersion\":8}");
    when(decision.applicant()).thenReturn("alice");
    new StandardPublishApprovalHandler(service).onApproved(decision);

    verify(service).enableApprovedVersion(91L, 8, "alice");
    verify(service, never()).lockDefinition(91L);
    verify(service, never()).enableAfterApproval(91L, "alice");
  }

  @Test
  void badVersionOrMissingIdentityNeverCallsTheService() {
    for (String payload : new String[] {
        "{\"standardId\":91,\"standardVersion\":0}",
        "{\"standardId\":91,\"standardVersion\":\"8\"}",
        "{\"standardId\":91}",
        "{\"standardVersion\":8}"
    }) {
      var service = mock(StandardCatalogService.class);
      var decision = mock(ApprovalDecision.class);
      when(decision.payloadJson()).thenReturn(payload);
      assertThrows(RuntimeException.class,
          () -> new StandardPublishApprovalHandler(service).onApproved(decision));
      verifyNoInteractions(service);
    }
  }
}
