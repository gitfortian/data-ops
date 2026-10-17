package io.yak.ops.business.development.handoff;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.task.DevelopmentPublishedSqlRevisionEvidenceService;
import io.yak.ops.business.development.task.DevelopmentTaskNodeResolver;
import io.yak.ops.business.development.task.DevelopmentTaskRevisionReader;
import io.yak.ops.business.development.task.TaskDefinitionDigestCalculator;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.version.VersionDigests;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.spi.modeling.PublishedDwdDesignEvidenceProvider;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FrozenDwdExecutionCandidateServiceTest {
  private static final String MAPPING = "a".repeat(64);
  private static final String LOGICAL = "b".repeat(64);
  private static final String PHYSICAL = "c".repeat(64);
  private static final String STANDARD = "d".repeat(64);
  private final CurrentProject project = () -> Optional.of(new ProjectContext(42L, null));
  private final DevelopmentModelTaskBindingRepository bindings =
      mock(DevelopmentModelTaskBindingRepository.class);
  private final PublishedDwdDesignEvidenceProvider modeling =
      mock(PublishedDwdDesignEvidenceProvider.class);
  private final DataSourceReader sources = mock(DataSourceReader.class);
  private final DevelopmentTaskNodeResolver nodes = mock(DevelopmentTaskNodeResolver.class);
  private final DevelopmentTaskRevisionReader revisions = mock(DevelopmentTaskRevisionReader.class);
  private final DevelopmentPublishedSqlRevisionEvidenceService integrity =
      mock(DevelopmentPublishedSqlRevisionEvidenceService.class);
  private final TaskDefinitionDigestCalculator digests = new TaskDefinitionDigestCalculator();
  private final ObjectMapper json = new ObjectMapper();
  private final FrozenDwdExecutionCandidateService service =
      new FrozenDwdExecutionCandidateService(project, bindings, modeling, sources,
          nodes, revisions, integrity, digests, json);

  private final DevelopmentNode node = new DevelopmentNode(
      7L, "订单明细", "SQL", 42L, null, true,
      Instant.parse("2026-10-17T00:00:00Z"), Instant.parse("2026-10-17T00:00:00Z"));
  private final DataSourceDefinition source = mock(DataSourceDefinition.class);
  private final TaskDefinition published = new TaskDefinition(
      "SQL", 1, "INSERT INTO dwd_order_detail SELECT * FROM ods_order_detail", "{}");

  private DevelopmentModelTaskBinding binding(String checksum) {
    String digest = VersionDigests.sha256Fields("42", "50", "90", "3", "901",
        MAPPING, LOGICAL, PHYSICAL, STANDARD, "7", "92", "2", checksum, "9",
        "warehouse", "public", "dwd_order_detail", "INSERT");
    return new DevelopmentModelTaskBinding(55L, 42, 50, 90, 3, 901,
        MAPPING, LOGICAL, PHYSICAL, STANDARD, 7, 92, 2, checksum, 9,
        "warehouse", "public", "dwd_order_detail", "INSERT", digest,
        "ea56a43b-2373-4f2b-a13e-76e26bd32221",
        "BINDING_RECORDED_NOT_EXECUTABLE");
  }

  @BeforeEach
  void setup() {
    String checksum = digests.calculate(published);
    when(bindings.byId(55)).thenReturn(Optional.of(binding(checksum)));
    when(modeling.inspect(50, 3)).thenReturn(
        new PublishedDwdDesignEvidenceProvider.Evidence(
            42, 50, 90, 3, "dwd_order_detail", 901, MAPPING,
            LOGICAL, PHYSICAL, STANDARD, true, "REVIEWABLE_ONLY"));
    when(sources.require(9L)).thenReturn(source);
    when(source.getProjectId()).thenReturn(42L);
    when(source.getConnStatus()).thenReturn(DataSourceConnStatus.CONNECTED);
    when(nodes.requireTaskNode(7L)).thenReturn(node);
    when(revisions.get(node, 2)).thenReturn(new DevelopmentTaskRevision(
        92L, 7L, 2, 4, published, checksum, Instant.parse("2026-10-17T00:00:00Z")));
    when(integrity.inspect(7L, 2)).thenReturn(
        new DevelopmentPublishedSqlRevisionEvidenceService.Report(
            "7", "92", 2, 4, checksum, 8, false,
            "PUBLISHED_SQL_REVISION_VERIFIED_NOT_EXECUTED", false));
  }

  @Test
  void frozenSnapshotUsesExactPublishedRevisionNotMutableEditor() throws Exception {
    var candidate = service.prepare(55);
    TaskVersionSnapshot snapshot = candidate.frozenSnapshot();
    assertEquals("development:7", snapshot.taskId());
    assertEquals("SQL", snapshot.type());
    assertEquals(2, snapshot.version());
    assertEquals(digests.calculate(published), snapshot.configDigest());
    assertEquals(published, json.readValue(snapshot.definitionSnapshotJson(), TaskDefinition.class));
    assertEquals(published.configJson(), snapshot.executionConfigSnapshotJson());
    assertEquals("FROZEN_REVISION_VERIFIED_EXECUTION_NOT_AUTHORIZED", candidate.preview().status());
    assertTrue(candidate.preview().editorDiffersFromPublishedRevision());
    assertFalse(candidate.preview().authorizedForExecution());
    assertFalse(json.writeValueAsString(candidate.preview()).contains(published.content()));
    verify(bindings).byId(55);
    verify(modeling).inspect(50, 3);
    verify(revisions).get(node, 2);
    verify(integrity).inspect(7L, 2);
  }

  @Test
  void newDraftDoesNotOverwriteFrozenPublishedSnapshot() {
    assertTrue(service.prepare(55).frozenSnapshot()
        .definitionSnapshotJson().contains(published.content()));
    assertFalse(service.inspect(55).authorizedForExecution());
  }

  @Test
  void changedPublishedSqlOrForgedChecksumFailsClosed() {
    var tampered = new TaskDefinition("SQL", 1, "SELECT 999", "{}");
    when(revisions.get(node, 2)).thenReturn(new DevelopmentTaskRevision(
        92L, 7L, 2, 4, tampered, digests.calculate(tampered),
        Instant.parse("2026-10-17T00:00:00Z")));
    assertThrows(IllegalStateException.class, () -> service.inspect(55));
  }

  @Test
  void sourceRevocationOrDriftedModelIsNotAReadyExecution() {
    when(source.getConnStatus()).thenReturn(DataSourceConnStatus.UNKNOWN);
    assertThrows(IllegalStateException.class, () -> service.inspect(55));
    when(source.getConnStatus()).thenReturn(DataSourceConnStatus.CONNECTED);
    when(modeling.inspect(50, 3)).thenReturn(new PublishedDwdDesignEvidenceProvider.Evidence(
        42, 50, 90, 3, "dwd_other_table", 901, MAPPING,
        LOGICAL, PHYSICAL, STANDARD, true, "REVIEWABLE_ONLY"));
    assertThrows(IllegalStateException.class, () -> service.inspect(55));
  }

  @Test
  void blockedModelOrChangedCurrentStandardCannotPass() {
    when(modeling.inspect(50, 3)).thenReturn(new PublishedDwdDesignEvidenceProvider.Evidence(
        42, 50, 90, 3, "dwd_order_detail", 901, MAPPING,
        LOGICAL, PHYSICAL, "f".repeat(64), true, "REVIEWABLE_ONLY"));
    assertThrows(IllegalStateException.class, () -> service.inspect(55));
    when(modeling.inspect(50, 3)).thenThrow(new SecurityException("permission revoked"));
    assertThrows(SecurityException.class, () -> service.inspect(55));
  }

  @Test
  void changedBindingDigestOrTaskRevisionIdentityFailsClosed() {
    var valid = binding(digests.calculate(published));
    var altered = new DevelopmentModelTaskBinding(valid.id(), valid.projectId(), valid.modelId(),
        valid.modelVersionId(), valid.modelVersionNo(), valid.mappingRevisionId(),
        valid.mappingSha256(), valid.logicalSha256(), valid.physicalSha256(),
        valid.currentStandardSha256(), valid.nodeId(), valid.taskRevisionId(),
        valid.taskRevisionNo(), valid.taskDefinitionSha256(), valid.targetDatasourceId(),
        valid.targetDatabase(), valid.targetSchema(), valid.targetTable(), "OVERWRITE_PARTITION",
        valid.bindingDigest(), valid.idempotencyKey(), valid.state());
    when(bindings.byId(55)).thenReturn(Optional.of(altered));
    assertThrows(IllegalStateException.class, () -> service.inspect(55));
    when(bindings.byId(55)).thenReturn(Optional.of(valid));
    when(integrity.inspect(7L, 2)).thenReturn(
        new DevelopmentPublishedSqlRevisionEvidenceService.Report(
            "7", "93", 2, 4, digests.calculate(published), 8, true,
            "PUBLISHED_SQL_REVISION_VERIFIED_NOT_EXECUTED", false));
    assertThrows(IllegalStateException.class, () -> service.inspect(55));
  }

  @Test
  void absentBindingOrForeignProjectNeverLeaksFrozenSql() {
    when(bindings.byId(55)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> service.inspect(55));
    verifyNoInteractions(revisions);
    when(bindings.byId(55)).thenReturn(Optional.of(binding(digests.calculate(published))));
    var noProject = new FrozenDwdExecutionCandidateService(
        Optional::empty, bindings, modeling, sources, nodes,
        revisions, integrity, digests, json);
    assertThrows(RuntimeException.class, () -> noProject.inspect(55));
  }

  @Test
  void invalidBindingIdentifierNeverReachesRepository() {
    assertThrows(IllegalArgumentException.class, () -> service.inspect(0));
    verify(bindings, never()).byId(anyLong());
  }
}
