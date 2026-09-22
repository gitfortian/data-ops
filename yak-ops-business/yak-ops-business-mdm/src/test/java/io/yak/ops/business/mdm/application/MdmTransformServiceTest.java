package io.yak.ops.business.mdm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.mdm.domain.attribute.MdmAttribute;
import io.yak.ops.business.mdm.domain.attribute.MdmAttributeType;
import io.yak.ops.business.mdm.domain.clean.CompleteExpr;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRule;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRuleType;
import io.yak.ops.business.mdm.domain.clean.StandardizeExpr;
import io.yak.ops.business.mdm.domain.record.MdmRecord;
import io.yak.ops.business.mdm.domain.record.MdmRecordStatus;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmAttributeRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmCleanRuleRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmDedupIgnoreRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmMergeLogRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmRecordRepository;
import io.yak.ops.business.mdm.notification.MdmNotifier;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 主数据标准化/补全服务单元测试(ticket 57)。 */
class MdmTransformServiceTest {

  private MdmCleanRuleRepository ruleRepository;
  private MdmMergeLogRepository mergeLogRepository;
  private MdmRecordRepository recordRepository;
  private MdmEntityService entityService;
  private MdmAttributeRepository attributeRepository;
  private BusinessAuditService auditService;
  private MdmCleanService service;

