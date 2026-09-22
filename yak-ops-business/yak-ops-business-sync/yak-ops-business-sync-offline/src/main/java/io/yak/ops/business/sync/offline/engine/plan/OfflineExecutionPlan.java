package io.yak.ops.business.sync.offline.engine.plan;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;
import org.springframework.util.StringUtils;

/** Immutable control-plane plan persisted inside the frozen logical execution snapshot. */
public record OfflineExecutionPlan(
    OfflineExecutionStrategy strategy,
    List<Unit> units) {

  public OfflineExecutionPlan {
    strategy = Objects.requireNonNull(strategy, "strategy");
    units = List.copyOf(Objects.requireNonNull(units, "units"));
    if (strategy == OfflineExecutionStrategy.FAN_OUT && units.isEmpty()) {
      throw new IllegalArgumentException("FAN_OUT execution plan must contain at least one unit");
    }
  }

  public boolean isFanOut() {
    return strategy == OfflineExecutionStrategy.FAN_OUT;
  }

  public record Unit(
      String unitKey,
      String sourceTable,
      String sinkTable,
      JsonNode logicalJobSpec) {

    public Unit {
      if (!StringUtils.hasText(unitKey)) {
        throw new IllegalArgumentException("fan-out unitKey must not be blank");
      }
      if (!StringUtils.hasText(sourceTable)) {
        throw new IllegalArgumentException("fan-out sourceTable must not be blank");
      }
      if (!StringUtils.hasText(sinkTable)) {
        throw new IllegalArgumentException("fan-out sinkTable must not be blank");
      }
      if (logicalJobSpec == null || !logicalJobSpec.isObject()) {
        throw new IllegalArgumentException("fan-out logicalJobSpec must be a JSON object");
      }
      unitKey = unitKey.trim();
      sourceTable = sourceTable.trim();
      sinkTable = sinkTable.trim();
      logicalJobSpec = logicalJobSpec.deepCopy();
    }
  }
}
