package io.yak.ops.business.mdm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskDraft;
import io.yak.ops.business.development.node.DevelopmentNodeService;
import io.yak.ops.business.development.service.DevelopmentDraftConflictException;
import io.yak.ops.business.development.task.DevelopmentTaskService;
import io.yak.ops.business.mdm.domain.collect.MdmCollectLink;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.entity.MdmEntityStatus;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmCollectLinkRepository;
import io.yak.ops.business.sync.offline.definition.OfflineJobDefinitionService;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobDefinitionVO;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

/** 加工任务注册单元测试(R2):find-or-create SQL 节点、草稿载荷、sink 预填、冲突重试、降级。 */
class MdmProcessingTaskServiceTest {

  private static final String TASK_NAME = "MDM主数据加工-customer";

  private MdmRecordService recordService;
  private MdmCollectLinkRepository linkRepository;
  private DevelopmentNodeService nodes;
  private DevelopmentTaskService tasks;
  private OfflineJobDefinitionService definitions;
  private MdmProcessingTaskService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    recordService = mock(MdmRecordService.class);
    MdmEntityService entityService = mock(MdmEntityService.class);
    linkRepository = mock(MdmCollectLinkRepository.class);
    nodes = mock(DevelopmentNodeService.class);
    tasks = mock(DevelopmentTaskService.class);
    definitions = mock(OfflineJobDefinitionService.class);
    ObjectProvider<DevelopmentNodeService> nodeProvider = mock(ObjectProvider.class);
    ObjectProvider<DevelopmentTaskService> taskProvider = mock(ObjectProvider.class);
    ObjectProvider<OfflineJobDefinitionService> definitionProvider = mock(ObjectProvider.class);
    lenient().when(nodeProvider.getIfAvailable()).thenReturn(nodes);
    lenient().when(taskProvider.getIfAvailable()).thenReturn(tasks);
    lenient().when(definitionProvider.getIfAvailable()).thenReturn(definitions);
    lenient()
        .when(entityService.get(1L))
        .thenReturn(
            new MdmEntity(
                1L, "customer", "客户", MdmEntityStatus.ACTIVE, "root", null, "root",
                null, null));
    lenient().when(recordService.generateMasterSql(1L)).thenReturn("INSERT SQL;");
    lenient().when(nodes.list()).thenReturn(List.of());
    lenient()
        .when(tasks.getDraft(anyLong()))
        .thenReturn(new DevelopmentTaskDraft(7L, new TaskDefinition("SQL", 1, "", "{}"), 0L, null, null));
    lenient().when(linkRepository.listByEntity(1L)).thenReturn(List.of());
    service =
        new MdmProcessingTaskService(
            recordService, entityService, linkRepository, nodeProvider, taskProvider, definitionProvider);
  }

  @Test
  void createsNodeAndSavesDraftWithSinkDatasourcePrefill() {
    when(nodes.create(TASK_NAME, "SQL", null)).thenReturn(node(7L, TASK_NAME, "SQL"));
    when(linkRepository.listByEntity(1L))
        .thenReturn(List.of(link("mdm_landing_customer_1", 123L)));
    OfflineJobDefinitionVO definition = new OfflineJobDefinitionVO();
    definition.setSinkDatasourceId(3L);
    when(definitions.get(123L)).thenReturn(definition);

    MdmProcessingTaskService.ProcessingTaskReceipt receipt = service.register(1L);

    assertEquals(7L, receipt.nodeId());
    assertTrue(receipt.nodeCreated());
    assertEquals(TASK_NAME, receipt.taskName());
    ArgumentCaptor<String> config = ArgumentCaptor.forClass(String.class);
    verify(tasks)
        .saveDraft(eq(7L), eq("SQL"), eq(1), eq("INSERT SQL;"), config.capture(), eq(0L));
    assertEquals("{\"dialect\":\"MYSQL\",\"dataSourceId\":\"3\"}", config.getValue());
  }

  @Test
  void reusesExistingNodeAndOmitsDatasourceWhenUnresolvable() {
    when(nodes.list()).thenReturn(List.of(node(9L, TASK_NAME, "SQL")));
    when(definitions.get(any())).thenThrow(new IllegalStateException("sync down"));

    MdmProcessingTaskService.ProcessingTaskReceipt receipt = service.register(1L);

    assertEquals(9L, receipt.nodeId());
    assertFalse(receipt.nodeCreated());
    verify(nodes, never()).create(anyString(), anyString(), any());
    verify(tasks).saveDraft(eq(9L), eq("SQL"), eq(1), eq("INSERT SQL;"), eq("{\"dialect\":\"MYSQL\"}"), eq(0L));
  }

  @Test
  void rejectsSameNameNonSqlNode() {
    when(nodes.list()).thenReturn(List.of(node(9L, TASK_NAME, "SHELL")));
    MdmException exception = assertThrows(MdmException.class, () -> service.register(1L));
    assertEquals(MdmErrorCode.PROCESSING_TASK_REGISTER_FAILED, exception.getErrorCode());
    verify(tasks, never()).saveDraft(any(), any(), anyInt(), any(), any(), any());
  }

  @Test
  void retriesOnceOnDraftConflict() {
    when(nodes.create(TASK_NAME, "SQL", null)).thenReturn(node(7L, TASK_NAME, "SQL"));
    when(tasks.getDraft(7L))
        .thenReturn(new DevelopmentTaskDraft(7L, new TaskDefinition("SQL", 1, "", "{}"), 0L, null, null))
        .thenReturn(new DevelopmentTaskDraft(7L, new TaskDefinition("SQL", 1, "", "{}"), 5L, null, null));
    when(tasks.saveDraft(any(), any(), anyInt(), any(), any(), any()))
        .thenThrow(new DevelopmentDraftConflictException("基线不符"))
        .thenReturn(null);

    service.register(1L);

    verify(tasks).saveDraft(eq(7L), eq("SQL"), eq(1), eq("INSERT SQL;"), any(), eq(5L));
  }

  @Test
  void surfacesPersistentConflictAsMdmException() {
    when(nodes.create(TASK_NAME, "SQL", null)).thenReturn(node(7L, TASK_NAME, "SQL"));
    when(tasks.saveDraft(any(), any(), anyInt(), any(), any(), any()))
        .thenThrow(new DevelopmentDraftConflictException("基线不符"));
    MdmException exception = assertThrows(MdmException.class, () -> service.register(1L));
    assertEquals(MdmErrorCode.PROCESSING_TASK_REGISTER_FAILED, exception.getErrorCode());
    verify(tasks, Mockito.times(2)).saveDraft(any(), any(), anyInt(), any(), any(), any());
  }

  @Test
  void blocksValidationFailureBeforeTouchingDevelopment() {
    when(recordService.generateMasterSql(1L))
        .thenThrow(new MdmException(MdmErrorCode.SOURCE_NOT_LANDED, "crm_customer"));
    MdmException exception = assertThrows(MdmException.class, () -> service.register(1L));
    assertEquals(MdmErrorCode.SOURCE_NOT_LANDED, exception.getErrorCode());
    verify(nodes, never()).list();
  }

  @Test
  @SuppressWarnings("unchecked")
  void failsCleanlyWhenDataDevelopmentModuleAbsent() {
    ObjectProvider<DevelopmentNodeService> emptyNodes = mock(ObjectProvider.class);
    ObjectProvider<DevelopmentTaskService> emptyTasks = mock(ObjectProvider.class);
    ObjectProvider<OfflineJobDefinitionService> emptyDefs = mock(ObjectProvider.class);
    when(emptyNodes.getIfAvailable()).thenReturn(null);
    MdmProcessingTaskService detached =
        new MdmProcessingTaskService(
            recordService,
            mock(MdmEntityService.class),
            mock(MdmCollectLinkRepository.class),
            emptyNodes,
            emptyTasks,
            emptyDefs);
    MdmException exception = assertThrows(MdmException.class, () -> detached.register(1L));
    assertEquals(MdmErrorCode.PROCESSING_TASK_REGISTER_FAILED, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("数据开发模块未启用"));
  }

  private DevelopmentNode node(Long id, String name, String type) {
    return new DevelopmentNode(
        id, name, type, 1L, null, false, Instant.EPOCH, Instant.EPOCH, "root", false, null);
  }

  private MdmCollectLink link(String landingTable, Long jobDefinitionId) {
    return new MdmCollectLink(
        null, 1L, 1L, 9L, landingTable, jobDefinitionId, "root", null, null);
  }
}
