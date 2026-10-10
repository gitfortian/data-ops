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
       "attributes":[{"code":"order_id","name":"订单标识","stdFieldId":11}]}],
       "relations":[{"cardinality":"UNKNOWN"}]}
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
}
