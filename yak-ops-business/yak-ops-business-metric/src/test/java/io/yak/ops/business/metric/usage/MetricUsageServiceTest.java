package io.yak.ops.business.metric.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.metric.api.MetricUsageApi;
import io.yak.ops.business.metric.dao.mapper.MetricUsageMapper;
import io.yak.ops.common.bean.po.metric.MetricUsagePO;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 01 消费接线:usage SPI 的绑定同步/撤销/聚合语义。 */
class MetricUsageServiceTest {

  private MetricUsageMapper mapper;
  private CurrentProject currentProject;
  private MetricUsageService service;

  @BeforeEach
  void setUp() {
    mapper = mock(MetricUsageMapper.class);
    currentProject = mock(CurrentProject.class);
    when(currentProject.requireProjectId()).thenReturn(7L);
    service = new MetricUsageService(mapper, currentProject);
  }

  @Test
  void syncBindingsRevokesOldRowsThenInsertsOnePerMetric() {
    service.syncBindings("DATASET", 42L, "销售明细集", List.of(101L, 102L));

    verify(mapper).delete(any(LambdaQueryWrapper.class));
    ArgumentCaptor<MetricUsagePO> inserted = ArgumentCaptor.forClass(MetricUsagePO.class);
    verify(mapper, times(2)).insert(inserted.capture());
    assertThat(inserted.getAllValues())
        .allSatisfy(po -> {
          assertThat(po.getProjectId()).isEqualTo(7L);
          assertThat(po.getUsageType()).isEqualTo("DATASET");
          assertThat(po.getUsageId()).isEqualTo(42L);
          assertThat(po.getUsageName()).isEqualTo("销售明细集");
        })
        .extracting(MetricUsagePO::getMetricId).containsExactly(101L, 102L);
  }

  @Test
  void syncBindingsWithEmptyIdsClearsBindingsOnly() {
    service.syncBindings("DATASET", 42L, "销售明细集", List.of());
    verify(mapper).delete(any(LambdaQueryWrapper.class));
    verify(mapper, never()).insert(any(MetricUsagePO.class));
  }

  @Test
  void syncBindingsIsFailOpen() {
    when(mapper.delete(any(LambdaQueryWrapper.class)))
        .thenThrow(new RuntimeException("db down"));
    service.syncBindings("DATASET", 42L, "x", List.of(101L));
    verify(mapper, never()).insert(any(MetricUsagePO.class));
  }

  @Test
  void boundMetricIdsReturnsDistinctMetricIds() {
    MetricUsagePO a = usageRow(101L);
    MetricUsagePO b = usageRow(101L);
    MetricUsagePO c = usageRow(102L);
    when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(a, b, c));
    assertThat(service.boundMetricIds("DATASET", 42L)).containsExactly(101L, 102L);
  }

  @Test
  void summaryCountsDatasetTypeSeparately() {
    when(mapper.countByMetric(7L, 101L)).thenReturn(3L);
    when(mapper.countGroupByType(7L, 101L)).thenReturn(List.of(
        Map.of("usageType", "DATASET", "cnt", 2L),
        Map.of("usageType", "REPORT", "cnt", 1L)));

    MetricUsageApi.UsageSummary summary = service.summary(101L);
    assertThat(summary.totalCount()).isEqualTo(3);
    assertThat(summary.datasetCount()).isEqualTo(2);
    assertThat(summary.reportCount()).isEqualTo(1);
    assertThat(summary.dashboardCount()).isZero();
  }

  private static MetricUsagePO usageRow(Long metricId) {
    MetricUsagePO po = new MetricUsagePO();
    po.setProjectId(7L);
    po.setMetricId(metricId);
    po.setUsageType("DATASET");
    po.setUsageId(42L);
    return po;
  }
}
