package io.yak.ops.business.sync.realtime.dao.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobCommandMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobDefinitionMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobDeploymentMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobEventMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobQueryMapper;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RealtimeJobDaoImplExecutionIsolationTest {

  @Test
  void executionLifecycleNeverDualWritesTaskRuntimeProjection() {
    RealtimeJobDefinitionMapper definitions = mock(RealtimeJobDefinitionMapper.class);
    RealtimeJobDeploymentMapper executions = mock(RealtimeJobDeploymentMapper.class);
    RealtimeJobEventMapper events = mock(RealtimeJobEventMapper.class);
    RealtimeJobCommandMapper commands = mock(RealtimeJobCommandMapper.class);
    RealtimeJobQueryMapper queries = mock(RealtimeJobQueryMapper.class);
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(7L, "Project A"));
    RealtimeJobDaoImpl dao =
        new RealtimeJobDaoImpl(definitions, executions, events, commands, queries, currentProject);

    when(executions.update(any(), any())).thenReturn(1);
    when(commands.reconcileDeploymentByProject(
            anyLong(), eq(7L), anyString(), anyString(), any(), any()))
        .thenReturn(1);

    dao.markDeploymentRunning(7L, 19L, "job-1", "runtime-r1");
    dao.markDeployFailure(7L, 19L, false, false, "failed");
    dao.markStopping(7L, 19L);
    dao.reconcile(7L, 19L, "UNKNOWN", "UNKNOWN", "job-1", "unknown");
    dao.markTerminalFailure(7L, 19L, "lost");

    verifyNoInteractions(definitions);
  }
}
