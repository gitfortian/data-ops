package io.yak.ops.business.dataset.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.dataset.dao.mapper.DatasetDraftFieldMapper;
import io.yak.ops.business.dataset.dao.mapper.DatasetFieldMapper;
import io.yak.ops.business.dataset.dao.mapper.DatasetMapper;
import io.yak.ops.business.dataset.dao.mapper.DatasetQueryPerformanceMapper;
import io.yak.ops.business.dataset.dao.mapper.DatasetVersionMapper;
import io.yak.ops.business.dataset.dao.model.DatasetQueryPerformancePO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DatasetExactVersionSuccessAuditQueryTest {

  @Test
  void limitsOnlyAfterProjectDatasetImmutableVersionAndSuccessPredicates() {
    DatasetQueryPerformanceMapper mapper = mock(DatasetQueryPerformanceMapper.class);
    DatasetQueryPerformancePO source = new DatasetQueryPerformancePO();
    source.setProjectId(42L);
    source.setDatasetId(101L);
    source.setDatasetVersionId(9007199254740993L);
    source.setStatus("SUCCESS");
    source.setQueryId("old-query");
    when(mapper.selectList(any())).thenReturn(List.of(source));

    var result = dao(mapper).selectSuccessfulQueryPerformanceByDatasetAndVersion(
        42L, 101L, 9007199254740993L, 999);

    assertThat(result).containsExactly(source);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<LambdaQueryWrapper<DatasetQueryPerformancePO>> capture =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(mapper).selectList(capture.capture());
    var query = capture.getValue();
    assertThat(query.getSqlSegment())
        .contains("project_id", "dataset_id", "dataset_version_id", "status",
            "started_at DESC", "id DESC", "LIMIT 200");
    assertThat(query.getParamNameValuePairs().values())
        .containsExactlyInAnyOrder(42L, 101L, 9007199254740993L, "SUCCESS");
  }

  @Test
  void invalidProjectDatasetOrVersionNeverTouchesAuditMapper() {
    DatasetQueryPerformanceMapper mapper = mock(DatasetQueryPerformanceMapper.class);
    var dao = dao(mapper);

    assertThatThrownBy(() -> dao.selectSuccessfulQueryPerformanceByDatasetAndVersion(
        null, 101L, 10L, 200)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> dao.selectSuccessfulQueryPerformanceByDatasetAndVersion(
        0L, 101L, 10L, 200)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> dao.selectSuccessfulQueryPerformanceByDatasetAndVersion(
        42L, 0L, 10L, 200)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> dao.selectSuccessfulQueryPerformanceByDatasetAndVersion(
        42L, 101L, -10L, 200)).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(mapper);
  }

  private DatasetDaoImpl dao(DatasetQueryPerformanceMapper queryPerformanceMapper) {
    return new DatasetDaoImpl(
        mock(DatasetMapper.class),
        mock(DatasetVersionMapper.class),
        mock(DatasetFieldMapper.class),
        mock(DatasetDraftFieldMapper.class),
        queryPerformanceMapper);
  }
}
