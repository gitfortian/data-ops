package io.yak.ops.business.mdm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.common.PageData;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.mdm.domain.clean.MdmDedupKey;
import io.yak.ops.business.mdm.domain.attribute.MdmAttribute;
import io.yak.ops.business.mdm.domain.attribute.MdmAttributeType;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRule;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRuleExpr;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRuleType;
import io.yak.ops.business.mdm.domain.clean.MdmDedupIgnore;
import io.yak.ops.business.mdm.domain.clean.MdmMatchField;
import io.yak.ops.business.mdm.domain.clean.MdmMatchType;
import io.yak.ops.business.mdm.domain.clean.MdmMergeLog;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.entity.MdmEntityStatus;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 主数据清洗服务单元测试:规则校验、去重发现聚合、合并预览/执行。 */
class MdmCleanServiceTest {

  private MdmCleanRuleRepository ruleRepository;
  private MdmMergeLogRepository mergeLogRepository;
  private MdmDedupIgnoreRepository dedupIgnoreRepository;
  private MdmRecordRepository recordRepository;
  private MdmEntityService entityService;
  private MdmAttributeRepository attributeRepository;
  private BusinessAuditService auditService;
  private MdmNotifier notifier;
  private MdmCleanService service;

  @BeforeEach
  void setUp() {
    ruleRepository = Mockito.mock(MdmCleanRuleRepository.class);
    mergeLogRepository = Mockito.mock(MdmMergeLogRepository.class);
    dedupIgnoreRepository = Mockito.mock(MdmDedupIgnoreRepository.class);
    recordRepository = Mockito.mock(MdmRecordRepository.class);
    entityService = Mockito.mock(MdmEntityService.class);
    attributeRepository = Mockito.mock(MdmAttributeRepository.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    notifier = Mockito.mock(MdmNotifier.class);
    AuditOperationHandle audit = Mockito.mock(AuditOperationHandle.class);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    lenient().when(entityService.get(any())).thenReturn(null);
    lenient()
        .when(attributeRepository.listByEntity(1L))
        .thenReturn(
            List.of(
                attribute(1L, "mobile", "手机号"),
                attribute(2L, "name", "姓名")));
    service =
        new MdmCleanService(
            ruleRepository, mergeLogRepository, dedupIgnoreRepository, recordRepository,
            entityService, attributeRepository, auditService, notifier);
  }

  private static MdmAttribute attribute(Long id, String code, String name) {
    return new MdmAttribute(
        id, 1L, code, name, MdmAttributeType.ATTR, "STRING", null, null, null, null,
        false, null, 0, "ENABLED", "tester", LocalDateTime.now(), LocalDateTime.now());
  }

  private static MdmEntity entity() {
    return new MdmEntity(
        1L, "customer", "客户", MdmEntityStatus.ACTIVE, null, null, "tester",
        LocalDateTime.now(), LocalDateTime.now());
  }

  private static MdmCleanRuleExpr expr(MdmMatchType type) {
    return new MdmCleanRuleExpr(
        List.of(new MdmMatchField("mobile", type)), MdmCleanRuleExpr.CONDITION_AND);
  }

  private static MdmRecord record(
      Long id, String masterId, String attributes, String sourceIds) {
    return new MdmRecord(
        id, 1L, masterId, attributes, sourceIds, MdmRecordStatus.ACTIVE, 1,
        LocalDateTime.now(), LocalDateTime.now());
  }

  @Test
  void listRulesWithoutTypeReturnsAllTypes() {
    service.listRules(1L, null);
    service.listRules(1L, "  ");

    verify(ruleRepository, Mockito.times(2)).listByEntity(1L, null);
  }

  @Test
  void listRulesFiltersByTypeCaseInsensitively() {
    service.listRules(1L, " dedup ");

    verify(ruleRepository).listByEntity(1L, MdmCleanRuleType.DEDUP);
  }

  @Test
  void listRulesRejectsUnknownTypeInsteadOfReturningAll() {
    MdmException exception =
        assertThrows(MdmException.class, () -> service.listRules(1L, "MERGE"));

    assertEquals(MdmErrorCode.INVALID_CLEAN_RULE, exception.getErrorCode());
    verify(ruleRepository, never()).listByEntity(anyLong(), any());
  }

  @Test
  void createRejectsDuplicateRuleName() {
    lenient().when(ruleRepository.existsByName(1L, "手机号去重", null)).thenReturn(true);
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.create(1L, "手机号去重", expr(MdmMatchType.EXACT), 0, "tester"));
    assertEquals(MdmErrorCode.INVALID_CLEAN_RULE, exception.getErrorCode());
    verify(ruleRepository, never()).insert(any(), any());
  }

