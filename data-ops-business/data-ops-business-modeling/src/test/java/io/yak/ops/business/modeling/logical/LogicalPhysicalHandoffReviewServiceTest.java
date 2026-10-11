package io.yak.ops.business.modeling.logical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.logical.LogicalPhysicalHandoffPreviewService.ColumnCheck;
import io.yak.ops.business.modeling.logical.LogicalPhysicalHandoffPreviewService.Preview;
import io.yak.ops.business.modeling.logical.LogicalPhysicalHandoffReviewService.ReviewRequest;
import io.yak.ops.business.modeling.logical.LogicalPhysicalHandoffReviewService.Selection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogicalPhysicalHandoffReviewServiceTest {
  private LogicalPhysicalHandoffPreviewService preview;
  private LogicalPhysicalHandoffReviewService service;

  private static ColumnCheck column(long colId, long attrId, long standard) {
    return new ColumnCheck("col_" + colId, standard, "订单", "attribute_" + attrId,
        "CANDIDATE_ONLY", "候选", colId, 1L, attrId);
  }

  private static Preview evidence(List<String> blockers, List<ColumnCheck> checks) {
    return new Preview(77L, 2, 50L, "DWD", "DRAFT", blockers.isEmpty(), blockers,
        checks, "logical-sha", "physical-sha");
  }

  @BeforeEach void setup() {
    preview = mock(LogicalPhysicalHandoffPreviewService.class);
    service = new LogicalPhysicalHandoffReviewService(preview);
    when(preview.preview(77L, 2, 50L)).thenReturn(
        evidence(List.of(), List.of(column(100L, 10L, 11L))));
  }

  private static ReviewRequest req(String logical, String physical, Selection... rows) {
    return new ReviewRequest(logical, physical, List.of(rows));
  }

  @Test void requiresExplicitMatchingIdentityAndDoesNotWrite() {
    var result = service.validate(77L, 2, 50L,
        req("logical-sha", "physical-sha", new Selection(10L, 100L)));
    assertTrue(result.evidenceUnchanged());
    assertTrue(result.readyForManualDesign());
    assertEquals("REVIEWABLE_ONLY", result.status());
    assertEquals(1, result.reviewedMappings().size());
    assertEquals(11L, result.reviewedMappings().get(0).stdFieldId());
    verify(preview).preview(77L, 2, 50L);
    verifyNoMoreInteractions(preview);
  }

  @Test void rejectsStaleInputAndDoesNotUsePreviouslySelectedPairs() {
    var result = service.validate(77L, 2, 50L,
        req("stale-logical", "physical-sha", new Selection(10L, 100L)));
    assertFalse(result.evidenceUnchanged());
    assertFalse(result.readyForManualDesign());
    assertTrue(result.reviewedMappings().isEmpty());
    assertTrue(result.blockers().stream().anyMatch(b -> b.contains("变化")));
  }

  @Test void rejectsCrossVersionOrForgedIds() {
    var result = service.validate(77L, 2, 50L,
        req("logical-sha", "physical-sha", new Selection(123L, 100L)));
    assertFalse(result.readyForManualDesign());
    assertTrue(result.blockers().stream().anyMatch(b -> b.contains("可访问范围")));
  }

  @Test void forbidsRepeatedPhysicalOrLogicalIdentities() {
    var result = service.validate(77L, 2, 50L,
        req("logical-sha", "physical-sha",
            new Selection(10L, 100L), new Selection(10L, 100L)));
    assertFalse(result.readyForManualDesign());
    assertTrue(result.blockers().stream().anyMatch(b -> b.contains("一对一")));
  }

  @Test void mismatchedStdFieldIdsDoNotBecomeNameBasedMappings() {
    when(preview.preview(77L, 2, 50L)).thenReturn(evidence(List.of(),
        List.of(column(100L, 10L, 11L),
            new ColumnCheck(null, 12L, "订单", "同名属性", "LOGICAL_ATTRIBUTE_UNMAPPED",
                "未匹配", null, 1L, 20L))));
    var result = service.validate(77L, 2, 50L,
        req("logical-sha", "physical-sha", new Selection(20L, 100L)));
    assertFalse(result.readyForManualDesign());
    assertTrue(result.blockers().stream().anyMatch(b -> b.contains("标准字段身份不一致")));
  }

  @Test void manualChoiceCanResolvePreviewAmbiguityWithoutSavingAnything() {
    var first = new ColumnCheck("order_id", 11L, null, null,
        "AMBIGUOUS_PHYSICAL_REFERENCE", "人工选择", 100L, null, null);
    var second = new ColumnCheck("order_key", 11L, null, null,
        "AMBIGUOUS_PHYSICAL_REFERENCE", "人工选择", 101L, null, null);
    var logicOne = new ColumnCheck(null, 11L, "订单", "主标识",
        "LOGICAL_ATTRIBUTE_UNMAPPED", "未覆盖", null, 1L, 10L);
    var logicTwo = new ColumnCheck(null, 11L, "订单", "业务标识",
        "LOGICAL_ATTRIBUTE_UNMAPPED", "未覆盖", null, 1L, 20L);
    when(preview.preview(77L, 2, 50L)).thenReturn(evidence(List.of(
        "存在缺失、失效、未覆盖或歧义的字段引用；仅允许人工修正后重新预检"),
        List.of(first, second, logicOne, logicTwo)));
    var result = service.validate(77L, 2, 50L,
        req("logical-sha", "physical-sha", new Selection(10L, 100L),
            new Selection(20L, 101L)));
    assertTrue(result.readyForManualDesign());
    assertEquals(2, result.reviewedMappings().size());
  }

  @Test void unresolvedRelationAlwaysBlocksEvenIfAllColumnsSelected() {
    when(preview.preview(77L, 2, 50L)).thenReturn(evidence(
        List.of("逻辑模型含未确认/无效的实体关系基数，不能自动推导 JOIN"),
        List.of(column(100L, 10L, 11L))));
    var result = service.validate(77L, 2, 50L,
        req("logical-sha", "physical-sha", new Selection(10L, 100L)));
    assertFalse(result.readyForManualDesign());
    assertFalse(result.blockers().isEmpty());
  }

  @Test void missingPhysicalSelectionIsVisibleNotAutoFilled() {
    when(preview.preview(77L, 2, 50L)).thenReturn(evidence(List.of(),
        List.of(column(100L, 10L, 11L), column(101L, 20L, 12L))));
    var result = service.validate(77L, 2, 50L,
        req("logical-sha", "physical-sha", new Selection(10L, 100L)));
    assertFalse(result.readyForManualDesign());
    assertTrue(result.blockers().stream().anyMatch(b -> b.contains("物理列未")));
  }

  @Test void permissionOrCrossProjectErrorsAreNotDowngradedToAnEmptyList() {
    when(preview.preview(77L, 2, 50L)).thenThrow(ModelingException.class);
    assertThrows(ModelingException.class, () -> service.validate(77L, 2, 50L,
        req("logical-sha", "physical-sha", new Selection(10L, 100L))));
  }
}
