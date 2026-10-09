package io.yak.ops.business.metric.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.yak.ops.business.metric.dao.mapper.MetricMapper;
import io.yak.ops.business.metric.dao.mapper.MetricTagRelMapper;
import io.yak.ops.business.metric.dao.model.MetricPO;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.domain.MetricStatus;
import io.yak.ops.business.metric.domain.MetricType;
import io.yak.ops.business.metric.domain.StatPeriod;
import io.yak.ops.core.project.CurrentProject;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** B-04: nullable Metric draft fields must be really cleared without weakening version CAS. */
class MetricRepositoryAdapterTest {

  private final MetricMapper mapper = mock(MetricMapper.class);
  private final MetricTagRelMapper tags = mock(MetricTagRelMapper.class);
  private final CurrentProject project = mock(CurrentProject.class);
  private MetricRepositoryAdapter adapter;

  @BeforeEach
  void setup() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MetricPO.class);
    when(project.requireProjectId()).thenReturn(42L);
    adapter = new MetricRepositoryAdapter(mapper, tags, project);
  }

  private static Metric clearedDraft() {
    return new Metric(7L, "metric", "New atomic metric", 1L, null, MetricType.ATOMIC,
        null, null, "SUM(amount)", null, null, null, null, null, null, null,
        StatPeriod.DAY, null, null, null, MetricStatus.ENABLED, 3,
        "creator", "editor", null, null);
  }

  @Test
  void nullReferencesAreExplicitlyWrittenAndProjectVersionCASIsRetained() {
    when(mapper.update(isNull(), any())).thenReturn(1);
    assertThat(adapter.update(clearedDraft())).isTrue();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<LambdaUpdateWrapper<MetricPO>> captured =
        ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    verify(mapper).update(isNull(), captured.capture());
    LambdaUpdateWrapper<MetricPO> update = captured.getValue();
    assertThat(update.getSqlSet())
        .contains("ref_metric_id", "model_id", "process_id", "caliber_id",
            "qualifiers_json", "dim_constraint", "filter_expr", "unit_id",
            "version", "updated_by")
        .doesNotContain("created_by =", "metric_code =");
    assertThat(update.getParamNameValuePairs().values()).containsNull();
    assertThat(update.getSqlSegment()).contains("project_id", "version", "id");
  }

  @Test
  void optimisticConflictReportsZeroRowsInsteadOfPretendingTheDraftWasSaved() {
    when(mapper.update(isNull(), any())).thenReturn(0);
    assertThat(adapter.update(clearedDraft())).isFalse();
  }
}
