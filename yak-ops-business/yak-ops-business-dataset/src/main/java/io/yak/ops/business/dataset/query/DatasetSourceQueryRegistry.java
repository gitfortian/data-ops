package io.yak.ops.business.dataset.query;

import io.yak.ops.business.dataset.DatasetSourceType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Resolves exactly one query runtime adapter for each supported Dataset source type. */
@Component
public class DatasetSourceQueryRegistry {

  private final Map<DatasetSourceType, DatasetSourceQueryAdapter> adapters;

  public DatasetSourceQueryRegistry(List<DatasetSourceQueryAdapter> adapters) {
    Map<DatasetSourceType, DatasetSourceQueryAdapter> discovered = new LinkedHashMap<>();
    for (DatasetSourceQueryAdapter adapter : adapters) {
      DatasetSourceQueryAdapter existing = discovered.putIfAbsent(adapter.sourceType(), adapter);
      if (existing != null) {
        throw new IllegalStateException("重复的 Dataset query adapter：" + adapter.sourceType());
      }
    }
    this.adapters = Map.copyOf(discovered);
  }

  public DatasetSourceQueryAdapter require(DatasetSourceType sourceType) {
    DatasetSourceQueryAdapter adapter = adapters.get(sourceType);
    if (adapter == null) {
      throw new IllegalStateException("Dataset sourceType 尚未接入 Query Runtime：" + sourceType);
    }
    return adapter;
  }
}
