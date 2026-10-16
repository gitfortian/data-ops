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


  @Test void escapedHandoffFieldNameCannotBypassPublicationOrExecutionGuard() {
    String escaped = """
        {"basic":{"mode":"GUIDE_SINGLE"},
         "source":{"dataSourceId":"10","config":{"table":"public.trade_order_detail"}},
         "sink":{"dataSourceId":"20","config":{"table":"ods.ods_order_detail"}},
         "\\u006fdsHandoff":{"modelId":50,"versionNo":2,"targetObservationId":99}}
        """;
    assertTrue(OfflineOdsHandoffGuard.requiresVerification(escaped));
    guard.verifyFrozen(escaped);
    verify(owner).verify(23L, 50L, 2, 99L, 10L,
        "public.trade_order_detail", 20L, "ods.ods_order_detail");
  }

  @Test void frozenOdsTaskRejectsAutomaticDdlAndUnreviewedWriteMode() {
    String frozen = """
        {"basic":{"mode":"GUIDE_SINGLE"},
         "source":{"dataSourceId":"10","config":{"table":"public.trade_order_detail"}},
         "sink":{"dataSourceId":"20","config":{
           "table":"ods.ods_order_detail","autoCreateTable":true,"writeMode":"APPEND"}},
         "odsHandoff":{"modelId":50,"versionNo":2,"targetObservationId":99}}
        """;
    assertThrows(IllegalArgumentException.class, () -> guard.verifyFrozen(frozen));
    String overwrite = frozen.replace("\"autoCreateTable\":true", "\"autoCreateTable\":false")
        .replace("\"writeMode\":\"APPEND\"", "\"writeMode\":\"OVERWRITE\"");
    assertThrows(IllegalArgumentException.class, () -> guard.verifyFrozen(overwrite));
    String upsert = overwrite.replace("OVERWRITE", "UPSERT");
    assertThrows(IllegalArgumentException.class, () -> guard.verifyFrozen(upsert));
    verifyNoInteractions(owner);
  }

  @Test void hiddenSinkOptionsCannotBypassReviewedAppendOnlyContract() {
    String hiddenAutoDdl = """
        {"basic":{"mode":"GUIDE_SINGLE"},
         "source":{"dataSourceId":"10","config":{"table":"public.trade_order_detail"}},
         "sink":{"dataSourceId":"20","config":{
           "table":"ods.ods_order_detail",
           "connectorOptions":{"autoCreateTable":true}}},
         "odsHandoff":{"modelId":50,"versionNo":2,"targetObservationId":99}}
        """;
    assertThrows(IllegalArgumentException.class, () -> guard.verifyFrozen(hiddenAutoDdl));

    String hiddenOverwrite = """
        {"basic":{"mode":"GUIDE_SINGLE"},
         "source":{"dataSourceId":"10","config":{"table":"public.trade_order_detail"}},
         "sink":{"dataSourceId":"20","options":{"write_mode":"OVERWRITE"},
           "config":{"table":"ods.ods_order_detail"}},
         "odsHandoff":{"modelId":50,"versionNo":2,"targetObservationId":99}}
        """;
    assertThrows(IllegalArgumentException.class, () -> guard.verifyFrozen(hiddenOverwrite));
    verifyNoInteractions(owner);
  }

  @Test void sqlOrMultiTableConfigurationCannotMasqueradeAsExactOdsTable() {
    String sourceSql = """
        {"basic":{"mode":"GUIDE_SINGLE"},
         "source":{"dataSourceId":"10","config":{
           "table":"public.trade_order_detail","sql":"select * from other_table"}},
         "sink":{"dataSourceId":"20","config":{"table":"ods.ods_order_detail"}},
         "odsHandoff":{"modelId":50,"versionNo":2,"targetObservationId":99}}
        """;
    assertThrows(IllegalArgumentException.class, () -> guard.verifyFrozen(sourceSql));
    String targetSql = """
        {"basic":{"mode":"GUIDE_SINGLE"},
         "source":{"dataSourceId":"10","config":{"table":"public.trade_order_detail"}},
         "sink":{"dataSourceId":"20","config":{
           "table":"ods.ods_order_detail","sql":"truncate table ods.ods_order_detail"}},
         "odsHandoff":{"modelId":50,"versionNo":2,"targetObservationId":99}}
        """;
    assertThrows(IllegalArgumentException.class, () -> guard.verifyFrozen(targetSql));
    verifyNoInteractions(owner);
  }

  @Test void conflictingPhysicalTableAliasCannotChangeVerifiedWriteTarget() {
    String conflicting = """
        {"basic":{"mode":"GUIDE_SINGLE"},
         "source":{"dataSourceId":"10","config":{"table":"public.trade_order_detail"}},
         "sink":{"dataSourceId":"20","table":"ods.other_table",
           "config":{"table":"ods.ods_order_detail"}},
         "odsHandoff":{"modelId":50,"versionNo":2,"targetObservationId":99}}
        """;
    assertThrows(IllegalArgumentException.class, () -> guard.verifyFrozen(conflicting));
    String hiddenTarget = """
        {"basic":{"mode":"GUIDE_SINGLE"},
         "source":{"dataSourceId":"10","config":{"table":"public.trade_order_detail"}},
         "sink":{"dataSourceId":"20","config":{
           "table":"ods.ods_order_detail", "connectorOptions":{"table":"ods.other_table"}}},
         "odsHandoff":{"modelId":50,"versionNo":2,"targetObservationId":99}}
        """;
    assertThrows(IllegalArgumentException.class, () -> guard.verifyFrozen(hiddenTarget));
    verifyNoInteractions(owner);
  }

  @Test void odsAppendReplayIsRejectedWithoutCommittedWindowReceipt() {
    String frozen = """
        {"odsHandoff":{"modelId":50,"versionNo":2,"targetObservationId":99}}
        """;
    assertThrows(IllegalStateException.class,
        () -> OfflineOdsHandoffGuard.rejectUnsafeAppendReplay(frozen));
    // Explicit null is protected intent; replay protection rejects it as unsafe.
    assertThrows(IllegalStateException.class,
        () -> OfflineOdsHandoffGuard.rejectUnsafeAppendReplay("{\"odsHandoff\":null}"));
    org.junit.jupiter.api.Assertions.assertDoesNotThrow(
        () -> OfflineOdsHandoffGuard.rejectUnsafeAppendReplay("{\"basic\":{}}"));
  }

  @Test void explicitNullHandoffCannotDowngradeProtectedFrozenJob() {
    String nullHandoff = "{\"odsHandoff\":null}";
    assertTrue(OfflineOdsHandoffGuard.requiresVerification(nullHandoff));
    assertThrows(IllegalArgumentException.class, () -> guard.verifyFrozen(nullHandoff));
    assertThrows(IllegalArgumentException.class,
        () -> guard.verify(nullHandoff, 10L, "public.trade_order_detail",
            20L, "ods.ods_order_detail"));
    verifyNoInteractions(owner);
  }

  @Test void unrelatedStringContentsCannotForgeAnOdsHandoff() {
    assertFalse(OfflineOdsHandoffGuard.requiresVerification(
        "{\"notes\":\"odsHandoff\"}"));
    assertTrue(OfflineOdsHandoffGuard.requiresVerification(
        "{\"odsHandoff\":null}"));
    assertThrows(IllegalArgumentException.class, () ->
        OfflineOdsHandoffGuard.requiresVerification("invalid JSON"));
  }

}
