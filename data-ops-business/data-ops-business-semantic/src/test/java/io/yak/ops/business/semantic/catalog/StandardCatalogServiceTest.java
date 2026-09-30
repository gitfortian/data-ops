package io.yak.ops.business.semantic.catalog;

import io.yak.ops.business.semantic.api.StandardStatus;

import io.yak.ops.business.semantic.api.StandardKind;

import io.yak.ops.business.semantic.api.Standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.semantic.api.SemanticStandardApi;
import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.business.semantic.repository.SemanticFieldRepository;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import io.yak.ops.business.semantic.repository.SemanticStandardVersionRepository;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 标准目录规则单元测试:类别/编码/名称校验、类别专有必填、编码唯一、码集批量保存(32.1)。 */
class StandardCatalogServiceTest {

  private SemanticStandardRepository repository;
  private SemanticStandardVersionRepository versionRepository;
  private SemanticFieldRepository fieldRepository;
  private BusinessAuditService auditService;
  private AuditOperationHandle audit;
  private StandardCatalogService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(SemanticStandardRepository.class);
    versionRepository = Mockito.mock(SemanticStandardVersionRepository.class);
    fieldRepository = Mockito.mock(SemanticFieldRepository.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    audit = Mockito.mock(AuditOperationHandle.class);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    service = new StandardCatalogService(repository, versionRepository, fieldRepository, auditService);
  }

