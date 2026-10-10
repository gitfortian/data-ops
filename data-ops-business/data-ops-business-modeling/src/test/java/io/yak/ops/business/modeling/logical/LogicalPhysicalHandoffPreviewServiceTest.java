package io.yak.ops.business.modeling.logical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.StandardField;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogicalPhysicalHandoffPreviewServiceTest {
  private LogicalDraftService logical;
  private ModelRepository repository;
  private ModelStructureService structure;
  private ProcessApi semantic;
  private LogicalPhysicalHandoffPreviewService preview;

  private static final String SNAPSHOT = """
      {"model":{"id":77,"processId":22,"status":"DRAFT"},
       "entities":[{"entity":{"id":1,"code":"orders","name":"订单"},
       "attributes":[{"id":10,"entityId":1,"code":"order_id","name":"订单标识","stdFieldId":11}]},
       {"entity":{"id":2,"code":"order_detail","name":"订单明细"},"attributes":[]}],
       "relations":[{"sourceEntityId":1,"targetEntityId":2,"cardinality":"UNKNOWN"}]}
      """;

  @BeforeEach void setup() {
    logical = mock(LogicalDraftService.class);
    repository = mock(ModelRepository.class);
    structure = mock(ModelStructureService.class);
    semantic = mock(ProcessApi.class);
    preview = new LogicalPhysicalHandoffPreviewService(logical, repository, structure, semantic,
        new ObjectMapper());
  }

  private void target(Long physicalFieldId) {
    when(logical.versionSnapshot(77L, 2)).thenReturn(SNAPSHOT);
    Model model = Model.create("dwd_orders", "订单明细", ModelDialect.DORIS, "", null, "DWD", 22L)
        .withPersisted(50L, "tester", null, null);
    when(repository.findById(50L)).thenReturn(Optional.of(model));
    StructureView.ColumnView column = new StructureView.ColumnView(10L, "order_id", "BIGINT",
        null, null, false, null, null, null, 0, null, null, null, null, null, null,
        physicalFieldId, null, null, null);
    StructureView value = new StructureView(50L, "dwd_orders", "订单明细", "DORIS", "DRAFT",
        "", "dwd_orders", "", List.of(column), List.of(), List.of(), null, java.util.Map.of());
    when(structure.get(50L)).thenReturn(value);
    if (physicalFieldId != null) {
      when(semantic.getField(physicalFieldId)).thenReturn(new StandardField(physicalFieldId,
          "order_id", "订单标识", "PROCESS", "ENABLED", "BIGINT", null, null, null,
          null, null, "", "MANUAL", 1, false, "tester", null, null));
    }
  }

  @Test void rejectsOtherProjectPhysicalModel() {
    when(logical.versionSnapshot(77L, 2)).thenReturn(SNAPSHOT);
    assertThrows(ModelingException.class, () -> preview.preview(77L, 2, 50L));
    verifyNoInteractions(structure, semantic);
  }

  @Test void flagsMissingStandardWithoutGeneratingAnything() {
    target(null);
    var result = preview.preview(77L, 2, 50L);
    assertFalse(result.readyForReview());
    assertEquals("MISSING_STANDARD", result.columns().get(0).result());
    assertFalse(result.blockers().isEmpty());
  }

  @Test void equalStandardIdsRemainCandidatesNotApprovedMappings() {
    target(11L);
    var result = preview.preview(77L, 2, 50L);
    assertFalse(result.readyForReview()); // relation cardinality still UNKNOWN
    assertEquals("CANDIDATE_ONLY", result.columns().get(0).result());
    assertEquals("订单", result.columns().get(0).logicalEntity());
    assertTrue(result.blockers().stream().anyMatch(b -> b.contains("关系基数")));
  }

  @Test void rejectsForgedSnapshotIdentity() {
    target(11L);
    when(logical.versionSnapshot(77L, 2)).thenReturn(SNAPSHOT.replace("\"id\":77", "\"id\":99"));
    assertThrows(ModelingException.class, () -> preview.preview(77L, 2, 50L));
  }

  @Test void anchorsCandidatesWithStableIdsAndTwoInputFingerprints() {
    target(11L);
    when(logical.versionSnapshot(77L, 2)).thenReturn(SNAPSHOT.replace("UNKNOWN", "ONE_TO_MANY"));
    var evidence = preview.preview(77L, 2, 50L);
    assertTrue(evidence.readyForReview());
    assertTrue(evidence.blockers().isEmpty());
    assertEquals(64, evidence.logicalSnapshotSha256().length());
    assertEquals(64, evidence.physicalStructureSha256().length());
    assertEquals(10L, evidence.columns().get(0).physicalColumnId());
    assertEquals(1L, evidence.columns().get(0).logicalEntityId());
    assertEquals(10L, evidence.columns().get(0).logicalAttributeId());
    assertEquals("CANDIDATE_ONLY", evidence.columns().get(0).result());
  }

  @Test void detectsDuplicatedPhysicalStandardReferencesRatherThanGuessingByName() {
    target(11L);
    var first = structure.get(50L).columns().get(0);
    var second = new StructureView.ColumnView(12L, "order_id_copy", "BIGINT",
        null, null, true, null, null, null, 1, null, null, null, null, null, null,
        11L, null, null, null);
    var current = structure.get(50L);
    when(structure.get(50L)).thenReturn(new StructureView(50L, "dwd_orders", "订单明细",
        "DORIS", "DRAFT", "", "dwd_orders", "",
        List.of(first, second), List.of(), List.of(), null, java.util.Map.of()));
    var result = preview.preview(77L, 2, 50L);
    assertFalse(result.readyForReview());
    assertEquals("AMBIGUOUS_PHYSICAL_REFERENCE", result.columns().get(0).result());
    assertEquals("AMBIGUOUS_PHYSICAL_REFERENCE", result.columns().get(1).result());
  }

  @Test void surfacesUnboundLogicalAttributesInsteadOfHidingThem() {
    target(11L);
    String extra = "{\"id\":20,\"entityId\":1,\"code\":\"amount\",\"name\":\"金额\"},";
    when(logical.versionSnapshot(77L, 2)).thenReturn(
        SNAPSHOT.replace("\"attributes\":[", "\"attributes\":[" + extra));
    var result = preview.preview(77L, 2, 50L);
    assertFalse(result.readyForReview());
    assertTrue(result.columns().stream().anyMatch(c ->
        "MISSING_LOGICAL_STANDARD".equals(c.result())
            && c.logicalAttributeId() != null && c.logicalAttributeId() == 20L));
  }

  @Test void invalidatesStaleCrossEntityAttributeIdentity() {
    target(11L);
    when(logical.versionSnapshot(77L, 2)).thenReturn(
        SNAPSHOT.replace("\"entityId\":1,\"code\":\"order_id\"",
            "\"entityId\":2,\"code\":\"order_id\""));
    var result = preview.preview(77L, 2, 50L);
    assertFalse(result.readyForReview());
    assertTrue(result.blockers().stream().anyMatch(s -> s.contains("跨实体")));
  }

  @Test void rejectsAStalePhysicalStructureProjection() {
    target(11L);
    var old = structure.get(50L);
    when(structure.get(50L)).thenReturn(new StructureView(999L,
        old.modelCode(), old.modelName(), old.dialect(), old.status(),
        old.modelDescription(), old.tableName(), old.tableComment(),
        old.columns(), old.primaryKey(), old.indexes(), old.partition(),
        old.tableProperties()));
    assertThrows(ModelingException.class, () -> preview.preview(77L, 2, 50L));
  }

  @Test void fingerprintsChangeWhenLogicalSnapshotDrifts() {
    target(11L);
    var before = preview.preview(77L, 2, 50L);
    when(logical.versionSnapshot(77L, 2)).thenReturn(
        SNAPSHOT.replace("订单标识", "订单业务标识"));
    var after = preview.preview(77L, 2, 50L);
    assertNotEquals(before.logicalSnapshotSha256(), after.logicalSnapshotSha256());
    assertEquals(before.physicalStructureSha256(), after.physicalStructureSha256());
  }

}
