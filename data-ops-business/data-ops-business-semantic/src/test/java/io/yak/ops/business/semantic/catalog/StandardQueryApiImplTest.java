package io.yak.ops.business.semantic.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardQueryApi;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
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

  @BeforeEach
  void setUp() {
    repository = mock(SemanticStandardRepository.class);
    api = new StandardQueryApiImpl(repository);
  }

  @Test
  void returnsProjectScopedCodeSetRevisionIncludingDisabledValues() {
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
  void codeSetRevisionDigestIsIndependentOfDaoRowOrder() {
    var created = code(1L, "ORDER_STATUS", "CREATED", StandardStatus.ENABLED);
    var cancelled = code(2L, "ORDER_STATUS", "CANCELLED", StandardStatus.ENABLED);
    when(repository.listByCodeSetCode("ORDER_STATUS")).thenReturn(List.of(created, cancelled));
    String first = api.codeSetReference("ORDER_STATUS").contentSha256();
    when(repository.listByCodeSetCode("ORDER_STATUS")).thenReturn(List.of(cancelled, created));
    String second = api.codeSetReference("ORDER_STATUS").contentSha256();
    assertEquals(first, second);
  }

  @Test
  void missingOrEmptyCodeSetIsNotReportedAsEnabled() {
    when(repository.listByCodeSetCode("MISSING")).thenReturn(List.of());
    var result = api.codeSetReference("MISSING");
    assertEquals("NOT_FOUND", result.status());
    assertEquals(0, result.itemCount());
    assertNull(result.contentSha256());
  }

  @Test
  void unrelatedOrWrongKindRowsCannotMakeASetLookEnabled() {
    var wrongSet = code(1L, "OTHER_SET", "CREATED", StandardStatus.ENABLED);
    var wrongKind = new Standard(2L, StandardKind.TYPE, "TYPE_2", "类型", StandardStatus.ENABLED,
        1, 0, false, null,
        new Standard.KindFields(null, null, null, null, "BIGINT", "BIGINT", null,
            null, null, null, null, null, null, null, null, null, null),
        "owner", null, null);
    when(repository.listByCodeSetCode("ORDER_STATUS")).thenReturn(List.of(wrongSet, wrongKind));
    var result = api.codeSetReference("ORDER_STATUS");
    assertEquals("NOT_FOUND", result.status());
    assertEquals(0, result.itemCount());
    assertNull(result.contentSha256());
  }
}
