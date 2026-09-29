package io.yak.ops.business.sync.realtime.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.dao.mapper.ComputeEnvironmentMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeDefinitionVersionMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobDefinitionMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobDeploymentMapper;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDefinitionPO;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDeploymentPO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ComputeEnvironmentDaoImplReferenceTest {

  private RealtimeJobDefinitionMapper definitionMapper;
  private RealtimeDefinitionVersionMapper definitionVersionMapper;
  private RealtimeJobDeploymentMapper deploymentMapper;
  private ComputeEnvironmentDaoImpl dao;

  @BeforeEach
  void setUp() {
    ComputeEnvironmentMapper environmentMapper = mock(ComputeEnvironmentMapper.class);
    definitionMapper = mock(RealtimeJobDefinitionMapper.class);
    definitionVersionMapper = mock(RealtimeDefinitionVersionMapper.class);
    deploymentMapper = mock(RealtimeJobDeploymentMapper.class);
    dao =
        new ComputeEnvironmentDaoImpl(
            environmentMapper, definitionMapper, definitionVersionMapper, deploymentMapper);
  }

  @Test
  void publishedDefinitionReferencePreventsEnvironmentDeletionAfterDraftMovesElsewhere() {
    RealtimeJobDefinitionPO task = new RealtimeJobDefinitionPO();
    task.setPublishedDefinitionVersionId(31L);

    when(definitionMapper.selectCount(any())).thenReturn(0L);
    when(definitionMapper.selectList(any())).thenReturn(List.of(task));
    when(definitionVersionMapper.selectCount(any())).thenReturn(1L);

    assertThat(dao.hasRuntimeEnvironmentReferences(3L)).isTrue();
  }

  @Test
  void activeExecutionReferencePreventsEnvironmentDeletion() {
    when(definitionMapper.selectCount(any())).thenReturn(0L);
    when(definitionMapper.selectList(any())).thenReturn(List.of());
    when(deploymentMapper.selectList(any())).thenReturn(List.of());
    when(deploymentMapper.selectCount(any())).thenReturn(1L);

    assertThat(dao.hasRuntimeEnvironmentReferences(3L)).isTrue();
  }

  @Test
  void latestPendingReplacementTargetPreventsEnvironmentDeletion() {
    RealtimeJobDeploymentPO pending = new RealtimeJobDeploymentPO();
    pending.setId(19L);
    pending.setDefinitionId(7L);
    pending.setReplacementTargetDefinitionVersionId(32L);
    pending.setReplacementIdempotencyKey("apply-v4");

    when(definitionMapper.selectCount(any())).thenReturn(0L);
    when(definitionMapper.selectList(any())).thenReturn(List.of());
    when(deploymentMapper.selectList(any())).thenReturn(List.of(pending));
    when(definitionVersionMapper.selectCount(any())).thenReturn(1L);

    assertThat(dao.hasRuntimeEnvironmentReferences(3L)).isTrue();
  }

  @Test
  void historicalReplacementIntentDoesNotBlockAfterNewerExecutionExists() {
    RealtimeJobDeploymentPO current = new RealtimeJobDeploymentPO();
    current.setId(20L);
    current.setDefinitionId(7L);
    RealtimeJobDeploymentPO historical = new RealtimeJobDeploymentPO();
    historical.setId(19L);
    historical.setDefinitionId(7L);
    historical.setReplacementTargetDefinitionVersionId(32L);
    historical.setReplacementIdempotencyKey("apply-v4");

    when(definitionMapper.selectCount(any())).thenReturn(0L);
    when(definitionMapper.selectList(any())).thenReturn(List.of());
    when(deploymentMapper.selectList(any())).thenReturn(List.of(current, historical));
    when(deploymentMapper.selectCount(any())).thenReturn(0L);

    assertThat(dao.hasRuntimeEnvironmentReferences(3L)).isFalse();
  }
}
