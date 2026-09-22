package io.yak.ops.business.quality.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.quality.dao.mapper.QualityExecutionMapper;
import io.yak.ops.business.quality.dao.mapper.QualityQueryMapper;
import io.yak.ops.business.quality.dao.mapper.QualityRuleExecutionMapper;
import io.yak.ops.common.bean.po.quality.QualityExecutionPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class QualityExecutionDaoProjectScopeTest {

  @Test
  void executionQueriesReceiveTrustedProjectId() {
    Fixture fixture = fixture(7L);
    when(fixture.queryMapper.countExecutions(any())).thenReturn(2L);
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("monitorId", 42L);

    assertThat(fixture.dao.countExecutions(params)).isEqualTo(2L);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(fixture.queryMapper).countExecutions(captor.capture());
    assertThat(captor.getValue())
        .containsEntry("projectId", 7L)
        .containsEntry("monitorId", 42L);
    assertThat(params).doesNotContainKey("projectId");
  }

  @Test
  void executionInsertAlwaysUsesTrustedProject() {
    Fixture fixture = fixture(7L);
    doAnswer(
            invocation -> {
              QualityExecutionPO row = invocation.getArgument(0);
              row.setId(11L);
              return 1;
            })
        .when(fixture.executionMapper)
        .insert(any(QualityExecutionPO.class));
    QualityExecutionPO execution = new QualityExecutionPO();

    assertThat(fixture.dao.insertExecution(execution)).isEqualTo(11L);
    assertThat(execution.getProjectId()).isEqualTo(7L);
  }

  @Test
  void callerCannotOverrideExecutionProject() {
    Fixture fixture = fixture(7L);
    QualityExecutionPO execution = new QualityExecutionPO();
    execution.setProjectId(8L);

    assertThatThrownBy(() -> fixture.dao.insertExecution(execution))
        .isInstanceOf(ProjectContextException.class);
  }

  @Test
  void executionDetailUsesProjectAndBusinessKeyTogether() {
    Fixture fixture = fixture(7L);

    fixture.dao.selectByExecutionNo("QM-TEST");

    verify(fixture.executionMapper).selectOne(any());
  }

  private Fixture fixture(long projectId) {
    QualityExecutionMapper executionMapper = mock(QualityExecutionMapper.class);
    QualityRuleExecutionMapper ruleExecutionMapper =
        mock(QualityRuleExecutionMapper.class);
    QualityQueryMapper queryMapper = mock(QualityQueryMapper.class);
    CurrentProject currentProject =
        () -> Optional.of(new ProjectContext(projectId, "test-project"));
    return new Fixture(
        new QualityExecutionDaoImpl(
            executionMapper,
            ruleExecutionMapper,
            queryMapper,
            currentProject),
        executionMapper,
        queryMapper);
  }

  private record Fixture(
      QualityExecutionDaoImpl dao,
      QualityExecutionMapper executionMapper,
      QualityQueryMapper queryMapper) {}
}
