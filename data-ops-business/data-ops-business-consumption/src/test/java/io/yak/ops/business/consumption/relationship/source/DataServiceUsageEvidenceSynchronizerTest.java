package io.yak.ops.business.consumption.relationship.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

  @Test
  void persistedRevisionCursorProcessesAtMost200ThenContinuesToOlderSuccess() {
    DataServiceCallLogReader reader = mock(DataServiceCallLogReader.class);
    DataServiceUsageEvidenceNormalizer normalizer = mock(DataServiceUsageEvidenceNormalizer.class);
    List<InvocationRecord> first = java.util.stream.LongStream.rangeClosed(1, 200)
        .mapToObj(index -> new InvocationRecord(
            901L - index, 42L, 7L, "Orders", "/orders", "API_KEY",
            9L, 19L, "Historic", "sk_x", 9007199254740995L, 1, "{}",
            true, 10L, 1, null, LocalDateTime.of(2025, 7, 1, 12, 0)))
        .toList();
    when(reader.successfulPageByApiAndRevision(7L, 9007199254740995L, null, 200))
        .thenReturn(first);
    when(normalizer.normalize(any(InvocationRecord.class)))
        .thenReturn(UsageNormalizationResult.gap("invocation:old", "missing original consumer"));

    var sync = new DataServiceUsageEvidenceSynchronizer(reader, normalizer);
    var page1 = sync.recoverSuccessfulRevisionPage(7L, 9007199254740995L, null, 500);

    assertEquals(200, page1.results().size());
    assertEquals(701L, page1.nextBeforeInvocationId());
    assertEquals(false, page1.exhausted());
    verify(reader).successfulPageByApiAndRevision(7L, 9007199254740995L, null, 200);
    InvocationRecord older = new InvocationRecord(
        700L, 42L, 7L, "Orders", "/orders", "API_KEY",
        9L, 19L, "Historic", "sk_x", 9007199254740995L, 1, "{}",
        true, 10L, 1, null, LocalDateTime.of(2025, 7, 1, 12, 0));
    when(reader.successfulPageByApiAndRevision(7L, 9007199254740995L, 701L, 200))
        .thenReturn(List.of(older));
    var page2 = sync.recoverSuccessfulRevisionPage(7L, 9007199254740995L, 701L, 200);
    assertEquals(1, page2.results().size());
    assertEquals(null, page2.nextBeforeInvocationId());
    assertEquals(true, page2.exhausted());
  }

  @Test
  void repeatedOrOutOfOrderInvocationPageDoesNotNormalizeOrAdvance() {
    DataServiceCallLogReader reader = mock(DataServiceCallLogReader.class);
    DataServiceUsageEvidenceNormalizer normalizer = mock(DataServiceUsageEvidenceNormalizer.class);
    InvocationRecord first = mock(InvocationRecord.class);
    InvocationRecord repeated = mock(InvocationRecord.class);
    when(first.id()).thenReturn(899L);
    when(repeated.id()).thenReturn(899L);
    when(reader.successfulPageByApiAndRevision(7L, 9L, 900L, 3))
        .thenReturn(List.of(first, repeated));
    var sync = new DataServiceUsageEvidenceSynchronizer(reader, normalizer);

    assertThrows(IllegalStateException.class,
        () -> sync.recoverSuccessfulRevisionPage(7L, 9L, 900L, 3));
    verify(normalizer, never()).normalize(any(InvocationRecord.class));
  }

  @Test
  void invocationRecoveryCannotReturnRowsAtOrBeyondExclusiveCursor() {
    DataServiceCallLogReader reader = mock(DataServiceCallLogReader.class);
    DataServiceUsageEvidenceNormalizer normalizer = mock(DataServiceUsageEvidenceNormalizer.class);
    InvocationRecord future = mock(InvocationRecord.class);
    when(future.id()).thenReturn(901L);
    when(reader.successfulPageByApiAndRevision(7L, 9L, 900L, 3))
        .thenReturn(List.of(future));
    var sync = new DataServiceUsageEvidenceSynchronizer(reader, normalizer);

    assertThrows(IllegalStateException.class,
        () -> sync.recoverSuccessfulRevisionPage(7L, 9L, 900L, 3));
    verify(normalizer, never()).normalize(any(InvocationRecord.class));
  }

  @Test
  void missingDurableInvocationIdDoesNotCreateFakeContinuationOrNormalizeAnything() {
    DataServiceCallLogReader reader = mock(DataServiceCallLogReader.class);
    DataServiceUsageEvidenceNormalizer normalizer = mock(DataServiceUsageEvidenceNormalizer.class);
    InvocationRecord corrupt = mock(InvocationRecord.class);
    when(reader.successfulPageByApiAndRevision(7L, 9L, null, 200))
        .thenReturn(List.of(corrupt));
    var sync = new DataServiceUsageEvidenceSynchronizer(reader, normalizer);

    assertThrows(IllegalStateException.class,
        () -> sync.recoverSuccessfulRevisionPage(7L, 9L, null, 200));
    verify(normalizer, never()).normalize(any(InvocationRecord.class));
  }

}
