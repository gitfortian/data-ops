package io.yak.ops.business.mdm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.common.PageData;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.mdm.domain.attribute.MdmAttribute;
import io.yak.ops.business.mdm.domain.attribute.MdmAttributeType;
import io.yak.ops.business.mdm.domain.collect.MdmCollectLink;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.entity.MdmEntityStatus;
import io.yak.ops.business.mdm.domain.source.MdmSource;
import io.yak.ops.business.mdm.domain.source.MdmSourceRole;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmAttributeRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmCollectLinkRepository;
import io.yak.ops.business.quality.asset.QualityTableAssetCommand;
import io.yak.ops.business.quality.asset.QualityTableAssetManager;
import io.yak.ops.business.quality.asset.QualityTableAssetReader;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.Template;
import io.yak.ops.business.quality.execution.QualityExecutionManager;
import io.yak.ops.business.quality.execution.QualityExecutionReceipt;
import io.yak.ops.business.quality.monitor.QualityMonitorCommand;
import io.yak.ops.business.quality.monitor.QualityMonitorManager;
import io.yak.ops.business.quality.monitor.QualityMonitorReader;
import io.yak.ops.business.quality.template.QualityTemplateReader;
import io.yak.ops.business.sync.offline.definition.OfflineJobDefinitionService;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobDefinitionVO;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.ExecutionStatus;
import io.yak.ops.common.enums.quality.QualityEnums.RuleScope;
import io.yak.ops.common.enums.quality.QualityEnums.RuleType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/** 质量复用单元测试(R3):资产注册、监控模板规则、幂等复用、执行受理、降级路径。 */
class MdmQualityServiceTest {

  private MdmSourceService sourceService;
  private MdmCollectLinkRepository linkRepository;
  private MdmCollectService collectService;
  private QualityTableAssetManager assets;
  private QualityTableAssetReader assetReader;
  private QualityMonitorManager monitors;
  private QualityMonitorReader monitorReader;
  private QualityExecutionManager executions;
  private MdmQualityService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    MdmEntityService entityService = mock(MdmEntityService.class);
    sourceService = mock(MdmSourceService.class);
    MdmAttributeRepository attributeRepository = mock(MdmAttributeRepository.class);
    linkRepository = mock(MdmCollectLinkRepository.class);
    collectService = mock(MdmCollectService.class);
    DataSourceReader dataSourceReader = mock(DataSourceReader.class);
    OfflineJobDefinitionService definitions = mock(OfflineJobDefinitionService.class);
    assets = mock(QualityTableAssetManager.class);
    assetReader = mock(QualityTableAssetReader.class);
    monitors = mock(QualityMonitorManager.class);
    monitorReader = mock(QualityMonitorReader.class);
    executions = mock(QualityExecutionManager.class);
    QualityTemplateReader templateReader = mock(QualityTemplateReader.class);

    lenient()
        .when(entityService.get(1L))
        .thenReturn(
            new MdmEntity(
                1L, "customer", "客户", MdmEntityStatus.ACTIVE, "root", null, "root",
                null, null));
    lenient()
        .when(attributeRepository.listByEntity(1L))
        .thenReturn(
            List.of(
                attribute("cust_id", MdmAttributeType.PK, false),
                attribute("cust_name", MdmAttributeType.ATTR, true)));
    lenient().when(sourceService.get(1L)).thenReturn(source());
    lenient().when(linkRepository.listByEntity(1L)).thenReturn(List.of(link()));
    lenient().when(collectService.businessDatabase()).thenReturn("yak_security");
    OfflineJobDefinitionVO definition = new OfflineJobDefinitionVO();
    definition.setSinkDatasourceId(3L);
    lenient().when(definitions.get(123L)).thenReturn(definition);
    DataSourceDefinition sinkDefinition = dataSourceDefinition("客户库(平台实例)");
    lenient()
        .when(dataSourceReader.require(3L))
        .thenReturn(sinkDefinition);
    lenient()
        .when(templateReader.list(any()))
        .thenReturn(
            new QualityTemplateReader.TemplateList(
                List.of(template(2L, "COLUMN_NOT_NULL"), template(3L, "COLUMN_UNIQUE")),
                new QualityTemplateReader.Summary(2L, Map.of())));
    lenient()
        .when(monitorReader.page(any()))
        .thenReturn(new PageData<>(List.of(), 0L, 0L, 1L, 50L));
    lenient()
        .when(assetReader.page(any()))
        .thenReturn(new PageData<>(List.of(), 0L, 0L, 1L, 50L));
    lenient()
        .when(monitors.create(any()))
        .thenReturn(monitor(9L, "yak_security", "mdm_landing_customer_1", CheckResult.NOT_RUN));
    lenient()
        .when(executions.run(eq(9L), anyString()))
        .thenReturn(
            new QualityExecutionReceipt("QM-1", ExecutionStatus.WAITING, CheckResult.RUNNING));

