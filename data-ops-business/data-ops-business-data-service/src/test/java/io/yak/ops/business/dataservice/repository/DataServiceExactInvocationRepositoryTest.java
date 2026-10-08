package io.yak.ops.business.dataservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceCallLogMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceCallLogPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataServiceExactInvocationRepositoryTest {

  @Test
  void queryScopesOneAuditByTrustedProjectAndOwningApiAndInvocationId() {
    DataServiceCallLogMapper mapper = mock(DataServiceCallLogMapper.class);
    CurrentProject project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenReturn(42L);
    DataServiceCallLogPO row = new DataServiceCallLogPO();
    row.setId(9007199254740993L);
    row.setApiId(7L);
    row.setProjectId(42L);
    row.setServiceName("Orders");
    row.setServicePath("/orders");
    row.setSuccess(true);
    row.setCreateTime(LocalDateTime.of(2025, 6, 1, 9, 0));
    when(mapper.selectOne(any())).thenReturn(row);

    var repository = new DataServiceCallLogRepositoryAdapter(mapper, project);
    var result = repository.findByApiAndId(7L, 9007199254740993L);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().id()).isEqualTo(9007199254740993L);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<LambdaQueryWrapper<DataServiceCallLogPO>> capture =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(mapper).selectOne(capture.capture());
    var query = capture.getValue();
    assertThat(query.getSqlSegment()).contains("project_id", "api_id", "id");
    assertThat(query.getParamNameValuePairs().values())
        .containsExactlyInAnyOrder(42L, 7L, 9007199254740993L);
    verify(project).requireProjectId();
  }

  @Test
  void foreignProjectOrApiReturnsNoAuditAndNeverFallsBackToGlobalLookup() {
    DataServiceCallLogMapper mapper = mock(DataServiceCallLogMapper.class);
    CurrentProject project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenReturn(5L);
    var repository = new DataServiceCallLogRepositoryAdapter(mapper, project);

    assertThat(repository.findByApiAndId(7L, 9007199254740993L)).isEmpty();
    @SuppressWarnings("unchecked")
    ArgumentCaptor<LambdaQueryWrapper<DataServiceCallLogPO>> capture =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(mapper).selectOne(capture.capture());
    assertThat(capture.getValue().getParamNameValuePairs().values())
        .containsExactlyInAnyOrder(5L, 7L, 9007199254740993L);
  }

  @Test
  void invalidIdsMustNotReadAnyProjectOrAudit() {
    DataServiceCallLogMapper mapper = mock(DataServiceCallLogMapper.class);
    CurrentProject project = mock(CurrentProject.class);
    var repository = new DataServiceCallLogRepositoryAdapter(mapper, project);
    assertThatThrownBy(() -> repository.findByApiAndId(null, 2L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> repository.findByApiAndId(7L, 0L))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(mapper, project);
  }
}
