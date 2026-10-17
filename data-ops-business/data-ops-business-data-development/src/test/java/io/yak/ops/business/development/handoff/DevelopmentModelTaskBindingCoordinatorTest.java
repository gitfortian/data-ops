package io.yak.ops.business.development.handoff;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.development.task.DevelopmentPublishedSqlRevisionEvidenceService;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.spi.modeling.PublishedDwdDesignEvidenceProvider;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DevelopmentModelTaskBindingCoordinatorTest {
  private static final String SHA = "a".repeat(64);
  private static final String TASK_SHA = "b".repeat(64);
  private static final String REQUEST_KEY = "ea56a43b-2373-4f2b-a13e-76e26bd32221";

  private final CurrentProject project = () -> Optional.of(new ProjectContext(42L, null));
  private final PublishedDwdDesignEvidenceProvider modeling =
      mock(PublishedDwdDesignEvidenceProvider.class);
  private final DevelopmentPublishedSqlRevisionEvidenceService tasks =
      mock(DevelopmentPublishedSqlRevisionEvidenceService.class);
  private final DevelopmentModelTaskBindingRepository repository =
      mock(DevelopmentModelTaskBindingRepository.class);
  private final DataSourceReader sources = mock(DataSourceReader.class);
  private final DataSourceDefinition connected = mock(DataSourceDefinition.class);
  private final DevelopmentModelTaskBindingCoordinator coordinator =
      new DevelopmentModelTaskBindingCoordinator(project, modeling, tasks, repository, sources);

  private DevelopmentModelTaskBindingCoordinator.BindCommand command() {
    return new DevelopmentModelTaskBindingCoordinator.BindCommand(
        50L, 90L, 3, 901L, SHA,
        7L, 92L, 2, TASK_SHA, 2L,
        9L, "warehouse", "public", "dwd_order_detail",
        "INSERT", REQUEST_KEY, true);
  }

  @BeforeEach
  void setup() {
    when(modeling.inspect(50L, 3)).thenReturn(
        new PublishedDwdDesignEvidenceProvider.Evidence(
            42L, 50L, 90L, 3, "dwd_order_detail", 901L, SHA,
            "c".repeat(64), "d".repeat(64), "e".repeat(64),
            true, "REVIEWABLE_ONLY"));
    when(tasks.inspect(7L, 2)).thenReturn(
        new DevelopmentPublishedSqlRevisionEvidenceService.Report(
            "7", "92", 2, 1L, TASK_SHA, 2L, false,
            "PUBLISHED_SQL_REVISION_VERIFIED_NOT_EXECUTED", false));
    when(sources.require(9L)).thenReturn(connected);
    when(connected.getProjectId()).thenReturn(42L);
    when(connected.getConnStatus()).thenReturn(DataSourceConnStatus.CONNECTED);
    when(repository.byKey(REQUEST_KEY)).thenReturn(Optional.empty());
    when(repository.byRevision(7L, 92L)).thenReturn(Optional.empty());
    when(repository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test void precisePublishedModelAndTaskAreRecordedAsNonExecutableBinding() {
    var returned = coordinator.bind(command());
    assertEquals("BINDING_RECORDED_NOT_EXECUTABLE", returned.state());
    assertEquals(42L, returned.projectId());
    assertEquals(90L, returned.modelVersionId());
    assertEquals(901L, returned.mappingRevisionId());
    assertEquals(92L, returned.taskRevisionId());
    assertEquals(TASK_SHA, returned.taskDefinitionSha256());
    assertEquals(64, returned.bindingDigest().length());
    verify(repository).lockNode(7L);
    verify(modeling).inspect(50L, 3);
    verify(tasks).inspect(7L, 2);
    verify(repository).insert(any());
  }

  @Test void staleModelOrForeignProjectNeverPersists() {
    when(modeling.inspect(50L, 3)).thenReturn(
        new PublishedDwdDesignEvidenceProvider.Evidence(
            99L, 50L, 90L, 3, "dwd_order_detail", 901L, SHA,
            "c".repeat(64), "d".repeat(64), "e".repeat(64),
            true, "REVIEWABLE_ONLY"));
    assertThrows(IllegalStateException.class, () -> coordinator.bind(command()));
    verify(repository, never()).insert(any());
  }

  @Test void oldOrBlockedPublishedDwdVersionIsRejected() {
    when(modeling.inspect(50L, 3)).thenReturn(
        new PublishedDwdDesignEvidenceProvider.Evidence(
            42L, 50L, 90L, 3, "dwd_order_detail", 901L, SHA,
            "c".repeat(64), "d".repeat(64), "e".repeat(64),
            false, "DESIGN_BLOCKED"));
    assertThrows(IllegalStateException.class, () -> coordinator.bind(command()));
    verify(repository, never()).insert(any());
  }

  @Test void frozenTaskChecksumMustMatchPersistentTaskRevision() {
    when(tasks.inspect(7L, 2)).thenReturn(
        new DevelopmentPublishedSqlRevisionEvidenceService.Report(
            "7", "92", 2, 1L, "f".repeat(64), 2L, true,
            "PUBLISHED_SQL_REVISION_VERIFIED_NOT_EXECUTED", false));
    assertThrows(IllegalStateException.class, () -> coordinator.bind(command()));
    verify(repository, never()).insert(any());
  }

  @Test void explicitUserConfirmationIsMandatoryBeforeTouchingRepositories() {
    var cmd = command();
    var unconfirmed = new DevelopmentModelTaskBindingCoordinator.BindCommand(
        cmd.modelId(), cmd.modelVersionId(), cmd.modelVersionNo(),
        cmd.mappingRevisionId(), cmd.mappingSha256(), cmd.nodeId(),
        cmd.taskRevisionId(), cmd.taskRevisionNo(), cmd.taskDefinitionSha256(),
        cmd.expectedDraftRevision(),
        cmd.targetDatasourceId(), cmd.targetDatabase(), cmd.targetSchema(),
        cmd.targetTable(), cmd.writeMode(), cmd.idempotencyKey(), false);
    assertThrows(IllegalArgumentException.class, () -> coordinator.bind(unconfirmed));
    verifyNoInteractions(repository, modeling, tasks);
  }

  @Test void duplicateRevisionWithDifferentIdempotencyKeyMustNotBeOverwritten() {
    var recorded = coordinator.bind(command());
    reset(repository);
    when(repository.byKey(REQUEST_KEY)).thenReturn(Optional.empty());
    when(repository.byRevision(7L, 92L)).thenReturn(Optional.of(recorded));
    assertThrows(IllegalStateException.class, () -> coordinator.bind(command()));
    verify(repository, never()).insert(any());
  }

  @Test void identicalRequestKeyIsIdempotentButNeverReturnsAConflictingBinding() {
    var recorded = coordinator.bind(command());
    reset(repository);
    when(repository.byKey(REQUEST_KEY)).thenReturn(Optional.of(recorded));
    assertEquals(recorded.bindingDigest(), coordinator.bind(command()).bindingDigest());
    verify(repository, never()).insert(any());
  }

  @Test void projectNotSelectedDeniesEvenAValidClientPayload() {
    var noProject = new DevelopmentModelTaskBindingCoordinator(
        Optional::empty, modeling, tasks, repository, sources);
    assertThrows(RuntimeException.class, () -> noProject.bind(command()));
    verify(repository, never()).insert(any());
  }

  @Test void readbackDoesNotGuessTaskIdentityFromModelName() {
    assertThrows(IllegalArgumentException.class, () -> coordinator.get(0L));
    verify(repository, never()).byId(anyLong());
  }
  @Test
  void foreignDatasourceDeniesBindingBeforeTaskLookupOrDatabaseInsert() {
    when(connected.getProjectId()).thenReturn(99L);
    assertThrows(IllegalStateException.class, () -> coordinator.bind(command()));
    verifyNoInteractions(tasks);
    verify(repository, never()).insert(any());
  }

  @Test
  void disconnectedDatasourceCannotProduceExecutableBindingProvenance() {
    when(connected.getConnStatus()).thenReturn(DataSourceConnStatus.UNKNOWN);
    assertThrows(IllegalStateException.class, () -> coordinator.bind(command()));
    verify(repository, never()).insert(any());
  }

  @Test
  void incorrectTargetPhysicalTableIsRejectedEvenWithValidModelSha() {
    var cmd = command();
    var mismatched = new DevelopmentModelTaskBindingCoordinator.BindCommand(
        cmd.modelId(), cmd.modelVersionId(), cmd.modelVersionNo(),
        cmd.mappingRevisionId(), cmd.mappingSha256(), cmd.nodeId(),
        cmd.taskRevisionId(), cmd.taskRevisionNo(), cmd.taskDefinitionSha256(),
        cmd.expectedDraftRevision(), cmd.targetDatasourceId(), cmd.targetDatabase(), cmd.targetSchema(),
        "dwd_another_table", cmd.writeMode(), cmd.idempotencyKey(), true);
    assertThrows(IllegalStateException.class, () -> coordinator.bind(mismatched));
    verifyNoInteractions(sources, tasks);
    verify(repository, never()).insert(any());
  }

  @Test
  void sameIdempotencyKeyCannotSilentlyReuseChangedCurrentStandardEvidence() {
    var recorded = coordinator.bind(command());
    reset(repository);
    when(repository.byKey(REQUEST_KEY)).thenReturn(Optional.of(recorded));
    when(modeling.inspect(50L, 3)).thenReturn(
        new PublishedDwdDesignEvidenceProvider.Evidence(
            42L, 50L, 90L, 3, "dwd_order_detail", 901L, SHA,
            "c".repeat(64), "d".repeat(64), "9".repeat(64),
            true, "REVIEWABLE_ONLY"));
    assertThrows(IllegalStateException.class, () -> coordinator.bind(command()));
    verify(repository, never()).insert(any());
  }
  @Test
  void concurrentDraftEditBetweenPrecheckAndBindCannotBeSilentlyAccepted() {
    var cmd = command();
    var stale = new DevelopmentModelTaskBindingCoordinator.BindCommand(
        cmd.modelId(), cmd.modelVersionId(), cmd.modelVersionNo(),
        cmd.mappingRevisionId(), cmd.mappingSha256(), cmd.nodeId(),
        cmd.taskRevisionId(), cmd.taskRevisionNo(), cmd.taskDefinitionSha256(),
        1L, cmd.targetDatasourceId(), cmd.targetDatabase(), cmd.targetSchema(),
        cmd.targetTable(), cmd.writeMode(), cmd.idempotencyKey(), true);
    assertThrows(IllegalStateException.class, () -> coordinator.bind(stale));
    verify(repository, never()).insert(any());
  }

}
