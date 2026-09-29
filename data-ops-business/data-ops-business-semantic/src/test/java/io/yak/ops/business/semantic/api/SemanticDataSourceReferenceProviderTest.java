package io.yak.ops.business.semantic.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.semantic.dao.mapper.SemanticLayerMapper;
import io.yak.ops.business.semantic.dao.mapper.SemanticProcessSourceMapper;
import io.yak.ops.core.project.CurrentProject;
import org.junit.jupiter.api.Test;

class SemanticDataSourceReferenceProviderTest {

  private final SemanticProcessSourceMapper processSourceMapper =
      mock(SemanticProcessSourceMapper.class);
  private final SemanticLayerMapper layerMapper = mock(SemanticLayerMapper.class);
  private final CurrentProject currentProject = mock(CurrentProject.class);
  private final SemanticDataSourceReferenceProvider provider =
      new SemanticDataSourceReferenceProvider(processSourceMapper, layerMapper, currentProject);

  @Test
  void sumsProcessSourcesAndLayers() {
    when(currentProject.requireProjectId()).thenReturn(1L);
    when(processSourceMapper.selectCount(any())).thenReturn(5L);
    when(layerMapper.selectCount(any())).thenReturn(2L);
    assertThat(provider.countReferences(42L)).isEqualTo(7L);
  }

  @Test
  void moduleNameIsHumanReadable() {
    assertThat(provider.moduleName()).isEqualTo("语义中心");
  }
}
