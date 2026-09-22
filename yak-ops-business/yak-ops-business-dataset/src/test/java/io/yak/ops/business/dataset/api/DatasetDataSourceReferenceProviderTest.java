package io.yak.ops.business.dataset.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.dao.mapper.DatasetMapper;
import io.yak.ops.business.dataset.dao.mapper.DatasetVersionMapper;
import io.yak.ops.core.project.CurrentProject;
import org.junit.jupiter.api.Test;

class DatasetDataSourceReferenceProviderTest {

  private final DatasetMapper datasetMapper = mock(DatasetMapper.class);
  private final DatasetVersionMapper versionMapper = mock(DatasetVersionMapper.class);
  private final CurrentProject currentProject = mock(CurrentProject.class);
  private final DatasetDataSourceReferenceProvider provider =
      new DatasetDataSourceReferenceProvider(datasetMapper, versionMapper, currentProject);

  @Test
  void sumsDraftAndVersionReferencesByStringId() {
    when(currentProject.requireProjectId()).thenReturn(7L);
    when(datasetMapper.selectCount(any())).thenReturn(1L);
    when(versionMapper.selectCount(any())).thenReturn(4L);
    assertThat(provider.countReferences(42L)).isEqualTo(5L);
  }

  @Test
  void moduleNameIsHumanReadable() {
    assertThat(provider.moduleName()).isEqualTo("数据集");
  }
}
