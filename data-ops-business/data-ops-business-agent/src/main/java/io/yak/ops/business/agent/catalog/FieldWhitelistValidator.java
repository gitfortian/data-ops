package io.yak.ops.business.agent.catalog;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.yak.ops.business.agent.domain.DatasetSummary;
import io.yak.ops.business.agent.gateway.DatasetCatalogGateway;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 字段白名单校验器：模型产出的 datasetId / fieldId 必须全部命中 ONLINE 数据集的真实字段。
 * 校验失败抛出带精确原因的 IllegalArgumentException，由工具层原样回喂模型自纠。
 */
@ConditionalOnAgentEnabled
@Component
@RequiredArgsConstructor
public class FieldWhitelistValidator {

  private final DatasetCatalogGateway catalogGateway;

  public List<DatasetSummary.FieldView> requireFields(long datasetId) {
    return catalogGateway.listFields(datasetId);
  }

  public void requireSnapshot(long datasetId, DatasetSummary.DatasetFields discovery,
      List<String> fieldIds) {
    if (discovery == null || discovery.datasetId() != datasetId || discovery.versionNo() == null) {
      throw new IllegalArgumentException("[DATASET_DISCOVERY_REQUIRED] 请先发现本轮数据集字段版本");
    }
    var current = catalogGateway.datasetOverview(datasetId);
    if (!discovery.versionNo().equals(current.versionNo())) {
      throw new IllegalArgumentException("[DATASET_VERSION_CHANGED] 数据集版本已变化，请重新调用 get_dataset_fields");
    }
    Set<String> known = current.fields().stream().map(DatasetSummary.FieldView::fieldId)
        .collect(java.util.stream.Collectors.toSet());
    if (fieldIds.stream().anyMatch(field -> !known.contains(field))) {
      throw new IllegalArgumentException("[FIELD_WHITELIST_REJECTED] 查询含未发现的字段，请重新确认字段清单");
    }
  }

  /** 校验一批 fieldId 全部存在于该 ONLINE 数据集，否则抛出带精确原因的异常。 */
  public void requireKnownFields(long datasetId, List<String> fieldIds, String usage) {
    if (fieldIds == null || fieldIds.isEmpty()) {
      return;
    }
    Set<String> known = new LinkedHashSet<>();
    for (DatasetSummary.FieldView view : requireFields(datasetId)) {
      known.add(view.fieldId());
    }
    for (String fieldId : fieldIds) {
      if (!known.contains(fieldId)) {
        throw new IllegalArgumentException(
            "[FIELD_WHITELIST_REJECTED] 非法字段引用 [" + usage + "]: '" + fieldId
                + "' 不存在于数据集 " + datasetId + "。请先调用 get_dataset_fields 获取合法 fieldId。");
      }
    }
  }
}
