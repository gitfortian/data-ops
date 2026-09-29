package io.yak.ops.business.quality.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.quality.dao.mapper.QualityMonitorMapper;
import io.yak.ops.business.quality.dao.mapper.QualityTableAssetMapper;
import io.yak.ops.core.project.CurrentProject;
import org.junit.jupiter.api.Test;

class QualityDataSourceReferenceProviderTest {

  private final QualityTableAssetMapper tableAssetMapper = mock(QualityTableAssetMapper.class);
  private final QualityMonitorMapper monitorMapper = mock(QualityMonitorMapper.class);
  private final CurrentProject currentProject = mock(CurrentProject.class);
  private final QualityDataSourceReferenceProvider provider =
      new QualityDataSourceReferenceProvider(tableAssetMapper, monitorMapper, currentProject);

  @Test
  void sumsAssetsAndMonitors() {
    when(currentProject.requireProjectId()).thenReturn(1L);
    when(tableAssetMapper.selectCount(any())).thenReturn(2L);
    when(monitorMapper.selectCount(any())).thenReturn(3L);
    assertThat(provider.countReferences(42L)).isEqualTo(5L);
  }

  @Test
  void treatsNullCountsAsZero() {
    when(currentProject.requireProjectId()).thenReturn(1L);
    when(tableAssetMapper.selectCount(any())).thenReturn(null);
    when(monitorMapper.selectCount(any())).thenReturn(null);
    assertThat(provider.countReferences(42L)).isZero();
  }

  @Test
  void moduleNameIsHumanReadable() {
    assertThat(provider.moduleName()).isEqualTo("数据质量");
  }
}
