package io.yak.ops.business.mdm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.framework.common.PagingData;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.mdm.domain.collect.MdmCollectLink;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.source.MdmSource;
import io.yak.ops.business.mdm.domain.source.MdmSourceRole;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmCollectLinkRepository;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobDefinitionDTO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobDefinitionVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobExecutionVO;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import io.yak.ops.business.sync.offline.definition.OfflineJobDefinitionService;
import io.yak.ops.business.sync.offline.execution.OfflineJobExecutionService;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/** R1 采集落地编排单元测试:幂等、任务构造、运行前置上线、状态反查。 */
class MdmCollectServiceTest {

  private MdmCollectLinkRepository linkRepository;
  private MdmSourceService sourceService;
  private MdmEntityService entityService;
  private DataSourceCatalogReader catalogReader;
  private OfflineJobDefinitionService definitionService;
  private OfflineJobExecutionService executionService;
  private MdmCollectService service;

  private final MdmSource source =
      new MdmSource(
          7L, 1L, 9L, "crm_db", null, "crm_customer", null, MdmSourceRole.MAIN,
          MdmSource.STATUS_ENABLED, 0, "tester", null, null);
  private final MdmEntity entity =
      new MdmEntity(1L, "customer", "客户", null, null, null, "tester", null, null);

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    linkRepository = mock(MdmCollectLinkRepository.class);
    sourceService = mock(MdmSourceService.class);
    entityService = mock(MdmEntityService.class);
    catalogReader = mock(DataSourceCatalogReader.class);
    definitionService = mock(OfflineJobDefinitionService.class);
    executionService = mock(OfflineJobExecutionService.class);
    ObjectProvider<OfflineJobDefinitionService> definitionProvider = mock(ObjectProvider.class);
    ObjectProvider<OfflineJobExecutionService> executionProvider = mock(ObjectProvider.class);
    when(definitionProvider.getIfAvailable()).thenReturn(definitionService);
    when(executionProvider.getIfAvailable()).thenReturn(executionService);
    service =
        spy(
            new MdmCollectService(
                linkRepository,
                sourceService,
                entityService,
                catalogReader,
                definitionProvider,
                executionProvider,
                mock(DataSource.class)));
    when(sourceService.get(7L)).thenReturn(source);
    when(entityService.get(1L)).thenReturn(entity);
    when(catalogReader.listColumns(9L, "crm_db", null, "crm_customer")).thenReturn(customerColumns());
    doReturn("yak_security").when(service).businessDatabase();
    doNothing().when(service).preCreateLandingTable(any(), any(), any());
  }

  @Test
  void generateIsIdempotentWhenLinkExists() {
    MdmCollectLink existing = link();
    when(linkRepository.findBySourceId(7L)).thenReturn(Optional.of(existing));
    assertSame(existing, service.generateLandingTask(7L, 11L, "tester"));
    verify(definitionService, never()).saveGuide(any());
    verify(service, never()).preCreateLandingTable(any(), any(), any());
  }

  @Test
  void generateBuildsGuideSingleFullLandingJobAndPersistsLink() {
    when(linkRepository.findBySourceId(7L)).thenReturn(Optional.empty());
    when(definitionService.page(any())).thenReturn(new PagingData<>(List.of(), null));
    when(definitionService.saveGuide(any())).thenReturn(555L);
    when(linkRepository.insert(any(), any())).thenAnswer(i -> i.getArgument(0));

    service.generateLandingTask(7L, 11L, "tester");

    ArgumentCaptor<OfflineJobDefinitionDTO> captor =
        ArgumentCaptor.forClass(OfflineJobDefinitionDTO.class);
    verify(definitionService).saveGuide(captor.capture());
    OfflineJobDefinitionDTO dto = captor.getValue();
    assertEquals("GUIDE_SINGLE", dto.getBasic().getMode());
    assertEquals("mdm_collect_customer_7", dto.getBasic().getJobName());
    assertEquals("9", dto.getSource().getDataSourceId());
    assertEquals("crm_db.crm_customer", dto.getSource().getConfig().path("table").asText());
    assertEquals("11", dto.getSink().getDataSourceId());
    JsonNode sinkConfig = dto.getSink().getConfig();
    assertEquals("yak_security.mdm_landing_customer_7", sinkConfig.path("table").asText());
    assertFalse(sinkConfig.path("autoCreateTable").asBoolean(true));
    assertEquals("overwrite", sinkConfig.path("writeMode").asText());
    assertEquals(
        List.of("cust_id", "cust_name"),
        dto.getMapping().getColumns().stream()
            .map(io.yak.ops.common.bean.dto.sync.offline.OfflineJobColumnMappingDTO::getSource)
            .toList());
    assertEquals(
        List.of("cust_id", "cust_name"),
        dto.getMapping().getColumns().stream()
            .map(io.yak.ops.common.bean.dto.sync.offline.OfflineJobColumnMappingDTO::getTarget)
            .toList());

    ArgumentCaptor<MdmCollectLink> linkCaptor = ArgumentCaptor.forClass(MdmCollectLink.class);
    verify(linkRepository).insert(linkCaptor.capture(), any());
    assertEquals("mdm_landing_customer_7", linkCaptor.getValue().landingTable());
    assertEquals(555L, linkCaptor.getValue().jobDefinitionId());
    assertEquals(7L, linkCaptor.getValue().sourceId());
  }

  @Test
  void generateReusesOrphanedDefinitionByName() {
    when(linkRepository.findBySourceId(7L)).thenReturn(Optional.empty());
    OfflineJobDefinitionVO orphan = new OfflineJobDefinitionVO();
    orphan.setId(999L);
    orphan.setJobName("mdm_collect_customer_7");
    when(definitionService.page(any())).thenReturn(new PagingData<>(List.of(orphan), null));
    when(definitionService.saveGuide(any())).thenReturn(999L);
    when(linkRepository.insert(any(), any())).thenAnswer(i -> i.getArgument(0));

    service.generateLandingTask(7L, 11L, "tester");

    ArgumentCaptor<OfflineJobDefinitionDTO> captor =
        ArgumentCaptor.forClass(OfflineJobDefinitionDTO.class);
    verify(definitionService).saveGuide(captor.capture());
    assertEquals(999L, captor.getValue().getId());
  }

  @Test
  void generateRejectsMissingSinkDatasource() {
    when(linkRepository.findBySourceId(7L)).thenReturn(Optional.empty());
    MdmException exception =
        assertThrows(MdmException.class, () -> service.generateLandingTask(7L, null, "tester"));
    assertEquals(MdmErrorCode.LANDING_TASK_CREATE_FAILED, exception.getErrorCode());
  }

  @Test
  void generateRejectsUnsafeSourceColumnNames() {
    when(linkRepository.findBySourceId(7L)).thenReturn(Optional.empty());
    when(catalogReader.listColumns(anyLong(), any(), any(), any()))
        .thenReturn(
            List.of(
                new CatalogColumn(
                    "cust id;drop", "VARCHAR", Types.VARCHAR, 64, 0, true, 1, false, null)));
    MdmException exception =
        assertThrows(MdmException.class, () -> service.generateLandingTask(7L, 11L, "tester"));
    assertEquals(MdmErrorCode.LANDING_TASK_CREATE_FAILED, exception.getErrorCode());
    verify(service, never()).preCreateLandingTable(any(), any(), any());
  }

  @Test
  void runBringsDefinitionOnlineThenExecutes() {
    when(linkRepository.findBySourceId(7L)).thenReturn(Optional.of(link()));
    OfflineJobDefinitionVO definition = new OfflineJobDefinitionVO();
    definition.setReleaseState("OFFLINE");
    when(definitionService.get(555L)).thenReturn(definition);
    OfflineJobExecutionVO execution = new OfflineJobExecutionVO();
    execution.setId(123L);
    when(executionService.execute(555L)).thenReturn(execution);

    assertSame(execution, service.run(7L));
    verify(definitionService).online(555L);
    verify(executionService).execute(555L);
  }

  @Test
  void runSkipsOnlineWhenAlreadyOnline() {
    when(linkRepository.findBySourceId(7L)).thenReturn(Optional.of(link()));
    OfflineJobDefinitionVO definition = new OfflineJobDefinitionVO();
    definition.setReleaseState("ONLINE");
    when(definitionService.get(555L)).thenReturn(definition);
    when(executionService.execute(555L)).thenReturn(new OfflineJobExecutionVO());

    service.run(7L);
    verify(definitionService, never()).online(any());
  }

  @Test
  void runFailsWhenLinkMissing() {
    when(linkRepository.findBySourceId(8L)).thenReturn(Optional.empty());
    MdmException exception = assertThrows(MdmException.class, () -> service.run(8L));
    assertEquals(MdmErrorCode.COLLECT_LINK_NOT_FOUND, exception.getErrorCode());
  }

  @Test
  void runSurfacesEngineConnectionFailureMessage() {
    when(linkRepository.findBySourceId(7L)).thenReturn(Optional.of(link()));
    OfflineJobDefinitionVO definition = new OfflineJobDefinitionVO();
    definition.setReleaseState("ONLINE");
    when(definitionService.get(555L)).thenReturn(definition);
    when(executionService.execute(555L))
        .thenThrow(new RuntimeException("无法连接 Link-Up Server：http://127.0.0.1:18080"));
    MdmException exception = assertThrows(MdmException.class, () -> service.run(7L));
    assertEquals(MdmErrorCode.LANDING_TASK_CREATE_FAILED, exception.getErrorCode());
    assertTrue(exception.getUserMessage().contains("Link-Up Server"));
  }

  @Test
  void statusExposesLastSucceededRun() {
    when(linkRepository.listByEntity(1L)).thenReturn(List.of(link()));
    OfflineJobDefinitionVO definition = new OfflineJobDefinitionVO();
    definition.setJobName("mdm_collect_customer_7");
    definition.setReleaseState("ONLINE");
    definition.setLastJobStatus("SUCCEEDED");
    when(definitionService.get(555L)).thenReturn(definition);
    OfflineJobExecutionVO last = new OfflineJobExecutionVO();
    last.setEndTime("2026-09-19 17:00:00");
    last.setSinkCommittedRecordCount(3060);
    when(executionService.page(any())).thenReturn(new PagingData<>(List.of(last), null));

    List<MdmCollectService.CollectStatus> statuses = service.listStatus(1L);
    assertEquals(1, statuses.size());
    MdmCollectService.CollectStatus status = statuses.get(0);
    assertEquals("mdm_landing_customer_7", status.landingTable());
    assertEquals("crm_customer", status.sourceTable());
    assertEquals("SUCCEEDED", status.lastJobStatus());
    assertEquals("2026-09-19 17:00:00", status.lastSuccessTime());
    assertEquals(3060L, status.lastSuccessRows());
  }

  @Test
  void statusToleratesSyncQueryFailure() {
    when(linkRepository.listByEntity(1L)).thenReturn(List.of(link()));
    when(definitionService.get(555L)).thenThrow(new IllegalStateException("boom"));
    when(executionService.page(any())).thenThrow(new IllegalStateException("boom"));

    List<MdmCollectService.CollectStatus> statuses = service.listStatus(1L);
    assertEquals(1, statuses.size());
    assertTrue(statuses.get(0).lastSuccessTime() == null);
    assertTrue(statuses.get(0).lastJobStatus() == null);
  }

  private static MdmCollectLink link() {
    return new MdmCollectLink(
        1L, 1L, 7L, 9L, "mdm_landing_customer_7", 555L, "tester", null, null);
  }

  private static List<CatalogColumn> customerColumns() {
    return List.of(
        new CatalogColumn("cust_id", "VARCHAR", Types.VARCHAR, 32, 0, false, 1, true, null),
        new CatalogColumn("cust_name", "VARCHAR", Types.VARCHAR, 128, 0, true, 2, false, null));
  }
}
