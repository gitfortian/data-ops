package io.yak.ops.business.semantic.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.semantic.api.*;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import io.yak.ops.common.constant.semantic.SemanticPermissionCode;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StandardSuggestionQueryAdapterTest {
  private final SemanticStandardRepository repo = mock(SemanticStandardRepository.class);
  private final ActionAuthorization authorization = mock(ActionAuthorization.class);
  private final StandardSuggestionQueryAdapter api = new StandardSuggestionQueryAdapter(repo, authorization);
  private Standard standard(StandardKind kind, StandardStatus status, int version) {
    var fields = new Standard.KindFields(null, null, null, null, "id", "BIGINT", null, null, null, null, null, null, null, null, null, null, null);
    return new Standard(9L, kind, "id", "编号", status, version, 0, false, "说明", fields, null, null, null);
  }
  @Test void deniedReadsTouchNoRepository() {
    doThrow(new SecurityException()).when(authorization).requirePermission(SemanticPermissionCode.READ);
    assertThrows(SecurityException.class, () -> api.types(""));
    assertThrows(SecurityException.class, () -> api.requireType(9, 1));
    verifyNoInteractions(repo);
  }
  @Test void resultIsBoundedAndTruncationIsExplicit() {
    when(repo.searchTypeCandidates("id")).thenReturn(java.util.Collections.nCopies(21, standard(StandardKind.TYPE, StandardStatus.ENABLED, 1)));
    assertEquals(20, api.types(" id ").candidates().size()); assertTrue(api.types("id").truncated());
    assertThrows(IllegalArgumentException.class, () -> api.types("x".repeat(65)));
  }
  @Test void removedDisabledWrongKindAndChangedVersionCannotBeAdopted() {
    when(repo.findById(9L)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> api.requireType(9, 1));
    for (var source : List.of(standard(StandardKind.UNIT, StandardStatus.ENABLED, 1),
        standard(StandardKind.TYPE, StandardStatus.DISABLED, 1), standard(StandardKind.TYPE, StandardStatus.ENABLED, 2))) {
      when(repo.findById(9L)).thenReturn(Optional.of(source));
      assertThrows(IllegalArgumentException.class, () -> api.requireType(9, 1));
    }
  }
}
