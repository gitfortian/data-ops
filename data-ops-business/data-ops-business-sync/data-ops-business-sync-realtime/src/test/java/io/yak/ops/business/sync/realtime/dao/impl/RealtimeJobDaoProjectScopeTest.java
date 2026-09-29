package io.yak.ops.business.sync.realtime.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobCommandMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobDefinitionMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobDeploymentMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobEventMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobQueryMapper;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDefinitionPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RealtimeJobDaoProjectScopeTest {

  @Mock private RealtimeJobDefinitionMapper definitionMapper;
  @Mock private RealtimeJobDeploymentMapper deploymentMapper;
  @Mock private RealtimeJobEventMapper eventMapper;
  @Mock private RealtimeJobCommandMapper commandMapper;
  @Mock private RealtimeJobQueryMapper queryMapper;

  @Test
  void insertDefinitionUsesCurrentProject() {
    when(definitionMapper.insert(any(RealtimeJobDefinitionPO.class)))
        .thenAnswer(
            invocation -> {
              RealtimeJobDefinitionPO po = invocation.getArgument(0);
              po.setId(11L);
              return 1;
            });
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(7L, "Project A"));
    RealtimeJobDaoImpl dao =
        new RealtimeJobDaoImpl(
            definitionMapper,
            deploymentMapper,
            eventMapper,
            commandMapper,
            queryMapper,
            currentProject);
    RealtimeJobDefinitionPO definition = new RealtimeJobDefinitionPO();
    definition.setJobName("orders-cdc");

    assertThat(dao.insertDefinition(definition)).isEqualTo(11L);

    ArgumentCaptor<RealtimeJobDefinitionPO> captor =
        ArgumentCaptor.forClass(RealtimeJobDefinitionPO.class);
    org.mockito.Mockito.verify(definitionMapper).insert(captor.capture());
    assertThat(captor.getValue().getProjectId()).isEqualTo(7L);
  }

  @Test
  void ordinaryLookupFailsClosedWithoutCurrentProject() {
    RealtimeJobDaoImpl dao =
        new RealtimeJobDaoImpl(
            definitionMapper,
            deploymentMapper,
            eventMapper,
            commandMapper,
            queryMapper);

    assertThatThrownBy(() -> dao.findDefinition(11L))
        .isInstanceOf(ProjectContextException.class);
  }
}
