package io.yak.ops.business.modeling.logical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.modeling.dao.model.LogicalAttributePO;
import io.yak.ops.business.modeling.dao.model.LogicalEntityPO;
import io.yak.ops.business.modeling.dao.model.LogicalModelPO;
import io.yak.ops.business.modeling.dao.model.LogicalRelationPO;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.semantic.api.BusinessProcess;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.StandardField;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogicalVersionSemanticReviewServiceTest {
  private LogicalDraftService drafts;
  private ProcessApi semantic;
  private ObjectMapper json;
  private LogicalVersionSemanticReviewService service;
  private LogicalDraftService.DraftView frozen;
  private LogicalDraftService.DraftView current;
  private String snapshot;

  private static LogicalAttributePO attr(long id, Long standard) {
    LogicalAttributePO a = new LogicalAttributePO();
    a.setId(id);
    a.setEntityId(5L);
    a.setCode("order_id");
    a.setName("订单标识");
    a.setStdFieldId(standard);
    a.setLogicalType("STRING");
    a.setPrimaryFlag(true);
    a.setNullable(false);
    return a;
  }
  private static LogicalEntityPO entity() {
    LogicalEntityPO e = new LogicalEntityPO();
    e.setId(5L);
    e.setLogicalModelId(77L);
    e.setCode("order");
    e.setName("订单");
    return e;
  }
  private static LogicalModelPO model() {
    LogicalModelPO m = new LogicalModelPO();
    m.setId(77L);
    m.setProjectId(101L);
    m.setProcessId(22L);
    m.setCode("order_business");
    m.setName("订单业务模型");
    m.setStatus("DRAFT");
    m.setDraftRevision(1L);
    return m;
  }
  private static BusinessProcess process() {
    return new BusinessProcess(22L, "place_order", "下单", 9L,
        "一笔订单一行", "FACT", "business", null, 0, "owner", null, null);
  }
  private static StandardField standard(String status) {
    return new StandardField(11L, "order_id", "订单标识", StandardField.ROLE_PROCESS,
        status, "VARCHAR", 3L, null, null, null, null, "订单ID", "MANUAL",
        1, true, "business", null, null);
  }
  private static LogicalRelationPO relation(String cardinality, Long target) {
    LogicalRelationPO relation = new LogicalRelationPO();
    relation.setId(30L);
    relation.setSourceEntityId(5L);
    relation.setTargetEntityId(target);
    relation.setCardinality(cardinality);
    relation.setRelationType("ASSOCIATION");
    return relation;
  }
  private static LogicalDraftService.DraftView view(Long standardId,
      List<LogicalRelationPO> relations) {
    return new LogicalDraftService.DraftView(model(), process(),
        List.of(new LogicalDraftService.EntityView(entity(), List.of(attr(10L, standardId)))),
        relations);
  }

  @BeforeEach void setup() throws Exception {
    drafts = mock(LogicalDraftService.class);
    semantic = mock(ProcessApi.class);
    json = new ObjectMapper().findAndRegisterModules();
    service = new LogicalVersionSemanticReviewService(drafts, semantic, json);
    frozen = view(11L, List.of());
    current = view(11L, List.of());
    snapshot = json.writeValueAsString(frozen);
    when(drafts.versionSnapshot(77L, 1)).thenAnswer(i -> snapshot);
    when(drafts.get(77L)).thenAnswer(i -> current);
    when(semantic.getField(11L)).thenReturn(standard(StandardField.STATUS_ENABLED));
  }

  @Test void stableSnapshotMatchesCurrentDespiteDraftRevisionOrRowTimestamps() {
    var report = service.review(77L, 1);
    assertEquals("MATCHES_CURRENT", report.designComparison());
    assertEquals("REVIEWABLE_ONLY", report.reviewStatus());
    assertEquals(64, report.frozenSnapshotSha256().length());
    assertEquals("ENABLED_NOW", report.attributes().get(0).standardStatus());
    assertEquals(StandardField.ROLE_PROCESS, report.attributes().get(0).standardRole());
    assertTrue(report.changes().isEmpty());
    current.model().setDraftRevision(900L);
    current.model().setOwner("other_user");
    assertEquals(report.currentDesignSha256(), service.review(77L, 1).currentDesignSha256());
    verify(drafts, never()).freezeDraft(anyLong(), anyString(), anyLong());
  }

  @Test void editorChangesAfterFreezeProduceStableIdDeltaWithoutChangingSnapshot() {
    var modified = view(11L, List.of());
    modified.entities().get(0).attributes().get(0).setName("业务订单号");
    current = modified;
    var report = service.review(77L, 1);
    assertEquals("DIFFERS_FROM_CURRENT", report.designComparison());
    assertEquals("NEEDS_REVIEW", report.reviewStatus());
    assertEquals(1, report.changes().size());
    assertEquals("ATTRIBUTE", report.changes().get(0).objectType());
    assertEquals("CHANGED_NOW", report.changes().get(0).status());
    assertEquals(64, report.frozenDesignSha256().length());
  }

  @Test void missingStandardIsPendingRatherThanSilentlyMatchedByName() throws Exception {
    frozen = view(null, List.of());
    snapshot = json.writeValueAsString(frozen);
    var report = service.review(77L, 1);
    assertEquals("PENDING_STANDARD", report.attributes().get(0).standardStatus());
    assertEquals("NEEDS_REVIEW", report.reviewStatus());
    verify(semantic, never()).getField(anyLong());
  }

  @Test void disabledOrUnavailableStandardMustNotClaimSemanticReviewSuccess() {
    when(semantic.getField(11L)).thenReturn(standard(StandardField.STATUS_DISABLED));
    var first = service.review(77L, 1);
    assertEquals("INVALID_STANDARD", first.attributes().get(0).standardStatus());
    assertEquals("NEEDS_REVIEW", first.reviewStatus());
    when(semantic.getField(11L)).thenReturn(null);
    assertEquals("INVALID_STANDARD", service.review(77L, 1).attributes().get(0).standardStatus());
  }

  @Test void unknownCardinalityAlwaysRequiresBusinessConfirmation() throws Exception {
    frozen = view(11L, List.of(relation("UNKNOWN", 5L)));
    current = view(11L, List.of(relation("UNKNOWN", 5L)));
    snapshot = json.writeValueAsString(frozen);
    var report = service.review(77L, 1);
    assertEquals("NEEDS_REVIEW", report.reviewStatus());
    assertEquals("CARDINALITY_PENDING", report.relations().get(0).status());
    assertFalse(report.blockers().isEmpty());
  }

  @Test void declaredCardinalityIsNotMisrepresentedAsBusinessConfirmed() throws Exception {
    frozen = view(11L, List.of(relation("ONE_TO_MANY", 5L)));
    current = view(11L, List.of(relation("ONE_TO_MANY", 5L)));
    snapshot = json.writeValueAsString(frozen);
    var report = service.review(77L, 1);
    assertEquals("DECLARED_NOT_CONFIRMED", report.relations().get(0).status());
    assertTrue(report.limitations().stream().anyMatch(s -> s.contains("人工")));
  }

  @Test void forgedSnapshotModelIdentityAndForeignRelationFailClosed() throws Exception {
    snapshot = snapshot.replace("\"id\":77", "\"id\":99");
    assertThrows(ModelingException.class, () -> service.review(77L, 1));
    frozen = view(11L, List.of(relation("ONE_TO_ONE", 998L)));
    snapshot = json.writeValueAsString(frozen);
    assertThrows(ModelingException.class, () -> service.review(77L, 1));
    verify(semantic, never()).getField(anyLong());
  }

  @Test void noCrossProjectOrOwnerDeniedSemanticLookupIsDowngradedToUnknown() {
    when(drafts.versionSnapshot(77L, 1)).thenThrow(ModelingException.class);
    assertThrows(ModelingException.class, () -> service.review(77L, 1));
    verify(semantic, never()).getField(anyLong());
    when(drafts.versionSnapshot(77L, 1)).thenAnswer(i -> snapshot);
    when(semantic.getField(11L)).thenThrow(SecurityException.class);
    assertThrows(SecurityException.class, () -> service.review(77L, 1));
  }

  @Test void processGrainCannotBeGuessedIfCurrentProcessHasNone() {
    var old = current;
    current = new LogicalDraftService.DraftView(old.model(),
        new BusinessProcess(22L, "place_order", "下单", 9L, null,
            "FACT", "business", null, 0, "owner", null, null),
        old.entities(), old.relations());
    var report = service.review(77L, 1);
    assertEquals("NEEDS_REVIEW", report.reviewStatus());
    assertTrue(report.blockers().stream().anyMatch(s -> s.contains("粒度")));
  }
}
