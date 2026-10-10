package io.yak.ops.business.modeling.logical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.modeling.dao.mapper.*;
import io.yak.ops.business.modeling.dao.model.*;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.semantic.api.*;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** A logical-model authoring draft is never a cross-project or cross-model data escape. */
class LogicalDraftServiceTest {
  private ModelingLogicalModelMapper models;
  private ModelingLogicalEntityMapper entities;
  private ModelingLogicalAttributeMapper attributes;
  private ModelingEntityRelationMapper relations;
  private ModelingLogicalModelVersionMapper versions;
  private ProcessApi processes;
  private LogicalDraftService service;

  private static LogicalModelPO model() {
    LogicalModelPO p = new LogicalModelPO();
    p.setId(77L);
    p.setProjectId(101L);
    p.setCode("order_business");
    p.setName("订单业务模型");
    p.setDomainId(9L);
    p.setProcessId(22L);
    p.setStatus("DRAFT");
    return p;
  }

  private static LogicalEntityPO entity(long id, long modelId) {
    LogicalEntityPO p = new LogicalEntityPO();
    p.setId(id);
    p.setLogicalModelId(modelId);
    p.setCode("order_" + id);
    p.setName("订单实体");
    return p;
  }

  private static StandardField field(String status) {
    return new StandardField(11L, "order_id", "订单标识", "PROCESS", status, "VARCHAR",
        3L, null, null, null, null, "订单主键", StandardField.SOURCE_MANUAL,
        1, true, "tester", null, null);
  }

  @BeforeEach
  void setUp() {
    models = mock(ModelingLogicalModelMapper.class);
    entities = mock(ModelingLogicalEntityMapper.class);
    attributes = mock(ModelingLogicalAttributeMapper.class);
    relations = mock(ModelingEntityRelationMapper.class);
    versions = mock(ModelingLogicalModelVersionMapper.class);
    processes = mock(ProcessApi.class);
    CurrentProject project = () -> Optional.of(new ProjectContext(101L, "交易"));
    service = new LogicalDraftService(project, processes, models, entities, attributes,
        relations, versions, new ObjectMapper().findAndRegisterModules());
    when(processes.listProcesses(null)).thenReturn(List.of(new BusinessProcess(
        22L, "order_create", "下单", 9L, "一行一个订单", "FACT", "tester", null, 0,
        "tester", null, null)));
  }

  @Test
  void projectMissingModelRejectsBeforeReadingChildren() {
    assertThrows(ModelingException.class, () -> service.get(900L));
    verifyNoInteractions(entities, attributes, relations, versions);
  }

  @Test
  void creatingDraftCarriesTrustedProjectAndReferencesProcessWithoutGuessingEntities() {
    when(models.selectOne(any())).thenReturn(model());
    service.create(new LogicalDraftService.NewDraft(22L, "order_business",
        "订单业务模型", "请业务确认粒度"), "tester");
    ArgumentCaptor<LogicalModelPO> saved = ArgumentCaptor.forClass(LogicalModelPO.class);
    verify(models).insert(saved.capture());
    assertEquals(101L, saved.getValue().getProjectId());
    assertEquals(22L, saved.getValue().getProcessId());
    assertEquals(9L, saved.getValue().getDomainId());
    assertEquals("DRAFT", saved.getValue().getStatus());
    verifyNoInteractions(attributes, relations);
  }

  @Test
  void cannotAssociateEntitiesFromDifferentLogicalModels() {
    when(models.selectOne(any())).thenReturn(model());
    when(entities.selectById(1L)).thenReturn(entity(1L, 77L));
    when(entities.selectById(2L)).thenReturn(entity(2L, 88L));
    assertThrows(ModelingException.class, () -> service.addRelation(
        77L, new LogicalDraftService.NewRelation(1L, 2L, "ASSOCIATION",
            "ONE_TO_MANY", "未证明的外部关系")));
    verify(relations, never()).insert(any(LogicalRelationPO.class));
  }

  @Test
  void editRejectsAttributeFromAnotherEntity() {
    when(models.selectOne(any())).thenReturn(model());
    when(entities.selectById(1L)).thenReturn(entity(1L, 77L));
    LogicalAttributePO wrong = new LogicalAttributePO();
    wrong.setId(100L);
    wrong.setEntityId(98L);
    wrong.setCode("order_id");
    when(attributes.selectById(100L)).thenReturn(wrong);
    assertThrows(ModelingException.class, () -> service.updateAttribute(
        77L, 1L, 100L, new LogicalDraftService.NewAttribute(
            "order_id", "订单ID", null, "STRING", null, false, true)));
    verify(attributes, never()).updateById(any(LogicalAttributePO.class));
  }

  @Test
  void editRejectsRebindingRelationshipEndpoints() {
    when(models.selectOne(any())).thenReturn(model());
    when(entities.selectById(1L)).thenReturn(entity(1L, 77L));
    when(entities.selectById(2L)).thenReturn(entity(2L, 77L));
    LogicalRelationPO original = new LogicalRelationPO();
    original.setId(7L);
    original.setSourceEntityId(1L);
    original.setTargetEntityId(2L);
    when(relations.selectById(7L)).thenReturn(original);
    assertThrows(ModelingException.class, () -> service.updateRelation(
        77L, 7L, new LogicalDraftService.NewRelation(2L, 1L, "ASSOCIATION",
            "UNKNOWN", null)));
    verify(relations, never()).updateById(any(LogicalRelationPO.class));
  }

  @Test
  void disabledStandardCannotBeAttachedToNewAttribute() {
    when(models.selectOne(any())).thenReturn(model());
    when(entities.selectById(1L)).thenReturn(entity(1L, 77L));
    when(processes.getField(11L)).thenReturn(field(StandardField.STATUS_DISABLED));
    assertThrows(ModelingException.class, () -> service.addAttribute(
        77L, 1L, new LogicalDraftService.NewAttribute("order_id", "订单ID",
            11L, "STRING", null, true, false)));
    verify(attributes, never()).insert(any(LogicalAttributePO.class));
  }

  @Test
  void frozenVersionKeepsExplicitDraftStateAndSemanticSourceRefs() {
    when(models.selectOne(any())).thenReturn(model());
    LogicalEntityPO rootEntity = entity(1L, 77L);
    when(entities.selectList(any())).thenReturn(List.of(rootEntity));
    LogicalAttributePO attr = new LogicalAttributePO();
    attr.setId(9L);
    attr.setEntityId(1L);
    attr.setCode("order_id");
    attr.setName("订单标识");
    attr.setStdFieldId(11L);
    when(attributes.selectList(any())).thenReturn(List.of(attr));
    when(relations.selectList(any())).thenReturn(List.of());
    when(versions.selectList(any())).thenReturn(List.of());
    LogicalDraftService.VersionView frozen = service.freezeDraft(77L, "tester");

    assertEquals(1, frozen.versionNo());
    assertEquals("DRAFT", frozen.status());
    ArgumentCaptor<LogicalModelVersionPO> saved = ArgumentCaptor.forClass(LogicalModelVersionPO.class);
    verify(versions).insert(saved.capture());
    assertTrue(saved.getValue().getSnapshot().contains("order_create"));
    assertTrue(saved.getValue().getSnapshot().contains("order_id"));
    assertTrue(saved.getValue().getSnapshot().contains("\"stdFieldId\":11"));
    assertEquals("DRAFT", saved.getValue().getStatus());
  }
}
