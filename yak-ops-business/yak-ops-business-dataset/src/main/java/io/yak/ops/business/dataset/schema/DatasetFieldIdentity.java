package io.yak.ops.business.dataset.schema;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Owns deterministic Dataset field identity across immutable versions. */
@Component
public class DatasetFieldIdentity {

  public String stableFieldId(long datasetId, String physicalName) {
    if (datasetId <= 0L) {
      throw new IllegalArgumentException("datasetId 必须大于 0");
    }
    if (physicalName == null || physicalName.isBlank()) {
      throw new IllegalArgumentException("physicalName 不能为空");
    }
    String normalized = physicalName.trim().toLowerCase(Locale.ROOT);
    return UUID.nameUUIDFromBytes(
            ("dataset:" + datasetId + ":" + normalized).getBytes(StandardCharsets.UTF_8))
        .toString();
  }
}
