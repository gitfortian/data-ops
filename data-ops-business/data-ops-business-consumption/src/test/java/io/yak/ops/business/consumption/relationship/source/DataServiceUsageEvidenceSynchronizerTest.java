package io.yak.ops.business.consumption.relationship.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.consumption.relationship.UsageNormalizationResult;
import io.yak.ops.business.consumption.relationship.UsageNormalizationState;
import io.yak.ops.business.dataservice.domain.InvocationRecord;
import io.yak.ops.business.dataservice.observability.DataServiceCallLogReader;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataServiceUsageEvidenceSynchronizerTest {

  @Test
  void apiProductReconcilesLatestSuccessfulSourceWindowRatherThanMixedFailureDiagnostics() {
    DataServiceCallLogReader reader = mock(DataServiceCallLogReader.class);
    DataServiceUsageEvidenceNormalizer normalizer = mock(DataServiceUsageEvidenceNormalizer.class);
    InvocationRecord successful = new InvocationRecord(
        9007199254740993L, 42L, 7L,
        "Orders", "/orders", "API_KEY", 9L, 19L,
        "Team A", "sk_x", 9007199254740995L, 7, "{}",
        true, 15L, 1, null, LocalDateTime.of(2026, 10, 8, 12, 0));
    when(reader.recentSuccessfulByApi(7L, 200)).thenReturn(List.of(successful));
    when(normalizer.normalize(successful))
        .thenReturn(UsageNormalizationResult.gap("invocation:9007199254740993", "missing consumer mapping"));

    var synchronizer = new DataServiceUsageEvidenceSynchronizer(reader, normalizer);
    var normalized = synchronizer.synchronizeRecentByProduct(7L, 999);

    assertEquals(1, normalized.size());
    assertEquals(UsageNormalizationState.GAP, normalized.getFirst().state());
    verify(reader).recentSuccessfulByApi(7L, 200);
    verify(reader, never()).recentByApi(anyLong(), anyInt());
    verify(normalizer).normalize(successful);
  }

  @Test
  void emptySuccessfulWindowRemainsEmptyWithoutTreatingFailedCallsAsUsage() {
    DataServiceCallLogReader reader = mock(DataServiceCallLogReader.class);
    DataServiceUsageEvidenceNormalizer normalizer = mock(DataServiceUsageEvidenceNormalizer.class);
    when(reader.recentSuccessfulByApi(7L, 2)).thenReturn(List.of());

    var normalized = new DataServiceUsageEvidenceSynchronizer(reader, normalizer)
        .synchronizeRecentByProduct(7L, 2);

    assertEquals(List.of(), normalized);
    verify(reader).recentSuccessfulByApi(7L, 2);
    verify(normalizer, never()).normalize(any(InvocationRecord.class));
  }
  @Test
  void exactOldRevisionRecoversSuccessfulAuditBeyondProductWideWindow() {
    DataServiceCallLogReader reader = mock(DataServiceCallLogReader.class);
    DataServiceUsageEvidenceNormalizer normalizer = mock(DataServiceUsageEvidenceNormalizer.class);
    InvocationRecord oldSuccess = new InvocationRecord(
        9007199254740993L, 42L, 7L, "Orders", "/orders", "API_KEY",
        9L, 19L, "Historic client", "sk_x", 9007199254740995L, 1, "{}",
        true, 15L, 1, null, LocalDateTime.of(2025, 7, 1, 12, 0));
    when(reader.recentSuccessfulByApiAndRevision(7L, 9007199254740995L, 200))
        .thenReturn(List.of(oldSuccess));
    when(normalizer.normalize(oldSuccess))
        .thenReturn(UsageNormalizationResult.gap(
            "invocation:9007199254740993", "historic consumer mapping missing"));

    var results = new DataServiceUsageEvidenceSynchronizer(reader, normalizer)
        .synchronizeRecentByProductAndRevision(7L, 9007199254740995L, 500);

    assertEquals(1, results.size());
    assertEquals(UsageNormalizationState.GAP, results.getFirst().state());
    verify(reader).recentSuccessfulByApiAndRevision(7L, 9007199254740995L, 200);
    verify(reader, never()).recentSuccessfulByApi(anyLong(), anyInt());
    verify(normalizer).normalize(oldSuccess);
  }

}
