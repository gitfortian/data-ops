package io.yak.ops.business.dataset.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
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

  @BeforeAll
  static void initializeMybatisLambdaMetadata() {
    // Mockito-only Dao tests do not perform MapperScan or initialize table metadata.
    // Use real MyBatis-Plus mappings to verify generated SQL and bound predicate values.
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(configuration, "dataset-exact-version-audit-test"),
        DatasetQueryPerformancePO.class);
  }

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

  @Test
  void cursorPageFiltersProjectDatasetImmutableVersionAndSuccessBeforeExclusiveAuditId() {
    DatasetQueryPerformanceMapper mapper = mock(DatasetQueryPerformanceMapper.class);
    DatasetQueryPerformancePO source = new DatasetQueryPerformancePO();
    source.setId(9007199254740993L);
    source.setStatus("SUCCESS");
    when(mapper.selectList(any())).thenReturn(List.of(source));

    var rows = dao(mapper).selectSuccessfulQueryPerformancePageByDatasetAndVersion(
        42L, 101L, 9007199254740995L, 9007199254740994L, 999);

    assertThat(rows).containsExactly(source);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<LambdaQueryWrapper<DatasetQueryPerformancePO>> capture =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(mapper).selectList(capture.capture());
    var query = capture.getValue();
    assertThat(query.getSqlSegment())
        .contains("project_id", "dataset_id", "dataset_version_id", "status",
            "id <", "id DESC", "LIMIT 200")
        .doesNotContain("started_at DESC");
    assertThat(query.getParamNameValuePairs().values())
        .containsExactlyInAnyOrder(42L, 101L, 9007199254740995L, "SUCCESS", 9007199254740994L);
  }

  @Test
  void firstCursorPageStartsAtNewestPersistedIdAndInvalidIdsNeverReachMapper() {
    DatasetQueryPerformanceMapper mapper = mock(DatasetQueryPerformanceMapper.class);
    var dao = dao(mapper);
    dao.selectSuccessfulQueryPerformancePageByDatasetAndVersion(42L, 101L, 77L, null, 1);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<LambdaQueryWrapper<DatasetQueryPerformancePO>> capture =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(mapper).selectList(capture.capture());
    assertThat(capture.getValue().getSqlSegment()).contains("id DESC", "LIMIT 1")
        .doesNotContain("id <");
    org.mockito.Mockito.clearInvocations(mapper);

    assertThatThrownBy(() -> dao.selectSuccessfulQueryPerformancePageByDatasetAndVersion(
        null, 101L, 77L, null, 200)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> dao.selectSuccessfulQueryPerformancePageByDatasetAndVersion(
        42L, 0L, 77L, null, 200)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> dao.selectSuccessfulQueryPerformancePageByDatasetAndVersion(
        42L, 101L, 0L, null, 200)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> dao.selectSuccessfulQueryPerformancePageByDatasetAndVersion(
        42L, 101L, 77L, 0L, 200)).isInstanceOf(IllegalArgumentException.class);
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
