package io.yak.ops.business.mdm.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.mdm.dao.mapper.MdmCollectLinkMapper;
import io.yak.ops.business.mdm.dao.mapper.MdmSourceMapper;
import io.yak.ops.core.project.CurrentProject;
import org.junit.jupiter.api.Test;

class MdmDataSourceReferenceProviderTest {

  private final MdmSourceMapper sourceMapper = mock(MdmSourceMapper.class);
  private final MdmCollectLinkMapper collectLinkMapper = mock(MdmCollectLinkMapper.class);
  private final CurrentProject currentProject = mock(CurrentProject.class);
  private final MdmDataSourceReferenceProvider provider =
      new MdmDataSourceReferenceProvider(sourceMapper, collectLinkMapper, currentProject);

  @Test
  void sumsSourcesAndCollectLinks() {
    when(currentProject.requireProjectId()).thenReturn(1L);
    when(sourceMapper.selectCount(any())).thenReturn(2L);
    when(collectLinkMapper.selectCount(any())).thenReturn(1L);
    assertThat(provider.countReferences(42L)).isEqualTo(3L);
  }

  @Test
  void moduleNameIsHumanReadable() {
    assertThat(provider.moduleName()).isEqualTo("主数据");
  }
}