    service =
        new MdmQualityService(
            entityService,
            sourceService,
            attributeRepository,
            linkRepository,
            collectService,
            dataSourceReader,
            provider(definitions),
            provider(assets),
            provider(assetReader),
            provider(monitors),
            provider(monitorReader),
            provider(templateReader),
            provider(executions));
  }

  @Test
  void checkRegistersAssetWithSinkQuadruple() {
    service.check(1L, "root");
    ArgumentCaptor<QualityTableAssetCommand.Register> captor =
        ArgumentCaptor.forClass(QualityTableAssetCommand.Register.class);
    verify(assets).register(captor.capture(), eq("root"));
    QualityTableAssetCommand.Register register = captor.getValue();
    assertEquals(3L, register.dataSourceId());
    assertEquals("客户库(平台实例)", register.dataSourceName());
    assertEquals("yak_security", register.databaseName());
    assertEquals("mdm_landing_customer_1", register.tables().get(0).tableName());
  }

  @Test
  void checkCreatesMonitorWithTemplateRulesOnMappedColumns() {
    service.check(1L, "root");
    ArgumentCaptor<QualityMonitorCommand.Save> captor =
        ArgumentCaptor.forClass(QualityMonitorCommand.Save.class);
    verify(monitors).create(captor.capture());
    QualityMonitorCommand.Save save = captor.getValue();
    assertEquals("MDM落地体检-mdm_landing_customer_1", save.name());
    assertEquals(3L, save.dataSourceId());
    assertEquals("yak_security", save.databaseName());
    assertEquals("mdm_landing_customer_1", save.tableName());
    // PK 重复 + PK 必填 + 必填属性 cust_name→cust_mobile(映射列) = 3 条规则
    assertEquals(3, save.rules().size());
    QualityMonitorCommand.Rule unique = save.rules().get(0);
    assertEquals(3L, unique.templateId());
    assertEquals("cust_no", unique.columnName());
    QualityMonitorCommand.Rule required = save.rules().get(2);
    assertEquals(2L, required.templateId());
    assertEquals("cust_mobile", required.columnName());
  }

  @Test
  void checkReusesExistingMonitorAndRunsIt() {
    when(monitorReader.page(any()))
        .thenReturn(
            new PageData<>(
                List.of(monitor(42L, "yak_security", "mdm_landing_customer_1", CheckResult.PASSED)),
                1L,
                1L,
                1L,
                50L));
    when(executions.run(eq(42L), anyString()))
        .thenReturn(
            new QualityExecutionReceipt("QM-2", ExecutionStatus.SUCCESS, CheckResult.PASSED));
    List<MdmQualityService.LandingCheckReceipt> receipts = service.check(1L, "root");
    verify(monitors, never()).create(any());
    assertEquals("mdm_landing_customer_1", receipts.get(0).landingTable());
    assertEquals(42L, receipts.get(0).monitorId());
    assertEquals("QM-2", receipts.get(0).executionNo());
  }

  @Test
  void checkBlocksWhenNoLandingLink() {
    when(linkRepository.listByEntity(1L)).thenReturn(List.of());
    MdmException exception = assertThrows(MdmException.class, () -> service.check(1L, "root"));
    assertEquals(MdmErrorCode.SOURCE_NOT_LANDED, exception.getErrorCode());
  }

  @Test
  void checkFailsWhenSinkDatasourceUnresolvable() {
    when(linkRepository.listByEntity(1L)).thenReturn(List.of(new MdmCollectLink(
        1L, 1L, 1L, 9L, "mdm_landing_customer_1", 999L, "root", null, null)));
    MdmException exception = assertThrows(MdmException.class, () -> service.check(1L, "root"));
    assertEquals(MdmErrorCode.QUALITY_CHECK_FAILED, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("落地目标数据源"));
  }

  @Test
  void checkReportsDisabledQualityModule() {
    MdmQualityService disabled =
        new MdmQualityService(
            mock(MdmEntityService.class),
            sourceService,
            mock(MdmAttributeRepository.class),
            linkRepository,
            collectService,
            mock(DataSourceReader.class),
            emptyProvider(),
            emptyProvider(),
            emptyProvider(),
            emptyProvider(),
            emptyProvider(),
            emptyProvider(),
            emptyProvider());
    MdmException exception = assertThrows(MdmException.class, () -> disabled.check(1L, "root"));
    assertEquals(MdmErrorCode.QUALITY_MODULE_DISABLED, exception.getErrorCode());
  }

  @Test
  void statusExposesMonitorAndAssetState() {
    when(monitorReader.page(any()))
        .thenReturn(
            new PageData<>(
                List.of(monitor(42L, "yak_security", "mdm_landing_customer_1", CheckResult.PASSED)),
                1L,
                1L,
                1L,
                50L));
    List<MdmQualityService.LandingQualityStatus> statuses = service.status(1L);
    assertEquals(1, statuses.size());
    MdmQualityService.LandingQualityStatus status = statuses.get(0);
    assertEquals(42L, status.monitorId());
    assertEquals("PASSED", status.lastResult());
    assertEquals(3L, status.datasourceId());
    assertEquals("客户库(平台实例)", status.datasourceName());
    assertTrue(!status.assetRegistered());
  }

  private static <T> ObjectProvider<T> provider(T bean) {
    @SuppressWarnings("unchecked")
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    lenient().when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }

  private static <T> ObjectProvider<T> emptyProvider() {
    @SuppressWarnings("unchecked")
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    lenient().when(provider.getIfAvailable()).thenReturn(null);
    return provider;
  }

  private MdmAttribute attribute(String code, MdmAttributeType type, boolean required) {
    return new MdmAttribute(
        null, 1L, code, code, type, "VARCHAR", null, null, null, null, required, null, 0,
        MdmAttribute.STATUS_ENABLED, "root", null, null);
  }

  private MdmSource source() {
    return new MdmSource(
        1L, 1L, 9L, "crm_db", null, "crm_customer",
        Map.of("cust_id", "cust_no", "cust_name", "cust_mobile"),
        MdmSourceRole.MAIN, MdmSource.STATUS_ENABLED, 0, "root", null, null);
  }

  private MdmCollectLink link() {
    return new MdmCollectLink(
        1L, 1L, 1L, 9L, "mdm_landing_customer_1", 123L, "root", null, null);
  }

  private Monitor monitor(long id, String database, String table, CheckResult lastResult) {
    return new Monitor(
        id, "MDM落地体检-" + table, null, 3L, "客户库(平台实例)", database, null, table, null,
        "root", true, lastResult, "QM-9", null, null, null, 3, List.of());
  }

  private Template template(long id, String code) {
    return new Template(
        id, code, code, null, RuleType.valueOf(code), RuleScope.COLUMN, "唯一性", null,
        true, true, 0L, 10);
  }

  private DataSourceDefinition dataSourceDefinition(String name) {
    DataSourceDefinition definition = mock(DataSourceDefinition.class);
    lenient().when(definition.getName()).thenReturn(name);
    return definition;
  }
}