  @Test
  void createRejectsUnknownAttributeField() {
    MdmCleanRuleExpr bad =
        new MdmCleanRuleExpr(
            List.of(new MdmMatchField("id_card", MdmMatchType.EXACT)),
            MdmCleanRuleExpr.CONDITION_AND);
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.create(1L, "身份证去重", bad, 0, "tester"));
    assertEquals(MdmErrorCode.INVALID_CLEAN_RULE, exception.getErrorCode());
  }

  @Test
  void createRejectsEmptyFields() {
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.create(1L, "空规则", new MdmCleanRuleExpr(List.of(), "AND"), 0, "tester"));
    assertEquals(MdmErrorCode.INVALID_CLEAN_RULE, exception.getErrorCode());
  }

  @Test
  void findDuplicatesGroupsByCombinedKey() {
    MdmCleanRule rule =
        new MdmCleanRule(
            10L, 1L, MdmCleanRuleType.DEDUP, "手机号去重",
            "{\"fields\":[{\"attrCode\":\"mobile\",\"matchType\":\"EXACT\"}],\"condition\":\"AND\"}",
            true, 0, "tester", LocalDateTime.now(), LocalDateTime.now());
    when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));
    when(recordRepository.countDedupKeys(anyLong(), anyLong(), any(), any()))
        .thenReturn(List.of(new MdmDedupKey("13800138000", 3)));
    when(recordRepository.listByDedupKey(anyLong(), any(), any(), anyInt()))
        .thenReturn(
            List.of(
                record(1L, "C001", "{\"mobile\":\"13800138000\",\"name\":\"张三\"}",
                    "{\"9\":\"crm_1\"}")));

    PageData<MdmCleanService.DedupGroup> page =
        service.findDuplicates(1L, 10L, 1, 10);

    assertEquals(1, page.total());
    MdmCleanService.DedupGroup group = page.records().get(0);
    assertEquals(3, group.total());
    assertEquals(1, group.records().size());
    assertTrue(group.matchBasis().contains("手机号=13800138000(EXACT)"));
    assertEquals(1.0, group.confidence());
  }

  @Test
  void findDuplicatesRejectsRuleOfOtherEntity() {
    MdmCleanRule rule =
        new MdmCleanRule(
            10L, 99L, MdmCleanRuleType.DEDUP, "其他实体规则",
            "{\"fields\":[{\"attrCode\":\"mobile\",\"matchType\":\"EXACT\"}],\"condition\":\"AND\"}",
            true, 0, "tester", LocalDateTime.now(), LocalDateTime.now());
    when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));
    MdmException exception =
        assertThrows(
            MdmException.class, () -> service.findDuplicates(1L, 10L, 1, 10));
    assertEquals(MdmErrorCode.INVALID_CLEAN_RULE, exception.getErrorCode());
    verify(recordRepository, never()).countDedupKeys(anyLong(), anyLong(), any(), any());
  }

  @Test
  void findDuplicatesPassesRuleIdSoIgnoredKeysAreExcludedInSql() {
    MdmCleanRule rule = dedupRule(10L, 1L);
    when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));
    when(recordRepository.countDedupKeys(anyLong(), anyLong(), any(), any()))
        .thenReturn(List.of(new MdmDedupKey("13800138000", 2)));
    when(recordRepository.listByDedupKey(anyLong(), any(), any(), anyInt())).thenReturn(List.of());

    MdmCleanService.DedupGroup group =
        service.findDuplicates(1L, 10L, 1, 10).records().get(0);

    verify(recordRepository).countDedupKeys(anyLong(), Mockito.eq(10L), any(), any());
    assertEquals("13800138000", group.matchKey());
  }

  @Test
  void ignoreKeepsMatchKeyByteExactAndRegistersOnce() {
    when(ruleRepository.findById(10L)).thenReturn(Optional.of(dedupRule(10L, 1L)));
    when(dedupIgnoreRepository.findByKey(1L, 10L, " 13800138000 ")).thenReturn(Optional.empty());
    MdmDedupIgnore stored =
        new MdmDedupIgnore(7L, 1L, 10L, " 13800138000 ", null, "同一个人两个号", "tester",
            LocalDateTime.now());
    when(dedupIgnoreRepository.insert(any(), any())).thenReturn(stored);

    service.ignoreDedupGroup(1L, 10L, " 13800138000 ", "手机号=13800138000", "同一个人两个号", "tester");

    // 组键原样入库:一旦 trim/小写,发现 SQL 的二进制比对就永远命中不了这条忽略。
    ArgumentCaptor<MdmDedupIgnore> captor = ArgumentCaptor.forClass(MdmDedupIgnore.class);
    verify(dedupIgnoreRepository).insert(captor.capture(), Mockito.eq("tester"));
    assertEquals(" 13800138000 ", captor.getValue().matchKey());
  }

  @Test
  void ignoreIsIdempotentForTheSameKey() {
    MdmDedupIgnore existing =
        new MdmDedupIgnore(7L, 1L, 10L, "13800138000", null, null, "tester", LocalDateTime.now());
    when(ruleRepository.findById(10L)).thenReturn(Optional.of(dedupRule(10L, 1L)));
    when(dedupIgnoreRepository.findByKey(1L, 10L, "13800138000")).thenReturn(Optional.of(existing));

    MdmDedupIgnore result =
        service.ignoreDedupGroup(1L, 10L, "13800138000", null, null, "tester");

    assertEquals(existing, result);
    verify(dedupIgnoreRepository, never()).insert(any(), any());
  }

  @Test
  void ignoreRejectsNonDedupRule() {
    when(ruleRepository.findById(10L))
        .thenReturn(
            Optional.of(
                new MdmCleanRule(
                    10L, 1L, MdmCleanRuleType.STANDARDIZE, "姓名标准化", "{}", true, 0, "tester",
                    LocalDateTime.now(), LocalDateTime.now())));

    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.ignoreDedupGroup(1L, 10L, "张三", null, null, "tester"));

    assertEquals(MdmErrorCode.INVALID_CLEAN_RULE, exception.getErrorCode());
    verify(dedupIgnoreRepository, never()).insert(any(), any());
  }

  @Test
  void unignoreReportsNotFoundWhenLedgerRowIsGone() {
    MdmException exception =
        assertThrows(MdmException.class, () -> service.unignoreDedupGroup(7L));

    assertEquals(MdmErrorCode.DEDUP_IGNORE_NOT_FOUND, exception.getErrorCode());
  }

  private static MdmCleanRule dedupRule(Long id, Long entityId) {
    return new MdmCleanRule(
        id,
        entityId,
        MdmCleanRuleType.DEDUP,
        "手机号去重",
        "{\"fields\":[{\"attrCode\":\"mobile\",\"matchType\":\"EXACT\"}],\"condition\":\"AND\"}",
        true,
        0,
        "tester",
        LocalDateTime.now(),
        LocalDateTime.now());
  }

  /** 「启用」开关必须真的能关掉执行,否则停用在业务上等于没停。 */
  @Test
  void findDuplicatesRejectsDisabledRule() {
    MdmCleanRule disabled =
        new MdmCleanRule(
            10L, 1L, MdmCleanRuleType.DEDUP, "手机号去重",
            "{\"fields\":[{\"attrCode\":\"mobile\",\"matchType\":\"EXACT\"}],\"condition\":\"AND\"}",
            false, 0, "tester", LocalDateTime.now(), LocalDateTime.now());
    when(ruleRepository.findById(10L)).thenReturn(Optional.of(disabled));

    MdmException exception =
        assertThrows(
            MdmException.class, () -> service.findDuplicates(1L, 10L, 1, 10));
    assertEquals(MdmErrorCode.INVALID_CLEAN_RULE, exception.getErrorCode());
    verify(recordRepository, never()).countDedupKeys(anyLong(), anyLong(), any(), any());
  }

  @Test
  void previewMergeMergesAttributesAndSourceIds() {
    when(recordRepository.listActiveByIds(1L, List.of(2L, 1L)))
        .thenReturn(
            List.of(
                record(1L, "C001", "{\"mobile\":\"13800138000\",\"name\":\"\"}",
                    "{\"9\":\"crm_1\"}"),
                record(2L, "C002", "{\"mobile\":\"13800138000\",\"name\":\"张三\"}",
                    "{\"11\":\"trade_2\"}")));

    MdmCleanService.MergePreview preview =
        service.previewMerge(1L, 1L, List.of(2L));

    assertEquals("张三", preview.mergedAttributes().get("name"));
    assertEquals("13800138000", preview.mergedAttributes().get("mobile"));
    assertEquals("crm_1", preview.mergedSourceIds().get("9"));
    assertEquals("trade_2", preview.mergedSourceIds().get("11"));
    assertEquals(2, preview.records().size());
  }

  @Test
  void executeMergeUpdatesMasterAndMergesOthers() {
    when(ruleRepository.findById(10L))
        .thenReturn(
            Optional.of(
                new MdmCleanRule(
                    10L, 1L, MdmCleanRuleType.DEDUP, "手机号去重",
                    "{\"fields\":[{\"attrCode\":\"mobile\",\"matchType\":\"EXACT\"}],\"condition\":\"AND\"}",
                    true, 0, "tester", LocalDateTime.now(), LocalDateTime.now())));
    when(recordRepository.listActiveByIds(1L, List.of(2L, 1L)))
        .thenReturn(
            List.of(
                record(1L, "C001", "{\"mobile\":\"13800138000\",\"name\":\"\"}",
                    "{\"9\":\"crm_1\"}"),
                record(2L, "C002", "{\"mobile\":\"13800138000\",\"name\":\"张三\"}",
                    "{\"11\":\"trade_2\"}")));
    when(recordRepository.update(any())).thenReturn(true);
    lenient()
        .when(mergeLogRepository.insert(any(MdmMergeLog.class), any()))
        .thenReturn(null);

    service.executeMerge(1L, 10L, 1L, List.of(2L), "tester");

    verify(recordRepository, Mockito.times(2)).update(any());
    verify(mergeLogRepository).insert(any(MdmMergeLog.class), any());
    verify(notifier).mergeCompleted(1L, "C001", 1);
  }

  @Test
  void executeMergeRejectsMasterInsideMergedList() {
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.executeMerge(1L, null, 1L, List.of(1L, 2L), "tester"));
    assertEquals(MdmErrorCode.MERGE_INVALID, exception.getErrorCode());
    verify(recordRepository, never()).update(any());
    verify(notifier, never()).mergeCompleted(any(), any(), Mockito.anyInt());
  }

  @Test
  void executeMergeRejectsMissingRecord() {
    when(recordRepository.listActiveByIds(1L, List.of(2L, 1L)))
        .thenReturn(List.of(record(1L, "C001", "{}", "{}")));
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.executeMerge(1L, null, 1L, List.of(2L), "tester"));
    assertEquals(MdmErrorCode.MERGE_INVALID, exception.getErrorCode());
    verify(recordRepository, never()).update(any());
  }

  @Test
  void createRejectsRuleNameTooLong() {
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.create(1L, "名".repeat(129), expr(MdmMatchType.EXACT), 0, "tester"));
    assertEquals(MdmErrorCode.INVALID_CLEAN_RULE, exception.getErrorCode());
  }

  @Test
  void createPersistsRuleWithParsedExpr() {
    lenient().when(entityService.get(1L)).thenReturn(entity());
    when(ruleRepository.existsByName(1L, "手机号去重", null)).thenReturn(false);
    when(ruleRepository.insert(any(MdmCleanRule.class), any()))
        .thenAnswer(
            invocation -> {
              MdmCleanRule rule = invocation.getArgument(0);
              return rule.withPersisted(5L, "tester", LocalDateTime.now());
            });

    MdmCleanRule created =
        service.create(1L, "手机号去重", expr(MdmMatchType.FUZZY), 1, "tester");

    assertEquals(5L, created.id());
    assertEquals(MdmCleanRuleType.DEDUP, created.ruleType());
    assertTrue(created.ruleExpr().contains("\"matchType\":\"FUZZY\""));
    assertTrue(created.enabled());
    verify(ruleRepository).insert(any(MdmCleanRule.class), any());
  }
}
