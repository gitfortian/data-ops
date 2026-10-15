package io.yak.ops.business.sync.offline.definition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.yak.ops.core.modeling.OdsSyncHandoffVerifier;
import io.yak.ops.core.project.CurrentProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class OfflineOdsHandoffGuardTest {
  private CurrentProject project;
  private OdsSyncHandoffVerifier owner;
  private ObjectProvider<OdsSyncHandoffVerifier> provider;
  private OfflineOdsHandoffGuard guard;

  @SuppressWarnings("unchecked")
  @BeforeEach void setup() {
    project = mock(CurrentProject.class);
    owner = mock(OdsSyncHandoffVerifier.class);
    provider = mock(ObjectProvider.class);
    when(project.requireProjectId()).thenReturn(23L);
    when(provider.getIfAvailable()).thenReturn(owner);
    guard = new OfflineOdsHandoffGuard(project, provider);
  }

  private static final String VALID = """
      {"basic":{"mode":"GUIDE_SINGLE"},
       "odsHandoff":{"modelId":50,"versionNo":2,"targetObservationId":99}}
      """;

  @Test void exactValidatedReferenceCallsModelingOwnerWithRealEndpoints() {
    guard.verify(VALID, 10L, "public.trade_order_detail", 20L, "ods.ods_order_detail");
    verify(owner).verify(23L, 50L, 2, 99L, 10L,
        "public.trade_order_detail", 20L, "ods.ods_order_detail");
  }

  @Test void ordinaryExistingOfflineJobsRemainUnchanged() {
    guard.verify("{\"basic\":{\"mode\":\"GUIDE_SINGLE\"}}", 10L, "a", 20L, "b");
    verifyNoInteractions(owner);
  }

  @Test void incompleteOrForgedIdsFailClosed() {
    assertThrows(IllegalArgumentException.class,
        () -> guard.verify("{\"odsHandoff\":{\"modelId\":50}}", 10L, "a", 20L, "b"));
    assertThrows(IllegalArgumentException.class,
        () -> guard.verify(VALID.replace("\"versionNo\":2", "\"versionNo\":0"),
            10L, "a", 20L, "b"));
    assertThrows(IllegalArgumentException.class,
        () -> guard.verify(VALID, 10L, "", 20L, "b"));
    verifyNoInteractions(owner);
  }

  @Test void unavailableModelingOwnerCannotAuthorizePublication() {
    when(provider.getIfAvailable()).thenReturn(null);
    assertThrows(IllegalStateException.class,
        () -> guard.verify(VALID, 10L, "public.trade_order_detail", 20L, "ods.ods_order_detail"));
    verifyNoInteractions(owner);
  }

  @Test void ownerRejectionPropagatesAndCannotBeDowngradedToSuccess() {
    doThrow(new IllegalArgumentException("stale Catalog")).when(owner)
        .verify(anyLong(), anyLong(), anyInt(), anyLong(), anyLong(), anyString(),
            anyLong(), anyString());
    assertThrows(IllegalArgumentException.class,
        () -> guard.verify(VALID, 10L, "public.trade_order_detail", 20L, "ods.ods_order_detail"));
  }
  @Test void frozenDefinitionVerifiesExactEndpointsNotEditableTaskDraft() {
    var frozen = """
        {"basic":{"mode":"GUIDE_SINGLE"},
         "source":{"dataSourceId":"10","config":{"table":"public.trade_order_detail"}},
         "sink":{"dataSourceId":"20","config":{"table":"ods.ods_order_detail"}},
         "odsHandoff":{"modelId":50,"versionNo":2,"targetObservationId":99}}
        """;
    guard.verifyFrozen(frozen);
    verify(owner).verify(23L, 50L, 2, 99L, 10L,
        "public.trade_order_detail", 20L, "ods.ods_order_detail");
  }

  @Test void frozenDefinitionRejectsMissingEndpointsAndUnsupportedMultitableMode() {
    assertThrows(IllegalArgumentException.class,
        () -> guard.verifyFrozen(VALID)); // has a handoff but no endpoints
    assertThrows(IllegalArgumentException.class,
        () -> guard.verifyFrozen(VALID.replace("GUIDE_SINGLE", "GUIDE_MULTI")));
    verifyNoInteractions(owner);
  }

}
