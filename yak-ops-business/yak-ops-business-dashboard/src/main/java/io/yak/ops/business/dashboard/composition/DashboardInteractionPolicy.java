package io.yak.ops.business.dashboard.composition;

import io.yak.ops.business.dashboard.domain.InteractionSpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Normalizes widget-to-filter interaction rules inside one DashboardVersion candidate. */
@Component
public class DashboardInteractionPolicy {

  private static final int MAX_INTERACTIONS = 100;

  public List<InteractionSpec> normalize(
      List<InteractionSpec> values,
      Set<String> widgetKeys,
      Set<String> filterKeys) {
    List<InteractionSpec> source = values == null ? List.of() : values;
    if (source.size() > MAX_INTERACTIONS) {
      throw new IllegalArgumentException(
          "Dashboard 联动规则不能超过 " + MAX_INTERACTIONS + " 个");
    }

    List<InteractionSpec> normalized = new ArrayList<>(source.size());
    Set<String> interactionKeys = new HashSet<>();
    for (InteractionSpec value : source) {
      if (value == null) {
        throw new IllegalArgumentException("Dashboard 联动规则不能为空");
      }
      String interactionKey = required(value.interactionKey(), "interactionKey", 64);
      if (!interactionKeys.add(interactionKey)) {
        throw new IllegalArgumentException("interactionKey 重复：" + interactionKey);
      }
      String sourceWidgetKey = required(value.sourceWidgetKey(), "联动 sourceWidgetKey", 64);
      if (!widgetKeys.contains(sourceWidgetKey)) {
        throw new IllegalArgumentException("联动来源 Widget 不存在：" + sourceWidgetKey);
      }
      String targetFilterKey = required(value.targetFilterKey(), "联动 targetFilterKey", 64);
      if (!filterKeys.contains(targetFilterKey)) {
        throw new IllegalArgumentException("联动目标筛选器不存在：" + targetFilterKey);
      }
      normalized.add(new InteractionSpec(
          interactionKey,
          Objects.requireNonNull(value.event(), "联动 event"),
          sourceWidgetKey,
          required(value.sourceFieldId(), "联动 sourceFieldId", 64),
          targetFilterKey));
    }
    return List.copyOf(normalized);
  }

  private String required(String value, String label, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + "不能为空");
    }
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(label + "不能超过 " + maxLength + " 个字符");
    }
    return normalized;
  }
}
