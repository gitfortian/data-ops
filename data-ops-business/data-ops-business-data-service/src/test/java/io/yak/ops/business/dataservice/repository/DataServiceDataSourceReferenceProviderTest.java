package io.yak.ops.business.dataservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.dao.mapper.DataServiceApiMapper;
import io.yak.ops.core.project.CurrentProject;
import org.junit.jupiter.api.Test;

class DataServiceDataSourceReferenceProviderTest {

  private final DataServiceApiMapper apiMapper = mock(DataServiceApiMapper.class);
  private final CurrentProject currentProject = mock(CurrentProject.class);
  private final DataServiceDataSourceReferenceProvider provider =
      new DataServiceDataSourceReferenceProvider(apiMapper, currentProject);

  @Test
  void countsApisTargetingTheDatasource() {
    when(currentProject.requireProjectId()).thenReturn(1L);
    when(apiMapper.selectCount(any())).thenReturn(2L);
    assertThat(provider.countReferences(42L)).isEqualTo(2L);
  }

  @Test
  void moduleNameIsHumanReadable() {
    assertThat(provider.moduleName()).isEqualTo("数据服务");
  }
}
