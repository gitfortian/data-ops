package io.yak.ops.business.metadata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metadata.dao.mapper.MdCollectJobMapper;
import io.yak.ops.core.project.CurrentProject;
import org.junit.jupiter.api.Test;

class MetadataDataSourceReferenceProviderTest {

  private final MdCollectJobMapper collectJobMapper = mock(MdCollectJobMapper.class);
  private final CurrentProject currentProject = mock(CurrentProject.class);
  private final MetadataDataSourceReferenceProvider provider =
      new MetadataDataSourceReferenceProvider(collectJobMapper, currentProject);

  @Test
  void countsLiveCollectJobsOnTheDatasource() {
    when(currentProject.requireProjectId()).thenReturn(1L);
    when(collectJobMapper.selectCount(any())).thenReturn(3L);
    assertThat(provider.countReferences(42L)).isEqualTo(3L);
  }

  @Test
  void moduleNameIsHumanReadable() {
    assertThat(provider.moduleName()).isEqualTo("元数据采集");
  }
}
