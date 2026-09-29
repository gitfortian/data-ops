package io.yak.ops.business.dataset.development;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.repository.DatasetRepository;
import io.yak.ops.business.dataset.repository.DatasetRepository.DevelopmentSourceDetails;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DatasetDevelopmentSourceServiceTest {

  @Test
  void resolvesDevelopmentNodeInsideCurrentProject() {
    DatasetRepository repository = mock(DatasetRepository.class);
    when(repository.findDevelopmentSource(55L))
        .thenReturn(Optional.of(new DevelopmentSourceDetails(55L, 7L, 3)));

    var source = new DatasetDevelopmentSourceService(repository).require(55L);

    assertEquals("FOUND", source.state());
    assertEquals(55L, source.datasetId());
    assertEquals(7L, source.developmentNodeId());
    assertEquals(3, source.currentDatasetVersionNo());
  }

  @Test
  void nonDevelopmentDatasetIsExplicitlyNotApplicable() {
    DatasetRepository repository = mock(DatasetRepository.class);
    when(repository.findDevelopmentSource(55L))
        .thenReturn(Optional.of(new DevelopmentSourceDetails(55L, null, null)));

    var source = new DatasetDevelopmentSourceService(repository).require(55L);

    assertEquals("NOT_APPLICABLE", source.state());
    assertNull(source.developmentNodeId());
    assertNull(source.currentDatasetVersionNo());
  }
}