  @BeforeEach
  void setUp() {
    ruleRepository = Mockito.mock(MdmCleanRuleRepository.class);
    mergeLogRepository = Mockito.mock(MdmMergeLogRepository.class);
    recordRepository = Mockito.mock(MdmRecordRepository.class);
    entityService = Mockito.mock(MdmEntityService.class);
    attributeRepository = Mockito.mock(MdmAttributeRepository.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    AuditOperationHandle audit = Mockito.mock(AuditOperationHandle.class);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    lenient().when(entityService.get(any())).thenReturn(null);
    lenient()
        .when(attributeRepository.listByEntity(1L))
        .thenReturn(
            List.of(
                attribute("gender", "性别"),
                attribute("grade", "等级"),
                attribute("name", "姓名")));
    service =
        new MdmCleanService(
            ruleRepository, mergeLogRepository,
            Mockito.mock(MdmDedupIgnoreRepository.class), recordRepository, entityService,
            attributeRepository, auditService, Mockito.mock(MdmNotifier.class));
  }

  private static MdmAttribute attribute(String code, String name) {
    return new MdmAttribute(
        1L, 1L, code, name, MdmAttributeType.ATTR, "STRING", null, null, null, null,
        false, null, 0, "ENABLED", "tester", LocalDateTime.now(), LocalDateTime.now());
  }

  private static MdmRecord record(Long id, String attributes) {
    return new MdmRecord(
        id, 1L, "M" + id, attributes, "{}", MdmRecordStatus.ACTIVE, 1,
        LocalDateTime.now(), LocalDateTime.now());
  }

  // ==== 领域表达式测试 ====

  @Test
  void standardizeExprAppliesMapping() {
    StandardizeExpr expr =
        new StandardizeExpr(Map.of("gender", Map.of("M", "1", "F", "2")));
    Map<String, Object> result =
        expr.apply(new LinkedHashMap<>(Map.of("gender", "M", "name", "张三")));
    assertNotNull(result);
    assertEquals("1", result.get("gender"));
    assertEquals("张三", result.get("name"));
  }

  @Test
  void standardizeExprReturnsNullWhenNoChange() {
    StandardizeExpr expr =
        new StandardizeExpr(Map.of("gender", Map.of("M", "1")));
    assertNull(expr.apply(new LinkedHashMap<>(Map.of("gender", "1"))));
  }

  @Test
  void standardizeExprWouldChange() {
    StandardizeExpr expr =
        new StandardizeExpr(Map.of("gender", Map.of("M", "1", "F", "2")));
    assertTrue(expr.wouldChange(Map.of("gender", "M")));
    assertFalse(expr.wouldChange(Map.of("gender", "1")));
    assertFalse(expr.wouldChange(Map.of("name", "张三")));
  }

  @Test
  void completeExprFillsDefaults() {
    CompleteExpr expr = new CompleteExpr(Map.of("grade", "普通"));
    Map<String, Object> result =
        expr.apply(new LinkedHashMap<>(Map.of("name", "张三")));
    assertNotNull(result);
    assertEquals("普通", result.get("grade"));
    assertEquals("张三", result.get("name"));
  }

  @Test
  void completeExprSkipsExistingValues() {
    CompleteExpr expr = new CompleteExpr(Map.of("grade", "普通"));
    assertNull(expr.apply(new LinkedHashMap<>(Map.of("grade", "VIP"))));
  }

  @Test
  void completeExprFillsEmptyValues() {
    CompleteExpr expr = new CompleteExpr(Map.of("grade", "普通"));
    Map<String, Object> result =
        expr.apply(new LinkedHashMap<>(Map.of("grade", "")));
    assertNotNull(result);
    assertEquals("普通", result.get("grade"));
  }

  @Test
  void completeExprWouldChange() {
    CompleteExpr expr = new CompleteExpr(Map.of("grade", "普通"));
    assertTrue(expr.wouldChange(Map.of("name", "张三")));
    assertFalse(expr.wouldChange(Map.of("grade", "VIP")));
  }

  // ==== 服务层测试 ====

  @Test
  void createStandardizeRuleValidatesAttributes() {
    when(ruleRepository.existsByName(1L, "性别标准化", null)).thenReturn(false);
    when(ruleRepository.insert(any(MdmCleanRule.class), any()))
        .thenAnswer(
            invocation -> {
              MdmCleanRule r = invocation.getArgument(0);
              return r.withPersisted(10L, "tester", LocalDateTime.now());
            });

    MdmCleanRule rule =
        service.createGeneric(
            1L, MdmCleanRuleType.STANDARDIZE, "性别标准化",
            "{\"fields\":{\"gender\":{\"M\":\"1\",\"F\":\"2\"}}}", 0, "tester");

    assertEquals(10L, rule.id());
    assertEquals(MdmCleanRuleType.STANDARDIZE, rule.ruleType());
    verify(ruleRepository).insert(any(MdmCleanRule.class), any());
  }

  @Test
  void createStandardizeRuleRejectsUnknownAttribute() {
    MdmException exception =
        assertThrows(
            MdmException.class,
            () ->
                service.createGeneric(
                    1L, MdmCleanRuleType.STANDARDIZE, "未知字段",
                    "{\"fields\":{\"unknown_attr\":{\"a\":\"b\"}}}", 0, "tester"));
    assertEquals(MdmErrorCode.INVALID_CLEAN_RULE, exception.getErrorCode());
    verify(ruleRepository, never()).insert(any(), any());
  }

  @Test
  void createStandardizeRuleRejectsEmptyMapping() {
    MdmException exception =
        assertThrows(
            MdmException.class,
            () ->
                service.createGeneric(
                    1L, MdmCleanRuleType.STANDARDIZE, "空映射",
                    "{\"fields\":{\"gender\":{}}}", 0, "tester"));
    assertEquals(MdmErrorCode.INVALID_CLEAN_RULE, exception.getErrorCode());
  }

  @Test
  void createCompleteRuleValidatesDefaults() {
    when(ruleRepository.existsByName(1L, "等级补全", null)).thenReturn(false);
    when(ruleRepository.insert(any(MdmCleanRule.class), any()))
        .thenAnswer(
            invocation -> {
              MdmCleanRule r = invocation.getArgument(0);
              return r.withPersisted(11L, "tester", LocalDateTime.now());
            });

    MdmCleanRule rule =
        service.createGeneric(
            1L, MdmCleanRuleType.COMPLETE, "等级补全",
            "{\"defaults\":{\"grade\":\"普通\"}}", 0, "tester");

    assertEquals(11L, rule.id());
    assertEquals(MdmCleanRuleType.COMPLETE, rule.ruleType());
  }

  @Test
  void createCompleteRuleRejectsUnknownAttribute() {
    MdmException exception =
        assertThrows(
            MdmException.class,
            () ->
                service.createGeneric(
                    1L, MdmCleanRuleType.COMPLETE, "未知字段",
                    "{\"defaults\":{\"unknown_attr\":\"val\"}}", 0, "tester"));
    assertEquals(MdmErrorCode.INVALID_CLEAN_RULE, exception.getErrorCode());
  }

  @Test
  void createCompleteRuleRejectsEmptyDefaults() {
    MdmException exception =
        assertThrows(
            MdmException.class,
            () ->
                service.createGeneric(
                    1L, MdmCleanRuleType.COMPLETE, "空默认值",
                    "{\"defaults\":{}}", 0, "tester"));
    assertEquals(MdmErrorCode.INVALID_CLEAN_RULE, exception.getErrorCode());
  }

  @Test
  void previewTransformCountsAffectedRecords() {
    MdmCleanRule rule =
        new MdmCleanRule(
            10L, 1L, MdmCleanRuleType.STANDARDIZE, "性别标准化",
            "{\"fields\":{\"gender\":{\"M\":\"1\",\"F\":\"2\"}}}",
            true, 0, "tester", LocalDateTime.now(), LocalDateTime.now());
    when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));
    when(recordRepository.listActiveByEntity(1L))
        .thenReturn(
            List.of(
                record(1L, "{\"gender\":\"M\",\"name\":\"张三\"}"),
                record(2L, "{\"gender\":\"1\",\"name\":\"李四\"}"),
                record(3L, "{\"gender\":\"F\",\"name\":\"王五\"}")));

