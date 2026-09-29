package io.yak.ops.business.mdm.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider.ResolvedSource;
import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider.ResponseFieldContract;
import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider.SourceDescriptor;
import io.yak.ops.business.mdm.application.MdmCollectService;
import io.yak.ops.business.mdm.application.MdmEntityService;
import io.yak.ops.business.mdm.application.MdmProcessingTaskService;
import io.yak.ops.business.mdm.dao.mapper.MdmDistributionMapper;
import io.yak.ops.business.mdm.domain.attribute.MdmAttribute;
import io.yak.ops.business.mdm.domain.attribute.MdmAttributeType;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionMode;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionStatus;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.entity.MdmEntityStatus;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmAttributeRepository;
import io.yak.ops.common.bean.po.mdm.MdmDistributionPO;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 分发发布来源单元测试(R5):一次 resolve 必须给出「ONLINE 描述 + 读统一主数据表 ACTIVE 行
 * 的查询 + 按分发配置隔离的 sourceRef/path」;缺平台库数据源时明确不可发布。
 */
class MdmDataServiceSourceProviderTest {

  private MdmDistributionMapper mapper;
  private MdmProcessingTaskService processingTaskService;
  private MdmDataServiceSourceProvider provider;

  @BeforeEach
  void setUp() {
    mapper = mock(MdmDistributionMapper.class);
    MdmEntityService entityService = mock(MdmEntityService.class);
    MdmAttributeRepository attributeRepository = mock(MdmAttributeRepository.class);
    processingTaskService = mock(MdmProcessingTaskService.class);
    MdmCollectService collectService = mock(MdmCollectService.class);
    provider =
        new MdmDataServiceSourceProvider(
            mapper,
            entityService,
            attributeRepository,
            processingTaskService,
            collectService,
            mock(CurrentProject.class));
    lenient().when(collectService.businessDatabase()).thenReturn("yak_security");
    lenient()
        .when(entityService.get(1L))
        .thenReturn(
            new MdmEntity(
                1L, "customer", "客户", MdmEntityStatus.ACTIVE, "root", null, "root", null, null));
    lenient()
        .when(attributeRepository.listByEntity(1L))
        .thenReturn(
            List.of(
                attribute(1, "customer_id", MdmAttributeType.PK, MdmAttribute.STATUS_ENABLED),
                attribute(2, "customer_name", MdmAttributeType.ATTR, MdmAttribute.STATUS_ENABLED),
                attribute(3, "city", MdmAttributeType.ATTR, MdmAttribute.STATUS_ENABLED),
                attribute(4, "draft_attr", MdmAttributeType.ATTR, "DRAFT")));
  }

  private static MdmAttribute attribute(
      int sortOrder, String code, MdmAttributeType type, String status) {
    return new MdmAttribute(
        null, 1L, code, code, type, "VARCHAR", null, null, null, null, false, null, sortOrder,
        status, "root", null, null);
  }

  private static MdmDistributionPO config(
      Long id, MdmDistributionMode mode, MdmDistributionStatus status) {
    MdmDistributionPO po = new MdmDistributionPO();
    po.setId(id);
    po.setProjectId(1L);
    po.setEntityId(1L);
    po.setTargetSystem("CRM");
    po.setTargetName("CRM 系统");
    po.setDistributeMode(mode.name());
    po.setDistributeFreq("DAILY");
    po.setStatus(status.name());
    po.setUpdateTime(LocalDateTime.of(2026, 9, 20, 10, 0));
    return po;
  }

  @Test
  void activeApiConfigResolvesToOnlineSourceReadingUnifiedRecordTable() {
    when(mapper.selectById(10L))
        .thenReturn(config(10L, MdmDistributionMode.API, MdmDistributionStatus.ACTIVE));
    when(processingTaskService.platformSinkDatasourceId(1L)).thenReturn(Optional.of(88L));

    ResolvedSource resolved = provider.resolve("10");
    SourceDescriptor descriptor = resolved.descriptor();

    assertEquals("MDM_DISTRIBUTION", descriptor.sourceType());
    assertEquals("10", descriptor.sourceRef());
    assertEquals("ONLINE", descriptor.status());
    assertEquals(88L, descriptor.dataSourceId());
    assertEquals("主数据分发-customer-CRM 系统", descriptor.name());
    assertEquals("/mdm/customer/10", descriptor.defaultPath());
    assertEquals(Boolean.FALSE, descriptor.paginationEnabled());
    assertTrue(descriptor.sourceRevisionId() > 0);
    assertTrue(descriptor.sourceRevisionNo() > 0);

    String sql = resolved.sql();
    assertTrue(sql.contains("FROM `yak_security`.`yak_mdm_record` r"));
    assertTrue(sql.contains("r.project_id = 1 AND r.entity_id = 1 AND r.status = 'ACTIVE'"));
    assertTrue(sql.contains("AS `customer_id`"));
    assertTrue(sql.indexOf("`customer_name`") < sql.indexOf("`city`"));
    // 无命名参数:runtime 调用不带项目头,租户边界靠内联字面量 + API Key
    assertFalse(sql.contains("#{"));
    assertEquals(
        List.of("master_id", "version", "customer_id", "customer_name", "city", "update_time"),
        resolved.contract().responseFields().stream().map(ResponseFieldContract::name).toList());
    assertTrue(resolved.contract().parameters().isEmpty());
  }

  /** 未接入的通道/未生效的配置不能被判成可发布。 */
  @Test
  void inactiveOrNonApiConfigResolvesOffline() {
    when(mapper.selectById(12L))
        .thenReturn(config(12L, MdmDistributionMode.MESSAGE, MdmDistributionStatus.DRAFT));
    when(processingTaskService.platformSinkDatasourceId(1L)).thenReturn(Optional.of(88L));

    assertEquals("OFFLINE", provider.resolve("12").descriptor().status());
  }

  /** 未生成采集落地任务 → 平台库数据源未知,必须拒绝发布而非发布一个跑不通的 API。 */
  @Test
  void missingPlatformDatasourceBlocksPublishWithActionableError() {
    when(mapper.selectById(11L))
        .thenReturn(config(11L, MdmDistributionMode.API, MdmDistributionStatus.ACTIVE));
    when(processingTaskService.platformSinkDatasourceId(1L)).thenReturn(Optional.empty());

    MdmException exception = assertThrows(MdmException.class, () -> provider.resolve("11"));

    assertEquals(MdmErrorCode.DISTRIBUTE_FAILED, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("采集落地任务"));
  }

  @Test
  void revisionStaysStableForSameTemplateAndGarbageSourceRefIsRejected() {
    when(mapper.selectById(any()))
        .thenReturn(config(13L, MdmDistributionMode.API, MdmDistributionStatus.ACTIVE));
    when(processingTaskService.platformSinkDatasourceId(1L)).thenReturn(Optional.of(88L));

    assertEquals(
        provider.resolve("13").descriptor().sourceRevisionId(),
        provider.resolve("13").descriptor().sourceRevisionId());

    assertThrows(MdmException.class, () -> provider.resolve("not-a-number"));
    when(mapper.selectById(99L)).thenReturn(null);
    assertEquals(
        MdmErrorCode.DISTRIBUTE_FAILED,
        assertThrows(MdmException.class, () -> provider.resolve("99")).getErrorCode());
  }

  @Test
  void providerIdentityAndOwnershipAreStable() {
    assertEquals("MDM_DISTRIBUTION", provider.sourceType());
    assertTrue(provider.managesServiceDefinition());
  }
}
