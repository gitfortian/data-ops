package io.yak.ops.business.agent.conversation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.agentscope.core.state.InMemoryAgentStateStore;
import io.yak.ops.business.agent.runtime.SourceSemanticChunkPlanner;
import io.yak.ops.business.agent.runtime.SourceSemanticScope;
import io.yak.ops.business.agent.runtime.SourceSemanticStateBridge;
import io.yak.ops.business.agent.runtime.SourceSemanticTaskLedger;
import io.yak.ops.business.metadata.api.PhysicalScopeEvidenceQueryApi;
import io.yak.ops.business.semantic.api.SemanticCandidateCatalogApi;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SourceSemanticCandidateServiceTest {
  private final SourceSemanticTaskFacade source=mock(SourceSemanticTaskFacade.class);
  private final SemanticCandidateCatalogApi catalog=mock(SemanticCandidateCatalogApi.class);
  private final AgentSkillManageService skills=mock(AgentSkillManageService.class);
  private SourceSemanticCandidateService service;
  private SourceSemanticTaskLedger taskLedger;
  private String taskId="b380e372-ea27-4ce6-861d-8de744c5b367";
  private static final String HASH="a".repeat(64);
  private static final String RESULT="b".repeat(64);

  private SemanticCandidateCatalogApi.Snapshot semantic() {
    return new SemanticCandidateCatalogApi.Snapshot(31L,List.of(
        new SemanticCandidateCatalogApi.Entry("TYPE",9L,1,"DECIMAL","Decimal","ENABLED",null,null,null),
        new SemanticCandidateCatalogApi.Entry("UNIT",10L,1,"CNY","人民币","ENABLED",null,null,null)),true);
  }

  @BeforeEach void setup() {
    var state=new SourceSemanticStateBridge(new InMemoryAgentStateStore());
    taskLedger=state.ledger();
    var scope=new SourceSemanticScope(31L,"7","warehouse","public","capture-1",
        List.of(new SourceSemanticScope.Table("orders-key",HASH,List.of("id","amount"))));
    var initial=taskLedger.create(taskId,"42","session-1",scope,2,40,HASH,8,100);
    var approved=taskLedger.confirmPlan("42",31L,taskId,scope.fingerprint(),HASH);
    String chunk=approved.chunkIds().get(0);
    taskLedger.reserveTurn("42",31L,taskId,scope.fingerprint(),HASH,chunk,"turn-1",8);
    var finished=taskLedger.completeTurn("42",31L,taskId,scope.fingerprint(),HASH,chunk,
        "turn-1",RESULT);
    var evidence=new PhysicalScopeEvidenceQueryApi.Evidence(31L,"7","warehouse","public",
        "capture-1","2026-10-10","original-source-fingerprint",List.of(
        new PhysicalScopeEvidenceQueryApi.Table("orders-key","orders","table-hash",2,List.of(
            new PhysicalScopeEvidenceQueryApi.Column("id","col-1","BIGINT",true,"primary key"),
            new PhysicalScopeEvidenceQueryApi.Column("amount","col-2","DECIMAL",false,"订单金额")))));
    when(source.verifiedCandidateInput(taskId))
        .thenReturn(new SourceSemanticTaskFacade.CandidateInput(finished,evidence));
    when(catalog.read()).thenReturn(semantic());
    when(skills.list()).thenReturn(List.of());
    service=new SourceSemanticCandidateService(source,catalog,skills,state);
  }

  @Test void draftsAreSourceBoundAndNeverAutoSelected() {
    var view=service.read(taskId);
    assertEquals(12,view.review().candidates().size()); // domain, process, 2*(field,binding,source), 2 TYPE + UNIT + CODE
    assertTrue(view.review().selectedIds().isEmpty());
    assertEquals(2,view.review().candidates().stream().filter(c->"FIELD".equals(c.kind())).count());
    assertTrue(view.review().candidates().stream()
        .allMatch(c->!c.evidence().isEmpty()));
    assertNull(service.preflight(taskId,1).ticket());
  }

  @Test void fieldRequiresRealEnabledTypeAndFreshRevision() {
    var view=service.read(taskId);
    var field=view.review().candidates().stream().filter(c->
        "FIELD".equals(c.kind())&&"amount".equals(c.name())).findFirst().orElseThrow();
    var selected=service.select(taskId,
        new SourceSemanticCandidateService.Selection(1,List.of(field.id())));
    var blocked=service.preflight(taskId,selected.review().revision());
    assertFalse(blocked.ready());
    assertTrue(blocked.blockers().stream().anyMatch(b->b.contains("TYPE_REQUIRED")));
    assertThrows(IllegalStateException.class,()->service.select(taskId,
        new SourceSemanticCandidateService.Selection(1,List.of())));
    var current=selected.review().revision();
    var edited=service.edit(taskId,new SourceSemanticCandidateService.Edit(current,
        field.id(),"ORDER_AMOUNT","订单金额","METRIC",null,"人工确认的金额字段",
        9L,10L,null,null));
    var preflight=service.preflight(taskId,edited.review().revision());
    assertTrue(preflight.ready(),preflight.blockers().toString());
    assertNotNull(preflight.ticket());
    assertEquals(List.of(field.id()),preflight.closure());
  }

  @Test void metricUnitAndUnknownSemanticReferenceFailClosed() {
    var view=service.read(taskId);
    var field=view.review().candidates().stream().filter(c->"FIELD".equals(c.kind()))
        .findFirst().orElseThrow();
    var selected=service.select(taskId,new SourceSemanticCandidateService.Selection(1,
        List.of(field.id())));
    var edited=service.edit(taskId,new SourceSemanticCandidateService.Edit(
        selected.review().revision(),field.id(),"ID_FIELD","ID字段","METRIC",null,
        "人工确认",9L,null,null,null));
    assertTrue(service.preflight(taskId,edited.review().revision()).blockers().stream()
        .anyMatch(b->b.contains("METRIC_UNIT_REQUIRED")));
    var corrected=service.edit(taskId,new SourceSemanticCandidateService.Edit(
        edited.review().revision(),field.id(),"ID_FIELD","ID字段","PROCESS",null,
        "人工确认",9999L,null,null,null));
    assertTrue(service.preflight(taskId,corrected.review().revision()).blockers().stream()
        .anyMatch(b->b.contains("ENABLED_TYPE_REQUIRED")));
  }

  @Test void unselectedDependenciesAreVisibleNotImplicitlyAdopted() {
    var initial=service.read(taskId);
    var binding=initial.review().candidates().stream()
        .filter(c->"PROCESS_FIELD".equals(c.kind())).findFirst().orElseThrow();
    var changed=service.select(taskId,new SourceSemanticCandidateService.Selection(1,
        List.of(binding.id())));
    var preview=service.preflight(taskId,changed.review().revision());
    assertFalse(preview.ready());
    assertTrue(preview.closure().size()>preview.selected().size());
    assertTrue(preview.blockers().stream().anyMatch(b->b.contains("DEPENDENCY_NOT_SELECTED")));
  }

  @Test void catalogDriftAndCrossProjectResultNeverProduceTicket() {
    var view=service.read(taskId);
    when(catalog.read()).thenReturn(new SemanticCandidateCatalogApi.Snapshot(31L,List.of(),true));
    var drift=service.preflight(taskId,view.review().revision());
    assertFalse(drift.ready());
    assertNull(drift.ticket());
    assertTrue(drift.blockers().stream().anyMatch(b->b.contains("CATALOG_CHANGED")));
    when(catalog.read()).thenReturn(new SemanticCandidateCatalogApi.Snapshot(99L,List.of(),true));
    assertThrows(IllegalStateException.class,()->service.preflight(taskId,view.review().revision()));
  }

  @Test void skillChangeInvalidatesPreviouslyReadyPreflight() {
    var view=service.read(taskId);
    var field=view.review().candidates().stream().filter(c->"FIELD".equals(c.kind()))
        .findFirst().orElseThrow();
    var selected=service.select(taskId,
        new SourceSemanticCandidateService.Selection(1,List.of(field.id())));
    when(skills.list()).thenReturn(List.of(new io.yak.ops.business.agent.domain.AgentSkillBrief(
        "review-skill","source review","description",Map.of(),"changed instruction",
        true,2,null,null)));
    var preflight=service.preflight(taskId,selected.review().revision());
    assertFalse(preflight.ready());
    assertNull(preflight.ticket());
    assertTrue(preflight.blockers().stream().anyMatch(b->b.contains("SKILL_CHANGED")));
  }

  @Test void answersInvalidateOlderReviewRevision() {
    var value=service.read(taskId);
    var next=service.answer(taskId,new SourceSemanticCandidateService.Answer(1,
        "business_domain","客户管理和订单支付按独立过程管理"));
    assertEquals(2,next.review().revision());
    assertEquals("客户管理和订单支付按独立过程管理",
        next.review().answers().get("business_domain"));
    assertThrows(IllegalStateException.class,()->service.preflight(taskId,1));
  }

  @Test void explicitMergeAndSplitPreserveAllColumnWitnessesAndRewireBindings() {
    var initial=service.read(taskId);
    var fields=initial.review().candidates().stream()
        .filter(c->"FIELD".equals(c.kind())).toList();
    assertEquals(2,fields.size());
    assertThrows(IllegalArgumentException.class,()->service.merge(taskId,
        new SourceSemanticCandidateService.Merge(1,fields.get(0).id(),fields.get(1).id(),false)));
    var merged=service.merge(taskId,new SourceSemanticCandidateService.Merge(1,
        fields.get(0).id(),fields.get(1).id(),true));
    assertEquals(2,merged.review().revision());
    var combined=merged.review().candidates().stream()
        .filter(c->"FIELD".equals(c.kind())).findFirst().orElseThrow();
    assertEquals(2,combined.evidence().size());
    assertTrue(merged.review().candidates().stream()
        .filter(c->"PROCESS_FIELD".equals(c.kind()))
        .allMatch(c->c.dependencies().contains(combined.id())));
    var toSplit=combined.evidence().get(1);
    var split=service.split(taskId,new SourceSemanticCandidateService.Split(
        merged.review().revision(),combined.id(),toSplit.tableAssetKey(),toSplit.column()));
    assertEquals(3,split.review().revision());
    assertEquals(2,split.review().candidates().stream()
        .filter(c->"FIELD".equals(c.kind())).count());
    assertEquals(12,split.review().candidates().size());
    assertEquals(2,split.review().candidates().stream()
        .filter(c->"PROCESS_FIELD".equals(c.kind()))
        .map(c->c.dependencies().get(1)).distinct().count());
    assertTrue(split.review().selectedIds().isEmpty());
  }

  @Test void incompleteTurnNeverYieldsDraft() {
    when(source.verifiedCandidateInput(taskId))
        .thenThrow(new IllegalStateException("[F039_ANALYSIS_NOT_COMPLETE]"));
    assertThrows(IllegalStateException.class,()->service.read(taskId));
  }
}