    MdmCleanService.TransformPreview preview = service.previewTransform(1L, 10L);

    assertEquals(2, preview.affectedCount());
    assertFalse(preview.truncated());
    assertEquals(2, preview.changes().size());
    assertEquals("M", preview.changes().get(0).before().get("gender"));
    assertEquals("1", preview.changes().get(0).after().get("gender"));
  }

  /** 明细有界、总数全量:实体再大也不该把预览响应撑成线性膨胀。 */
  @Test
  void previewTransformCapsChangeSamplesButCountsAll() {
    MdmCleanRule rule =
        new MdmCleanRule(
            10L, 1L, MdmCleanRuleType.STANDARDIZE, "性别标准化",
            "{\"fields\":{\"gender\":{\"M\":\"1\"}}}",
            true, 0, "tester", LocalDateTime.now(), LocalDateTime.now());
    when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));
    List<MdmRecord> records = new java.util.ArrayList<>();
    for (int i = 0; i < MdmCleanService.MAX_PREVIEW_ITEMS + 20; i++) {
      records.add(record((long) i + 1, "{\"gender\":\"M\"}"));
    }
    when(recordRepository.listActiveByEntity(1L)).thenReturn(records);

    MdmCleanService.TransformPreview preview = service.previewTransform(1L, 10L);

    assertEquals(70, preview.affectedCount());
    assertTrue(preview.truncated());
    assertEquals(MdmCleanService.MAX_PREVIEW_ITEMS, preview.changes().size());
  }

  /** 停用的规则不该还能被预览——「启用」开关必须在执行侧生效。 */
  @Test
  void previewTransformRejectsDisabledRule() {
    MdmCleanRule rule =
        new MdmCleanRule(
            10L, 1L, MdmCleanRuleType.STANDARDIZE, "性别标准化",
            "{\"fields\":{\"gender\":{\"M\":\"1\"}}}",
            false, 0, "tester", LocalDateTime.now(), LocalDateTime.now());
    when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));

    MdmException exception =
        assertThrows(MdmException.class, () -> service.previewTransform(1L, 10L));
    assertEquals(MdmErrorCode.INVALID_CLEAN_RULE, exception.getErrorCode());
    verify(recordRepository, never()).listActiveByEntity(anyLong());
  }

  @Test
  void applyTransformUpdatesAffectedRecords() {
    MdmCleanRule rule =
        new MdmCleanRule(
            10L, 1L, MdmCleanRuleType.COMPLETE, "等级补全",
            "{\"defaults\":{\"grade\":\"普通\"}}",
            true, 0, "tester", LocalDateTime.now(), LocalDateTime.now());
    when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));
    when(recordRepository.listActiveByEntity(1L))
        .thenReturn(
            List.of(
                record(1L, "{\"name\":\"张三\"}"),
                record(2L, "{\"name\":\"李四\",\"grade\":\"VIP\"}"),
                record(3L, "{\"name\":\"王五\"}")));
    when(recordRepository.update(any())).thenReturn(true);

    int count = service.applyTransform(1L, 10L, "tester");

    assertEquals(2, count);
    verify(recordRepository, Mockito.times(2)).update(any());
  }

  @Test
  void applyTransformRejectsDedupRule() {
    MdmCleanRule rule =
        new MdmCleanRule(
            10L, 1L, MdmCleanRuleType.DEDUP, "去重规则",
            "{\"fields\":[{\"attrCode\":\"gender\",\"matchType\":\"EXACT\"}],\"condition\":\"AND\"}",
            true, 0, "tester", LocalDateTime.now(), LocalDateTime.now());
    when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));

    MdmException exception =
        assertThrows(
            MdmException.class, () -> service.applyTransform(1L, 10L, "tester"));
    assertEquals(MdmErrorCode.INVALID_CLEAN_RULE, exception.getErrorCode());
    verify(recordRepository, never()).update(any());
  }

  @Test
  void previewTransformRejectsWrongEntity() {
    MdmCleanRule rule =
        new MdmCleanRule(
            10L, 99L, MdmCleanRuleType.STANDARDIZE, "其他实体",
            "{\"fields\":{\"gender\":{\"M\":\"1\"}}}",
            true, 0, "tester", LocalDateTime.now(), LocalDateTime.now());
    when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));

    MdmException exception =
        assertThrows(
            MdmException.class, () -> service.previewTransform(1L, 10L));
    assertEquals(MdmErrorCode.INVALID_CLEAN_RULE, exception.getErrorCode());
  }
}
