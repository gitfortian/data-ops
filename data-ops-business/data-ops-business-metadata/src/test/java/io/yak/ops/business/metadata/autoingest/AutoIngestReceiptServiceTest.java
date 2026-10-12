package io.yak.ops.business.metadata.autoingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.yak.ops.business.metadata.dao.mapper.AutoIngestEventMapper;
import io.yak.ops.business.metadata.dao.model.AutoIngestEvent;
import org.junit.jupiter.api.Test;

/** Tenant-scoped delivery status and manual dead-letter action are separate from Asset status. */
class AutoIngestReceiptServiceTest {
  private final AutoIngestEventMapper events = mock(AutoIngestEventMapper.class);
  private final AutoIngestReceiptService receipts = new AutoIngestReceiptService(events);

  @Test
  void neverDoesNotMeanAssetIsMissing() {
    var receipt = receipts.latest(42L, 10L);
    assertThat(receipt.status()).isEqualTo("NEVER");
    assertThat(receipt.entityId()).isEqualTo(10L);
    assertThat(receipt.eventId()).isNull();
    verify(events).latest(42L, 10L);
  }

  @Test
  void receiptIsProjectScopedAndStripsInternalLeaseToken() {
    AutoIngestEvent event = new AutoIngestEvent();
    event.setId(77L);
    event.setRunId(9L);
    event.setEntityId(10L);
    event.setStatus("RETRY");
    event.setAttemptCount(3);
    event.setErrorMessage("temporary owner unavailable");
    event.setClaimToken("secret-internal-token");
    when(events.latest(42L, 10L)).thenReturn(event);
    var receipt = receipts.latest(42L, 10L);
    assertThat(receipt.status()).isEqualTo("RETRY");
    assertThat(receipt.errorMessage()).isEqualTo("temporary owner unavailable");
    assertThat(receipt.toString()).doesNotContain("secret-internal-token");
    verify(events, never()).latest(41L, 10L);
  }

  @Test
  void deadLetterRetryCannotCrossProjectOrResetOtherStatuses() {
    when(events.retryDead(eq(42L), eq(77L), any())).thenReturn(1);
    assertThat(receipts.retryDead(42L, 77L)).isTrue();
    assertThat(receipts.retryDead(41L, 77L)).isFalse();
    assertThat(receipts.retryDead(42L, -1L)).isFalse();
    verify(events, never()).retryDead(eq(42L), eq(-1L), any());
  }
}
