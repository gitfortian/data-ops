package io.yak.ops.business.quality.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.repository.QualityExecutionReadRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QualityExecutionReaderTest {

  private final QualityExecutionReadRepository repository =
      mock(QualityExecutionReadRepository.class);
  private final QualityExecutionReader reader = new QualityExecutionReader(repository);

  @Test
  void findSummaryDelegatesOptionalReadWithoutChangingMissingResultSemantics() {
    Execution execution = mock(Execution.class);
    when(repository.findSummary("exec-42")).thenReturn(Optional.of(execution));

    assertThat(reader.findSummary("exec-42")).contains(execution);
    verify(repository).findSummary("exec-42");

    when(repository.findSummary("missing")).thenReturn(Optional.empty());
    assertThat(reader.findSummary("missing")).isEmpty();
  }
}
