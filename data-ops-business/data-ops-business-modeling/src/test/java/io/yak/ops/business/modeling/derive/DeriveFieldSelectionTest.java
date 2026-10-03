package io.yak.ops.business.modeling.derive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.modeling.derive.ModelDeriveService.DerivedField;
import io.yak.ops.business.modeling.exception.ModelingException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeriveFieldSelectionTest {
  @Test
  void preservesRequestedOrderAndMandatoryTechnicalsWithoutMutatingInputs() {
    var technical = new DeriveTechnicalColumn("_batch_id", "BIGINT", null, "batch");
    var inherited = new ArrayList<>(List.of(field("a"), field("b")));
    var requested = new ArrayList<>(List.of(chosen("B", true), chosen("a", false)));
    var plan = DeriveFieldSelection.plan(requested, inherited, List.of(technical));
    assertEquals(List.of("b"), plan.choices().stream().map(c -> c.base().columnName()).toList());
    assertEquals(List.of(technical), plan.missingTechnicals());
    requested.clear();
    inherited.clear();
    assertEquals(1, plan.choices().size());
    assertThrows(UnsupportedOperationException.class, () -> plan.choices().clear());
  }

  @Test
  void rejectsUnknownIncludedColumnButIgnoresExcludedUnknownColumn() {
    assertThrows(ModelingException.class,
        () -> DeriveFieldSelection.plan(List.of(chosen("missing", true)), List.of(), List.of()));
    assertTrue(DeriveFieldSelection.plan(
        List.of(chosen("missing", false)), List.of(), List.of()).choices().isEmpty());
  }

  @Test
  void includedTechnicalIsNotAddedTwice() {
    var technical = new DeriveTechnicalColumn("_batch_id", "BIGINT", null, "batch");
    assertTrue(DeriveFieldSelection.plan(List.of(chosen("_BATCH_ID", true)),
        List.of(field("_batch_id")), List.of(technical)).missingTechnicals().isEmpty());
  }

  private static DerivedField chosen(String name, boolean include) {
    return new DerivedField("source", name, name, null, null, include, false, null, null);
  }

  private static ResolvedDeriveField field(String name) {
    return new ResolvedDeriveField("source", "SOURCE", 1L, "source", null, "VARCHAR",
        null, null, null, name, null, true, null, null, null, null, null);
  }
}
