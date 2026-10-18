package io.yak.ops.business.semantic.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardQueryApi;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StandardQueryApiImplTest {
  private SemanticStandardRepository repository;
  private StandardQueryApiImpl api;

  private static Standard code(long id, String codeSet, String value, StandardStatus status) {
    return new Standard(id, StandardKind.CODE, "ROW_" + id, value, status, 1, 0,
        false, "test code",
        new Standard.KindFields(null, null, null, null, null, null, null,
            codeSet, value, value, null, null, null, null, null, null, null),
        "owner", null, null);
  }

  private static Standard withAuditMetadata(
      Standard row, String createdBy, LocalDateTime createTime, LocalDateTime updateTime) {
    return new Standard(row.id(), row.kind(), row.code(), row.name(), row.status(), row.version(),
        row.sortOrder(), row.preset(), row.description(), row.fields(),
        createdBy, createTime, updateTime);
  }

  @BeforeEach
  void setUp() {
    repository = mock(SemanticStandardRepository.class);
    api = new StandardQueryApiImpl(repository);
  }

  @Test
  void returnsAllMembersAndDetectsMixedCodeSetStatus() {
    var rows = List.of(
        code(1L, "ORDER_STATUS", "CREATED", StandardStatus.ENABLED),
        code(2L, "ORDER_STATUS", "CANCELLED", StandardStatus.DISABLED));
    when(repository.listByCodeSetCode("ORDER_STATUS")).thenReturn(rows);

    StandardQueryApi.CodeSetReference result = api.codeSetReference("ORDER_STATUS");

    assertEquals("ORDER_STATUS", result.codeSetCode());
    assertEquals(2, result.itemCount());
    assertEquals(1, result.enabledItemCount());
    assertEquals("MIXED", result.status());
    assertEquals(64, result.contentSha256().length());
    verify(repository).listByCodeSetCode("ORDER_STATUS");
  }

  @Test
  void digestIsStableAcrossDaoOrderingButChangesWhenMemberStateChanges() {
    var created = code(1L, "ORDER_STATUS", "CREATED", StandardStatus.ENABLED);
    var cancelled = code(2L, "ORDER_STATUS", "CANCELLED", StandardStatus.ENABLED);
    when(repository.listByCodeSetCode("ORDER_STATUS")).thenReturn(List.of(created, cancelled));
    String first = api.codeSetReference("ORDER_STATUS").contentSha256();
    when(repository.listByCodeSetCode("ORDER_STATUS")).thenReturn(List.of(cancelled, created));
    String same = api.codeSetReference("ORDER_STATUS").contentSha256();
    assertEquals(first, same);

    when(repository.listByCodeSetCode("ORDER_STATUS"))
        .thenReturn(List.of(created, cancelled.withStatus(StandardStatus.DISABLED, null)));
    String changed = api.codeSetReference("ORDER_STATUS").contentSha256();
    assertNotEquals(first, changed);
  }

  @Test
  void digestIgnoresAuditMetadataButRetainsSemanticVersionAndContent() {
    var row = code(1L, "ORDER_STATUS", "CREATED", StandardStatus.ENABLED);
    var auditOnlyUpdate = withAuditMetadata(row, "another-operator",
        LocalDateTime.parse("2026-10-01T10:00:00"),
        LocalDateTime.parse("2026-10-18T14:00:00"));
    when(repository.listByCodeSetCode("ORDER_STATUS")).thenReturn(List.of(row));
    String first = api.codeSetReference("ORDER_STATUS").contentSha256();

    when(repository.listByCodeSetCode("ORDER_STATUS")).thenReturn(List.of(auditOnlyUpdate));
    String sameContent = api.codeSetReference("ORDER_STATUS").contentSha256();
    assertEquals(first, sameContent);

    var newVersion = new Standard(row.id(), row.kind(), row.code(), row.name(), row.status(),
        row.version() + 1, row.sortOrder(), row.preset(), row.description(), row.fields(),
        auditOnlyUpdate.createdBy(), auditOnlyUpdate.createTime(), auditOnlyUpdate.updateTime());
    when(repository.listByCodeSetCode("ORDER_STATUS")).thenReturn(List.of(newVersion));
    String changedVersion = api.codeSetReference("ORDER_STATUS").contentSha256();
    assertNotEquals(first, changedVersion);

    var changedLabel = new Standard(row.id(), row.kind(), row.code(), "已创建（修订）", row.status(),
        row.version(), row.sortOrder(), row.preset(), row.description(), row.fields(),
        auditOnlyUpdate.createdBy(), auditOnlyUpdate.createTime(), auditOnlyUpdate.updateTime());
    when(repository.listByCodeSetCode("ORDER_STATUS")).thenReturn(List.of(changedLabel));
    String changedContent = api.codeSetReference("ORDER_STATUS").contentSha256();
    assertNotEquals(first, changedContent);
  }

  @Test
  void emptyAndUnrelatedRowsAreNotReportedAsAnEnabledCodeSet() {
    when(repository.listByCodeSetCode("MISSING")).thenReturn(List.of());
    var missing = api.codeSetReference("MISSING");
    assertEquals("NOT_FOUND", missing.status());
    assertEquals(0, missing.itemCount());
    assertNull(missing.contentSha256());

    var wrongSet = code(1L, "OTHER_SET", "CREATED", StandardStatus.ENABLED);
    var wrongKind = new Standard(2L, StandardKind.TYPE, "TYPE_2", "类型",
        StandardStatus.ENABLED, 1, 0, false, null,
        new Standard.KindFields(null, null, null, null, "BIGINT", "BIGINT", null,
            null, null, null, null, null, null, null, null, null, null),
        "owner", null, null);
    when(repository.listByCodeSetCode("ORDER_STATUS")).thenReturn(List.of(wrongSet, wrongKind));
    var unrelated = api.codeSetReference("ORDER_STATUS");
    assertEquals("NOT_FOUND", unrelated.status());
    assertEquals(0, unrelated.itemCount());
    assertNull(unrelated.contentSha256());
  }

  @Test
  void allDisabledMembersProduceDisabledSetWithFullRevisionDigest() {
    when(repository.listByCodeSetCode("ORDER_STATUS")).thenReturn(List.of(
        code(1L, "ORDER_STATUS", "CREATED", StandardStatus.DISABLED),
        code(2L, "ORDER_STATUS", "CANCELLED", StandardStatus.DISABLED)));
    var result = api.codeSetReference("ORDER_STATUS");
    assertEquals("DISABLED", result.status());
    assertEquals(2, result.itemCount());
    assertEquals(0, result.enabledItemCount());
    assertEquals(64, result.contentSha256().length());
  }
}