  @Test
  void createRejectsUnknownKind() {
    SemanticStandardApi.CreateRequest request =
        new SemanticStandardApi.CreateRequest(
            "BAD_KIND", "some_code", "某标准", null, null,
            null, null, null, null,
            null, null, null,
            null, null, null,
            null, null,
            null, null, null,
            null, null);
    SemanticException exception =
        assertThrows(SemanticException.class, () -> service.create(request, "tester"));
    assertEquals(SemanticErrorCode.INVALID_KIND, exception.getErrorCode());
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void createRejectsInvalidCode() {
    SemanticException exception =
        assertThrows(
            SemanticException.class, () -> service.create(createRequest("bad-code!"), "tester"));
    assertEquals(SemanticErrorCode.INVALID_CODE, exception.getErrorCode());
  }

  @Test
  void createRejectsMissingKindRequiredField() {
    // NAMING 必填 rule_expr
    SemanticStandardApi.CreateRequest request =
        new SemanticStandardApi.CreateRequest(
            "NAMING", "ods_prefix", "ODS 前缀", null, null,
            "TABLE", null, null, null,
            null, null, null,
            null, null, null,
            null, null,
            null, null, null,
            null, null);
    SemanticException exception =
        assertThrows(SemanticException.class, () -> service.create(request, "tester"));
    assertEquals(SemanticErrorCode.KIND_FIELD_REQUIRED, exception.getErrorCode());
  }

  @Test
  void createRejectsDuplicateCode() {
    when(repository.existsByCode(StandardKind.NAMING, "ods_prefix")).thenReturn(true);
    SemanticException exception =
        assertThrows(
            SemanticException.class, () -> service.create(createRequest("ods_prefix"), "tester"));
    assertEquals(SemanticErrorCode.DUPLICATE_CODE, exception.getErrorCode());
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void createInsertsWithDefaultsAndAudits() {
    when(repository.existsByCode(StandardKind.NAMING, "ods_prefix")).thenReturn(false);
    when(repository.insert(any(), eq("tester")))
        .thenAnswer(invocation -> invocation.getArgument(0));
    Standard created = service.create(createRequest("ods_prefix"), "tester");
    assertEquals(StandardKind.NAMING, created.kind());
    assertEquals(StandardStatus.ENABLED, created.status());
    assertEquals(1, created.version());
    assertEquals(0, created.sortOrder());
    Mockito.verify(audit, never()).failure(Mockito.anyString(), Mockito.any(Throwable.class));
  }

  @Test
  void deleteFailsWhenRepositoryReportsNothingDeleted() {
    Standard existing = existingStandard();
    when(repository.findById(9L)).thenReturn(java.util.Optional.of(existing));
    when(repository.deleteById(9L)).thenReturn(false);
    SemanticException exception =
        assertThrows(SemanticException.class, () -> service.delete(9L, "tester"));
    assertEquals(SemanticErrorCode.DELETE_FAILED, exception.getErrorCode());
  }

  @Test
  void updateBumpsVersion() {
    Standard existing = existingStandard();
    when(repository.findById(9L)).thenReturn(java.util.Optional.of(existing));
    when(repository.update(any(), eq(existing.version()), eq("tester")))
        .thenAnswer(invocation -> invocation.getArgument(0));
    Standard updated = service.update(9L, updateRequest(), "tester");
    assertEquals(existing.version() + 1, updated.version());
    assertEquals("改名后的标准", updated.name());
    // 32:修改前必须落版本快照
    verify(versionRepository).recordSnapshot(existing, "tester");
  }

  // ── 码集批量保存(32.1) ──

  @Test
  void saveCodeSetCreatesRowsWithGeneratedCodes() {
    when(repository.listByCodeSetCode("order_status")).thenReturn(List.of());
    when(repository.existsByCodeSetCode("order_status")).thenReturn(false);
    when(repository.existsByCode(StandardKind.CODE, "order_status_1")).thenReturn(false);
    when(repository.existsByCode(StandardKind.CODE, "order_status_2")).thenReturn(false);
    when(repository.insert(any(), eq("tester")))
        .thenAnswer(invocation -> invocation.getArgument(0));
    List<Standard> saved =
        service.saveCodeSet(
            codeSetRequest(
                "order_status",
                null,
                new SemanticStandardApi.CodeValueItem("1", "待支付", 0),
                new SemanticStandardApi.CodeValueItem("2", "已支付", 1)),
            "tester");
    assertEquals(2, saved.size());
    assertEquals("order_status_1", saved.get(0).code());
    assertEquals("订单状态", saved.get(0).name());
    assertEquals("order_status", saved.get(0).fields().codeSetCode());
    // 新建不落快照
    verify(versionRepository, never()).recordSnapshot(any(), any());
  }

  @Test
  void saveCodeSetSanitizesUnsafeCharsAndSuffixesConflicts() {
    when(repository.listByCodeSetCode("gender")).thenReturn(List.of());
    when(repository.existsByCodeSetCode("gender")).thenReturn(false);
    when(repository.existsByCode(StandardKind.CODE, "gender_1")).thenReturn(true);
    when(repository.existsByCode(StandardKind.CODE, "gender_1_2")).thenReturn(false);
    when(repository.insert(any(), eq("tester")))
        .thenAnswer(invocation -> invocation.getArgument(0));
    List<Standard> saved =
        service.saveCodeSet(
            codeSetRequest("gender", null, new SemanticStandardApi.CodeValueItem("1", "男", 0)),
            "tester");
    assertEquals("gender_1_2", saved.get(0).code());
  }

  @Test
  void saveCodeSetRejectsDuplicateValueInRequest() {
    SemanticException exception =
        assertThrows(
            SemanticException.class,
            () ->
                service.saveCodeSet(
                    codeSetRequest(
                        "order_status",
                        null,
                        new SemanticStandardApi.CodeValueItem("1", "待支付", 0),
                        new SemanticStandardApi.CodeValueItem("1", "重复", 1)),
                    "tester"));
    assertEquals(SemanticErrorCode.CODE_SET_VALUE_DUPLICATE, exception.getErrorCode());
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void saveCodeSetRejectsDuplicateSetOnCreate() {
    when(repository.listByCodeSetCode("order_status")).thenReturn(List.of());
    when(repository.existsByCodeSetCode("order_status")).thenReturn(true);
    SemanticException exception =
        assertThrows(
            SemanticException.class,
            () ->
                service.saveCodeSet(
                    codeSetRequest("order_status", null, new SemanticStandardApi.CodeValueItem("1", "待支付", 0)),
                    "tester"));
    assertEquals(SemanticErrorCode.CODE_SET_DUPLICATE, exception.getErrorCode());
  }

  @Test
  void saveCodeSetUpdateSnapshotsModifiedAndRemovesDeletedRows() {
    Standard kept = codeRow(1L, "order_status_1", "order_status", "1", 2);
    Standard removed = codeRow(2L, "order_status_2", "order_status", "2", 3);
    when(repository.listByCodeSetCode("order_status")).thenReturn(List.of(kept, removed));
    when(repository.update(any(), eq("tester"))).thenAnswer(invocation -> invocation.getArgument(0));
    when(repository.existsByCode(StandardKind.CODE, "order_status_3")).thenReturn(false);
    when(repository.insert(any(), eq("tester")))
        .thenAnswer(invocation -> invocation.getArgument(0));
    List<Standard> saved =
        service.saveCodeSet(
            codeSetRequest(
                "order_status",
                null,
                new SemanticStandardApi.CodeValueItem("1", "改标签", 0),
                new SemanticStandardApi.CodeValueItem("3", "新增", 2)),
            "tester");
    // 修改行:先落快照再更新(32 不变式)
    verify(versionRepository).recordSnapshot(kept, "tester");
    verify(repository).update(any(), eq("tester"));
    // 未提交的既有码值:删除
    verify(repository).deleteById(2L);
    // 新增行:编码自动生成
    ArgumentCaptor<Standard> captor = ArgumentCaptor.forClass(Standard.class);
    verify(repository).insert(captor.capture(), eq("tester"));
    assertEquals("order_status_3", captor.getValue().code());
    // 返回 = 更新的既有行 + 新增行
    assertEquals(2, saved.size());
  }

  @Test
  void saveCodeSetAdoptsLegacyRowIntoCompletedCodeSet() {
    Standard legacy = codeRow(5L, "legacy_row", null, "1", 1);
    when(repository.listByCodeSetCode("legacy_row")).thenReturn(List.of(legacy));
    when(repository.existsByCodeSetCode("order_status")).thenReturn(false);
    when(repository.update(any(), eq("tester"))).thenAnswer(invocation -> invocation.getArgument(0));
    List<Standard> saved =
        service.saveCodeSet(
            codeSetRequest(
                "order_status", "legacy_row", new SemanticStandardApi.CodeValueItem("1", "待支付", 0)),
            "tester");
    assertEquals("order_status", saved.get(0).fields().codeSetCode());
    // 采纳保留原行编码(std_code 不可改)
    assertEquals("legacy_row", saved.get(0).code());
  }

  @Test
  void saveCodeSetRejectsAdoptIntoExistingSet() {
    Standard legacy = codeRow(5L, "legacy_row", null, "1", 1);
    when(repository.listByCodeSetCode("legacy_row")).thenReturn(List.of(legacy));
    when(repository.existsByCodeSetCode("order_status")).thenReturn(true);
    SemanticException exception =
        assertThrows(
            SemanticException.class,
            () ->
                service.saveCodeSet(
                    codeSetRequest(
                        "order_status",
                        "legacy_row",
                        new SemanticStandardApi.CodeValueItem("1", "待支付", 0)),
                    "tester"));
    assertEquals(SemanticErrorCode.CODE_SET_DUPLICATE, exception.getErrorCode());
  }

  @Test
  void deleteCodeSetBlocksWhenReferencedByField() {
    when(repository.listByCodeSetCode("order_status"))
        .thenReturn(List.of(codeRow(1L, "order_status_1", "order_status", "1", 1)));
    when(fieldRepository.countByCodeSet("order_status")).thenReturn(2L);
    SemanticException exception =
        assertThrows(SemanticException.class, () -> service.deleteCodeSet("order_status", "tester"));
    assertEquals(SemanticErrorCode.STANDARD_REFERENCED, exception.getErrorCode());
    verify(repository, never()).deleteByCodeSetCode(any());
  }

  @Test
  void changeCodeSetStatusFailsWhenGroupMissing() {
    when(repository.listByCodeSetCode("missing")).thenReturn(List.of());
    SemanticException exception =
        assertThrows(
            SemanticException.class, () -> service.changeCodeSetStatus("missing", "DISABLED", "tester"));
    assertEquals(SemanticErrorCode.CODE_SET_NOT_FOUND, exception.getErrorCode());
  }

  private Standard codeRow(long id, String code, String setCode, String value, int version) {
    return new Standard(
        id,
        StandardKind.CODE,
        code,
        "订单状态",
        StandardStatus.ENABLED,
        version,
        0,
        false,
        null,
        new Standard.KindFields(null, null, null, null, null, null, null, setCode, value, "标签",
            null, null, null, null, null, null, null),
        "tester",
        null,
        null);
  }

  private SemanticStandardApi.CodeSetSaveRequest codeSetRequest(
      String setCode, String origin, SemanticStandardApi.CodeValueItem... items) {
    return new SemanticStandardApi.CodeSetSaveRequest(setCode, origin, "订单状态", null, List.of(items));
  }

  private Standard existingStandard() {
    return new Standard(
        9L,
        StandardKind.NAMING,
        "ods_prefix",
        "ODS 前缀",
        StandardStatus.ENABLED,
        3,
        0,
        false,
        null,
        new Standard.KindFields(null, null, "^ods_", null, null, null, null, null, null, null,
            null, null, null, null, null, null, null),
        "tester",
        null,
        null);
  }

  private SemanticStandardApi.CreateRequest createRequest(String code) {
    return new SemanticStandardApi.CreateRequest(
        "NAMING", code, "ODS 前缀", null, null,
        "TABLE", null, "^ods_", null,
        null, null, null,
        null, null, null,
        null, null,
        null, null, null,
        null, null);
  }

  private SemanticStandardApi.UpdateRequest updateRequest() {
    return new SemanticStandardApi.UpdateRequest(
        3, "改名后的标准", null, null,
        "TABLE", null, "^ods_", null,
        null, null, null,
        null, null, null,
        null, null,
        null, null, null,
        null, null);
  }
}
