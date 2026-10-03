package io.yak.ops.business.modeling.derive;

import io.yak.ops.business.modeling.derive.ModelDeriveService.DerivedField;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Pure selection rules; standard-field lookups and writes remain in the application service. */
final class DeriveFieldSelection {
  private DeriveFieldSelection() {}

  record Choice(ResolvedDeriveField base, DerivedField chosen) {}

  record Plan(List<Choice> choices, List<DeriveTechnicalColumn> missingTechnicals) {
    Plan {
      choices = List.copyOf(choices);
      missingTechnicals = List.copyOf(missingTechnicals);
    }
  }

  static Plan plan(
      List<DerivedField> requested,
      List<ResolvedDeriveField> inherited,
      List<DeriveTechnicalColumn> technicals) {
    Map<String, ResolvedDeriveField> byName = new LinkedHashMap<>();
    for (ResolvedDeriveField field : inherited) {
      byName.put(normalize(field.columnName()), field);
    }
    List<Choice> choices = new ArrayList<>();
    if (requested != null) {
      for (DerivedField chosen : requested) {
        if (!chosen.include() || chosen.sourceColumn() == null || chosen.sourceColumn().isBlank()) {
          continue;
        }
        ResolvedDeriveField base = byName.get(normalize(chosen.sourceColumn()));
        if (base == null) {
          throw new ModelingException(
              ModelingErrorCode.INVALID_COLUMN,
              "字段不在继承范围内：" + chosen.sourceTable() + "." + chosen.sourceColumn());
        }
        choices.add(new Choice(base, chosen));
      }
    }
    Set<String> present = choices.stream()
        .map(choice -> normalize(choice.base().columnName())).collect(Collectors.toSet());
    return new Plan(choices, technicals.stream()
        .filter(column -> !present.contains(normalize(column.name()))).toList());
  }

  private static String normalize(String name) {
    return name.toLowerCase(Locale.ROOT);
  }
}
