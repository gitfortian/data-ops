package io.yak.ops.business.modeling.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.dao.mapper.ModelingColumnMappingMapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper;
import io.yak.ops.core.project.CurrentProject;
import org.junit.jupiter.api.Test;

class ModelingDataSourceReferenceProviderTest {

  private final ModelingModelMapper modelMapper = mock(ModelingModelMapper.class);
  private final ModelingColumnMappingMapper mappingMapper =
      mock(ModelingColumnMappingMapper.class);
  private final CurrentProject currentProject = mock(CurrentProject.class);
  private final ModelingDataSourceReferenceProvider provider =
      new ModelingDataSourceReferenceProvider(modelMapper, mappingMapper, currentProject);

  @Test
  void sumsLiveModelsAndColumnMappings() {
    when(currentProject.requireProjectId()).thenReturn(1L);
    when(modelMapper.selectCount(any())).thenReturn(6L);
    when(mappingMapper.selectCount(any())).thenReturn(1L);
    assertThat(provider.countReferences(42L)).isEqualTo(7L);
  }

  @Test
  void moduleNameIsHumanReadable() {
    assertThat(provider.moduleName()).isEqualTo("数据建模");
  }
}
