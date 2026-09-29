package io.yak.ops.business.sync.offline.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.dao.mapper.OfflineJobDefinitionMapper;
import io.yak.ops.core.project.CurrentProject;
import org.junit.jupiter.api.Test;

class OfflineSyncDataSourceReferenceProviderTest {

  private final OfflineJobDefinitionMapper mapper = mock(OfflineJobDefinitionMapper.class);
  private final CurrentProject currentProject = mock(CurrentProject.class);
  private final OfflineSyncDataSourceReferenceProvider provider =
      new OfflineSyncDataSourceReferenceProvider(mapper, currentProject);

  @Test
  void moduleNameIsHumanReadable() {
    assertThat(provider.moduleName()).isEqualTo("离线同步");
  }

  @Test
  void countsJobDefinitionsUsingSourceEitherAsSourceOrSink() {
    when(currentProject.requireProjectId()).thenReturn(1L);
    when(mapper.selectCount(any())).thenReturn(4L);
    assertThat(provider.countReferences(42L)).isEqualTo(4L);
  }

  @Test
  void nullDataSourceIdShortCircuitsWithoutQuery() {
    assertThat(provider.countReferences(null)).isZero();
    verify(mapper, never()).selectCount(any());
  }

  @Test
  void nullCountFromMapperIsTreatedAsZero() {
    when(currentProject.requireProjectId()).thenReturn(1L);
    when(mapper.selectCount(any())).thenReturn(null);
    assertThat(provider.countReferences(42L)).isZero();
  }
}
