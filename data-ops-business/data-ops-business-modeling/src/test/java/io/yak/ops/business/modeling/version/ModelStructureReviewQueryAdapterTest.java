package io.yak.ops.business.modeling.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.modeling.dao.model.ModelingColumnMappingPO;
import io.yak.ops.business.modeling.domain.ModelVersion;
import io.yak.ops.business.modeling.repository.MappingRepository;
import io.yak.ops.business.modeling.repository.ModelVersionRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModelStructureReviewQueryAdapterTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final ActionAuthorization authorization = mock(ActionAuthorization.class);
  private final CurrentProject project = mock(CurrentProject.class);
  private final ModelStructureService structures = mock(ModelStructureService.class);
  private final ModelVersionRepository versions = mock(ModelVersionRepository.class);
  private final MappingRepository mappings = mock(MappingRepository.class);
  private final ModelStructureReviewQueryAdapter adapter = new ModelStructureReviewQueryAdapter(authorization, project, structures, versions, mappings);

  @BeforeEach void setup() throws Exception {
    when(project.requireProjectId()).thenReturn(42L);
    when(structures.getForUpdate(7L)).thenReturn(view(List.of(column("amount", "BIGINT", null), column("new_field", "TEXT", null))));
    version(JSON.writeValueAsString(view(List.of(column("amount", "INT", null), column("removed", "TEXT", null)))));
    when(mappings.listByModelForReview(7L)).thenReturn(List.of(mapping(1, "amount", "SECRET_EXPRESSION"), mapping(2, "removed", null)));
  }

  @Test void authorizationPrecedesEverySourceAndProjectRead() {
    doThrow(new SecurityException("denied")).when(authorization).requirePermission(ModelingPermissionCode.READ);
    assertThrows(SecurityException.class, () -> adapter.read(7, 3, null));
    verifyNoInteractions(project, structures, versions, mappings);
  }

  @Test void projectsLockExactBaselineAndBoundedMappingReadsInOrder() {
    var context = adapter.read(7, 3, null);
    var order = inOrder(authorization, project, structures, versions, mappings);
    order.verify(authorization).requirePermission(ModelingPermissionCode.READ);
    order.verify(project).requireProjectId(); order.verify(structures).getForUpdate(7L);
    order.verify(versions).findByVersionNo(7L, 3); order.verify(mappings).listByModelForReview(7L);
    assertEquals("42", context.projectId()); assertEquals("11", context.baselineVersionId());
    verify(mappings, never()).listByModel(7L);
    assertEquals(context, adapter.read(7, 3, context.definition()));
  }

  @Test void projectsChangesAndCurrentMappingReasonsWithoutSourceOrExpressions() throws Exception {
    var context = adapter.read(7, 3, null);
    assertEquals(3, context.changes().size());
    assertEquals(List.of("CHANGED_TARGET_REVIEW", "TRANSFORM_MANUAL_REVIEW"), context.mappingChecks().get(0).reasons());
    assertEquals(List.of("UNMAPPED_SAVED_COLUMN"), context.mappingChecks().get(1).reasons());
    assertEquals(List.of("ORPHAN_MAPPING_TARGET"), context.mappingChecks().get(2).reasons());
    assertTrue(context.coverageGaps().contains("NO_HISTORICAL_MAPPING_SNAPSHOT"));
    String output = JSON.writeValueAsString(context);
    for (String forbidden : List.of("SECRET_EXPRESSION", "secret_database", "secret_table", "secret_source_column", "secret_actor", "secret_default")) assertFalse(output.contains(forbidden));
  }

  @Test void caseInsensitiveAlignmentDoesNotInventRenameAndExcludesDefaultOnlyChanges() throws Exception {
    version(JSON.writeValueAsString(view(List.of(column("Amount", "BIGINT", "old_default")))));
    when(structures.getForUpdate(7L)).thenReturn(view(List.of(column("amount", "BIGINT", "secret_default"))));
    assertEquals(1, adapter.read(7, 3, null).changes().size());
    version(JSON.writeValueAsString(view(List.of(column("amount", "BIGINT", "old_default")))));
    assertTrue(adapter.read(7, 3, null).changes().isEmpty());
    when(structures.getForUpdate(7L)).thenReturn(view(List.of(column("renamed", "BIGINT", "secret_default"))));
    assertEquals(2, adapter.read(7, 3, null).changes().size());
  }

  @Test void structureAndMappingDriftBothRequireRepreparingEvenForExcludedDetails() {
    String definition = adapter.read(7, 3, null).definition();
    when(mappings.listByModelForReview(7L)).thenReturn(List.of(mapping(1, "amount", "different_expression")));
    assertThrows(IllegalArgumentException.class, () -> adapter.read(7, 3, definition));
    String newDefinition = adapter.read(7, 3, null).definition();
    assertNotEquals(definition, newDefinition);
    when(structures.getForUpdate(7L)).thenReturn(view(List.of(column("amount", "BIGINT", "changed_default"))));
    assertThrows(IllegalArgumentException.class, () -> adapter.read(7, 3, newDefinition));
  }

  @ParameterizedTest @ValueSource(strings = {"{broken", "null", "{}", "{\"modelId\":8}", "oversized"})
  void corruptMissingOrOversizedVersionNeverFallsBack(String raw) {
    version("oversized".equals(raw) ? "x".repeat(262145) : raw);
    assertThrows(IllegalArgumentException.class, () -> adapter.read(7, 3, null));
    verifyNoInteractions(mappings);
  }

  @Test void absentAndMismatchedVersionFailBeforeMappings() {
    when(versions.findByVersionNo(7L, 3)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> adapter.read(7, 3, null));
    when(versions.findByVersionNo(7L, 3)).thenReturn(Optional.of(new ModelVersion(11L, 8L, 3, "{}", "{}", 0, "", "", null)));
    assertThrows(IllegalArgumentException.class, () -> adapter.read(7, 3, null)); verifyNoInteractions(mappings);
  }

  @Test void overLimitOrCrossProjectMappingsAreNotAnEmptyChecklist() {
    when(mappings.listByModelForReview(7L)).thenReturn(java.util.stream.IntStream.range(0, 101).mapToObj(i -> mapping(i + 1, "f" + i, null)).toList());
    assertThrows(IllegalArgumentException.class, () -> adapter.read(7, 3, null));
    var wrong = mapping(1, "amount", null); wrong.setProjectId(99L);
    when(mappings.listByModelForReview(7L)).thenReturn(List.of(wrong));
    assertThrows(IllegalArgumentException.class, () -> adapter.read(7, 3, null));
    when(mappings.listByModelForReview(7L)).thenThrow(new IllegalStateException("source unavailable"));
    assertThrows(IllegalStateException.class, () -> adapter.read(7, 3, null));
  }

  @Test void duplicateColumnOrMappingIdentityAndTooManyColumnsFailClosed() {
    when(structures.getForUpdate(7L)).thenReturn(view(List.of(column("A", "INT", null), column("a", "INT", null))));
    assertThrows(IllegalArgumentException.class, () -> adapter.read(7, 3, null));
    when(structures.getForUpdate(7L)).thenReturn(view(java.util.stream.IntStream.range(0, 101).mapToObj(i -> column("c" + i, "INT", null)).toList()));
    assertThrows(IllegalArgumentException.class, () -> adapter.read(7, 3, null));
    when(structures.getForUpdate(7L)).thenReturn(view(List.of(column("amount", "INT", null))));
    when(mappings.listByModelForReview(7L)).thenReturn(List.of(mapping(1, "amount", null), mapping(2, "AMOUNT", null)));
    assertThrows(IllegalArgumentException.class, () -> adapter.read(7, 3, null));
  }

  @Test void tablePrimaryKeyIndexAndPartitionHaveIndependentFacts() {
    var before = view(List.of(column("amount", "BIGINT", null)));
    var after = new StructureView(7L, "code", "name", "POSTGRESQL", "DRAFT", null, "changed", null, before.columns(),
        List.of("amount"), List.of(new StructureView.IndexView(1L, "idx", true, "BTREE", List.of("amount"))),
        new StructureView.PartitionView("HASH", List.of("amount"), "hidden_partition_expression"), Map.of());
    var changes = ModelStructureReviewProjection.changes(before, after);
    assertEquals(List.of("TABLE", "TABLE", "PRIMARY_KEY", "INDEX", "PARTITION"), changes.stream().map(change -> change.area()).toList());
    assertFalse(ModelStructureReviewProjection.encode(changes).contains("hidden_partition_expression"));
  }

  private void version(String raw) { when(versions.findByVersionNo(7L, 3)).thenReturn(Optional.of(new ModelVersion(11L, 7L, 3, raw, "{}", 2, "checksum", "publisher", null))); }
  private static StructureView view(List<StructureView.ColumnView> columns) {
    return new StructureView(7L, "code", "name", "MYSQL", "DRAFT", "hidden_description", "table", null, columns, List.of(), List.of(), null, Map.of());
  }
  private static StructureView.ColumnView column(String name, String type, String defaultValue) {
    return new StructureView.ColumnView(1L, name, type, null, null, false, defaultValue, null, null, 0, null, null, null, null, null, null, null, null, null, null);
  }
  private static ModelingColumnMappingPO mapping(long id, String column, String transform) {
    var row = new ModelingColumnMappingPO(); row.setId(id); row.setProjectId(42L); row.setModelId(7L); row.setTargetColumn(column);
    row.setTransformExpr(transform); row.setSourceDatasourceId(900L); row.setSourceDatabase("secret_database"); row.setSourceTable("secret_table"); row.setSourceColumn("secret_source_column"); row.setCreatedBy("secret_actor"); return row;
  }
}
