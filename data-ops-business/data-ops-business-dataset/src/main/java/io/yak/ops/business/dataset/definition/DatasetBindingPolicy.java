package io.yak.ops.business.dataset.definition;

import io.yak.ops.business.dataset.DatasetDetail;
import io.yak.ops.business.dataset.DatasetStatus;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Validates downstream bindings against the current ONLINE Dataset schema contract. */
@Component
public class DatasetBindingPolicy {

  private final DatasetReader reader;

  public DatasetBindingPolicy(DatasetReader reader) {
    this.reader = reader;
  }

  public void validateAnalysisBinding(long datasetId, Collection<String> fieldIds) {
    DatasetDetail detail = reader.require(datasetId);
    if (detail.dataset().status() != DatasetStatus.ONLINE) {
      throw new IllegalArgumentException("Analysis 只能绑定 ONLINE Dataset：" + datasetId);
    }
    if (detail.currentVersion() == null) {
      throw new IllegalArgumentException("Analysis 绑定的 Dataset 尚无当前版本：" + datasetId);
    }

    Set<String> available = new HashSet<>();
    detail.fields().forEach(field -> available.add(field.fieldId()));
    if (fieldIds == null) {
      return;
    }
    for (String value : fieldIds) {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("Analysis fieldId 不能为空");
      }
      String fieldId = value.trim();
      if (!available.contains(fieldId)) {
        throw new IllegalArgumentException("Analysis 字段不属于 Dataset 当前 schema：" + fieldId);
      }
    }
  }
}
