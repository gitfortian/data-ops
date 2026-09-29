package io.yak.ops.business.mdm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.common.PageData;
import io.yak.ops.business.mdm.domain.attribute.MdmAttribute;
import io.yak.ops.business.mdm.domain.attribute.MdmAttributeType;
import io.yak.ops.business.mdm.domain.collect.MdmCollectLink;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.entity.MdmEntityStatus;
import io.yak.ops.business.mdm.domain.record.MdmRecord;
import io.yak.ops.business.mdm.domain.record.MdmRecordStatus;
import io.yak.ops.business.mdm.domain.source.MdmSource;
import io.yak.ops.business.mdm.domain.source.MdmSourceRole;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmAttributeRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmCollectLinkRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmRecordRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmSourceRepository;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 记录读侧单元测试(R2):落地表加工口径、未落地阻断、属性白名单检索(P0-1.4)。 */
class MdmRecordServiceTest {

  private MdmRecordRepository recordRepository;
  private MdmEntityService entityService;
  private MdmAttributeRepository attributeRepository;
  private MdmSourceRepository sourceRepository;
  private MdmCollectLinkRepository collectLinkRepository;
  private MdmCollectService collectService;
  private MdmRecordService service;

  @BeforeEach
  void setUp() {
    recordRepository = Mockito.mock(MdmRecordRepository.class);
    entityService = Mockito.mock(MdmEntityService.class);
    attributeRepository = Mockito.mock(MdmAttributeRepository.class);
    sourceRepository = Mockito.mock(MdmSourceRepository.class);
    collectLinkRepository = Mockito.mock(MdmCollectLinkRepository.class);
    collectService = Mockito.mock(MdmCollectService.class);
    CurrentProject currentProject = Mockito.mock(CurrentProject.class);
    lenient().when(currentProject.requireProjectId()).thenReturn(1L);
    lenient().when(entityService.get(1L)).thenReturn(entity());
    lenient().when(collectService.businessDatabase()).thenReturn("yak_security");
    service =
        new MdmRecordService(
            recordRepository,
            entityService,
            attributeRepository,
            sourceRepository,
            collectLinkRepository,
            collectService,
            currentProject);
  }

  @Test
  void generateReadsPlatformLandingTable() {
    stubBindings();
    String sql = service.generateMasterSql(1L);
    assertTrue(sql.contains("FROM `yak_security`.`mdm_landing_customer_1`"));
    assertTrue(sql.contains("INSERT INTO `yak_security`.`yak_mdm_record`"));
    // 映射列取源列名(落地表按源列名建列)
    assertTrue(sql.contains("`cust_mobile`"));
  }

  @Test
  void generateConcatenatesOneStatementPerLandingTable() {
    MdmSource second =
        new MdmSource(
            2L, 1L, 10L, "trade", null, "cust", Map.of(), MdmSourceRole.AUXILIARY,
            MdmSource.STATUS_ENABLED, 1, "root", null, null);
    when(sourceRepository.listByEntity(1L)).thenReturn(List.of(source(), second));
    when(collectLinkRepository.listByEntity(1L))
        .thenReturn(List.of(link(1L, "mdm_landing_customer_1"), link(2L, "mdm_landing_customer_2")));
    stubAttributes();
    String sql = service.generateMasterSql(1L);
    assertTrue(sql.contains("FROM `yak_security`.`mdm_landing_customer_1`"));
    assertTrue(sql.contains("FROM `yak_security`.`mdm_landing_customer_2`"));
    // 一段落地表一条 INSERT(注释里也有分号,不能按 ';' 计数)
    assertEquals(2, sql.split("INSERT INTO", -1).length - 1);
    assertEquals(2, sql.split("ON DUPLICATE KEY UPDATE", -1).length - 1);
  }

  @Test
  void generateBlocksWhenSourceNotLanded() {
    stubAttributes();
    when(sourceRepository.listByEntity(1L)).thenReturn(List.of(source()));
    when(collectLinkRepository.listByEntity(1L)).thenReturn(List.of());
    MdmException exception =
        assertThrows(MdmException.class, () -> service.generateMasterSql(1L));
    assertEquals(MdmErrorCode.SOURCE_NOT_LANDED, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("crm_customer"));
  }

  @Test
  void generateStillRequiresPkAndAttributes() {
    when(attributeRepository.listByEntity(1L)).thenReturn(List.of(attribute("cust_name", false)));
    assertThrows(MdmException.class, () -> service.generateMasterSql(1L));
  }

  @Test
  @SuppressWarnings("unchecked")
  void pageSearchesMasterIdAndWhitelistedAttributeCodes() {
    stubAttributes();
    when(recordRepository.page(any(), anyInt(), anyInt(), any(), any(), anyList()))
        .thenReturn(new PageData<>(List.of(), 0L, 0L, 1L, 10L));

    service.page(1L, 1, 10, "张三", "");

    ArgumentCaptor<List<String>> codes = ArgumentCaptor.forClass(List.class);
    verify(recordRepository)
        .page(eq(1L), eq(1), eq(10), eq("张三"), isNull(), codes.capture());
    // 非法属性编码(含空格/符号)被白名单过滤
    assertEquals(List.of("cust_id", "cust_name"), codes.getValue());
  }

  private void stubBindings() {
    stubAttributes();
    when(sourceRepository.listByEntity(1L)).thenReturn(List.of(source()));
    when(collectLinkRepository.listByEntity(1L))
        .thenReturn(List.of(link(1L, "mdm_landing_customer_1")));
  }

  private void stubAttributes() {
    when(attributeRepository.listByEntity(1L))
        .thenReturn(List.of(attribute("cust_id", true), attribute("cust_name", false)));
  }

  private MdmEntity entity() {
    return new MdmEntity(
        1L, "customer", "客户", MdmEntityStatus.DRAFT, "root", null, "root", null, null);
  }

  private MdmAttribute attribute(String code, boolean pk) {
    return new MdmAttribute(
        null, 1L, code, code, pk ? MdmAttributeType.PK : MdmAttributeType.ATTR,
        "VARCHAR", null, null, null, null, false, null, 0,
        MdmAttribute.STATUS_ENABLED, "root", null, null);
  }

  private MdmSource source() {
    return new MdmSource(
        1L, 1L, 9L, "crm_db", null, "crm_customer", Map.of("cust_name", "cust_mobile"),
        MdmSourceRole.MAIN, MdmSource.STATUS_ENABLED, 0, "root", null, null);
  }

  private MdmCollectLink link(Long sourceId, String landingTable) {
    return new MdmCollectLink(
        null, 1L, sourceId, 9L, landingTable, 123L, "root", null, null);
  }
}
