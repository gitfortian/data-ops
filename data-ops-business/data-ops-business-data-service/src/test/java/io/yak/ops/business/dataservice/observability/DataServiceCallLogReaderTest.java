package io.yak.ops.business.dataservice.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.domain.InvocationRecord;
import io.yak.ops.business.dataservice.repository.DataServiceCallLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DataServiceCallLogReaderTest {

  @Test
  void findsPersistedInvocationOutsideRecentWindowWithLosslessBigintAndRevision() {
    DataServiceCallLogRepository repository = mock(DataServiceCallLogRepository.class);
    long invocationId = 9007199254740993L;
    InvocationRecord row = new InvocationRecord(
        invocationId, 42L, 7L, "Orders", "/orders", "API_KEY", 14L, 19L,
        "automation", "sk_123", 9007199254740995L, 12, "{\"page\":1}",
        true, 25L, 2, null, LocalDateTime.of(2025, 6, 1, 9, 0));
    when(repository.findByApiAndId(7L, invocationId)).thenReturn(Optional.of(row));

    InvocationEvidenceView result =
        new DataServiceCallLogReader(repository).findByApiAndId(7L, invocationId);

    assertThat(result.state()).isEqualTo(InvocationEvidenceView.State.FOUND);
    assertThat(result.record().id()).isEqualTo("9007199254740993");
    assertThat(result.record().apiId()).isEqualTo("7");
    assertThat(result.record().sourceRevisionId()).isEqualTo("9007199254740995");
    assertThat(result.record().consumerId()).isEqualTo("19");
    assertThat(result.record().apiKeyId()).isEqualTo("14");
    assertThat(result.record().sourceRevisionNo()).isEqualTo(12);
    assertThat(result.record().success()).isTrue();
    verify(repository).findByApiAndId(7L, invocationId);
  }

  @Test
  void missingOrForeignAuditIsNotFoundWithoutInventingAnExecution() {
    DataServiceCallLogRepository repository = mock(DataServiceCallLogRepository.class);
    when(repository.findByApiAndId(7L, 901L)).thenReturn(Optional.empty());

    InvocationEvidenceView result = new DataServiceCallLogReader(repository).findByApiAndId(7L, 901L);

    assertThat(result.state()).isEqualTo(InvocationEvidenceView.State.NOT_FOUND);
    assertThat(result.record()).isNull();
    verify(repository).findByApiAndId(7L, 901L);
  }

  @Test
  void invalidRequestedIdsFailBeforeRepositoryAccess() {
    DataServiceCallLogRepository repository = mock(DataServiceCallLogRepository.class);
    DataServiceCallLogReader reader = new DataServiceCallLogReader(repository);
    assertThatThrownBy(() -> reader.findByApiAndId(0L, 2L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> reader.findByApiAndId(7L, -5L))
        .isInstanceOf(IllegalArgumentException.class);
    org.mockito.Mockito.verifyNoInteractions(repository);
  }

  @Test
  void readsOnlyTheRequestedServiceWithBoundedLimit() {
    DataServiceCallLogRepository repository = mock(DataServiceCallLogRepository.class);
    InvocationRecord record = new InvocationRecord(
        1L, 7L, "Orders", "/orders", "PUBLIC", null, null, null,
        "{}", true, 10L, 1, null, LocalDateTime.now());
    when(repository.recentByApi(7L, 200)).thenReturn(List.of(record));

    DataServiceCallLogReader reader = new DataServiceCallLogReader(repository);

    assertThat(reader.recentByApi(7L, 999)).containsExactly(record);
    verify(repository).recentByApi(7L, 200);
  }
  @Test
  void successfulUsageWindowUsesDedicatedPersistedSourceQueryNotMixedCallDiagnostics() {
    DataServiceCallLogRepository repository = mock(DataServiceCallLogRepository.class);
    InvocationRecord successful = new InvocationRecord(
        3L, 7L, "Orders", "/orders", "API_KEY", null, null, null,
        "{}", true, 10L, 1, null, LocalDateTime.now());
    when(repository.recentSuccessfulByApi(7L, 200)).thenReturn(List.of(successful));

    DataServiceCallLogReader reader = new DataServiceCallLogReader(repository);

    assertThat(reader.recentSuccessfulByApi(7L, 999)).containsExactly(successful);
    verify(repository).recentSuccessfulByApi(7L, 200);
    org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).recentByApi(7L, 200);
  }

  @Test
  void successfulSourceReaderBoundsEmptyOrNegativeLimitBeforeDelegation() {
    DataServiceCallLogRepository repository = mock(DataServiceCallLogRepository.class);
    DataServiceCallLogReader reader = new DataServiceCallLogReader(repository);

    assertThat(reader.recentSuccessfulByApi(7L, 0)).isEmpty();
    verify(repository).recentSuccessfulByApi(7L, 1);
  }

}
