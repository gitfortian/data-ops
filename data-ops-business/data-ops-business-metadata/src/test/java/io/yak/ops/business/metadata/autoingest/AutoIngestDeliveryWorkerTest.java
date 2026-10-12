package io.yak.ops.business.metadata.autoingest;

import io.yak.ops.business.metadata.dao.model.AutoIngestEvent;

import io.yak.ops.business.metadata.dao.mapper.AutoIngestEventMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.asset.api.MetadataAutoIngestApi;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AutoIngestDeliveryWorkerTest {
  private final AutoIngestEventMapper events = mock(AutoIngestEventMapper.class);
  private final MetadataAutoIngestApi asset = mock(MetadataAutoIngestApi.class);
  private final ProjectContextScope scope = mock(ProjectContextScope.class);
  private final AutoIngestDeliveryWorker worker =
      new AutoIngestDeliveryWorker(events, asset, scope);

  @BeforeEach
  void setup() {
    doAnswer(invocation -> {
      Runnable task = invocation.getArgument(1);
      task.run();
      return null;
    }).when(scope).run(any(ProjectContext.class), any(Runnable.class));
  }

  private AutoIngestEvent ticket(int priorAttempts) {
    AutoIngestEvent event = new AutoIngestEvent();
    event.setId(100L);
    event.setProjectId(42L);
    event.setRunId(9L);
    event.setEntityId(7L);
    event.setAttemptCount(priorAttempts);
    return event;
  }

  @Test
  void acknowledgesOnlyAfterScopedOwnerReceipt() {
    AutoIngestEvent e = ticket(0);
    when(events.claim(eq(100L), anyString(), any(), any())).thenReturn(1);
    when(events.succeed(eq(100L), anyString(), any())).thenReturn(1);
    when(asset.ingestCommittedMetadataTable(42L, 7L, "metadata-auto-ingest"))
        .thenReturn(new MetadataAutoIngestApi.ScopedIngestReceipt(
            42L, 7L, "source-key", 1, 0, 0));
    worker.deliver(e);
    verify(asset).ingestCommittedMetadataTable(42L, 7L, "metadata-auto-ingest");
    verify(events).succeed(eq(100L), anyString(), any());
    verify(events, never()).fail(any(), any(), any(), any(), any(), any());
  }

  @Test
  void lostClaimNeverWritesToAssetOwner() {
    when(events.claim(eq(100L), anyString(), any(), any())).thenReturn(0);
    worker.deliver(ticket(0));
    verifyNoInteractions(asset);
    verify(events, never()).succeed(any(), any(), any());
  }

  @Test
  void failedDeliveryRetriesAndExhaustionBecomesDeadLetter() {
    when(events.claim(eq(100L), anyString(), any(), any())).thenReturn(1);
    when(asset.ingestCommittedMetadataTable(42L, 7L, "metadata-auto-ingest"))
        .thenThrow(new IllegalStateException("source unavailable"));
    worker.deliver(ticket(0));
    verify(events).fail(eq(100L), anyString(), eq("RETRY"), any(),
        eq("source unavailable"), any());
    worker.deliver(ticket(7));
    verify(events).fail(eq(100L), anyString(), eq("DEAD"), any(),
        eq("source unavailable"), any());
  }

  @Test
  void impossibleProjectEvidenceDoesNotGetAClaimOrImplicitSuccess() {
    AutoIngestEvent e = ticket(0);
    e.setProjectId(null);
    worker.deliver(e);
    verifyNoInteractions(events, asset);
  }
}
