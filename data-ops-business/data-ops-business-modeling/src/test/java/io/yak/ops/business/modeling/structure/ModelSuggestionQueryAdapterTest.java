package io.yak.ops.business.modeling.structure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelSuggestionQueryAdapterTest {
  private final ModelStructureService structures = mock(ModelStructureService.class);
  private final ActionAuthorization authorization = mock(ActionAuthorization.class);
  private final ModelSuggestionQueryAdapter adapter = new ModelSuggestionQueryAdapter(structures, authorization);
  private StructureView view(String description) {
    var column = new StructureView.ColumnView(1L, "amount", "DECIMAL", 18, 2, true, "private-default", "private-comment", description, 0,
        null, null, null, null, null, null, null, null, null, null);
    return new StructureView(9L, "orders", "订单", "MYSQL", "DRAFT", "", "orders", "", List.of(column), List.of(), List.of(), null, Map.of());
  }
  @Test void fieldsProjectOnlyBoundedMetadataAndAnExactStructureFingerprint() {
    var original = view("金额"); when(structures.getForUpdate(9L)).thenReturn(original);
    var source = adapter.fields(9);
    assertEquals(StructureFingerprint.of(original), source.definition()); assertEquals("amount", source.fields().getFirst().name());
    assertEquals("金额", source.fields().getFirst().description()); assertFalse(source.toString().contains("private"));
    when(structures.getForUpdate(9L)).thenReturn(view("x".repeat(513)));
    assertThrows(IllegalArgumentException.class, () -> adapter.fields(9));
  }
  @Test void permissionFailurePrecedesTheModelRead() {
    doThrow(new SecurityException("forbidden")).when(authorization).requirePermission(ModelingPermissionCode.READ);
    assertThrows(SecurityException.class, () -> adapter.fields(9)); verifyNoInteractions(structures);
  }
}
