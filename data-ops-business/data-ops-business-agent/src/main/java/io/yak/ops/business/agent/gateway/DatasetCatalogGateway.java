package io.yak.ops.business.agent.gateway;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.yak.ops.business.agent.domain.DatasetSummary;
import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetService;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 数据集目录出站网关：dataset 公共契约的唯一消费点，对上暴露 domain 值对象。 */
@ConditionalOnAgentEnabled
@Component
@RequiredArgsConstructor
public class DatasetCatalogGateway {

  private final DatasetService datasetService;
  private final ActionAuthorization authorization;

  public List<DatasetSummary> listOnlineDatasets() {
    authorization.requirePermission("data-development:read");
    return datasetService.list().stream()
        .filter(dataset -> dataset.status() == DatasetStatus.ONLINE)
        .limit(50)
        .map(DatasetCatalogGateway::toSummary)
        .toList();
  }

  public List<DatasetSummary.FieldView> listFields(long datasetId) {
    authorization.requirePermission("data-development:read");
    var detail = datasetService.get(datasetId);
    if (detail.dataset().status() != DatasetStatus.ONLINE) {
      throw new IllegalArgumentException("[DATASET_OFFLINE] 数据集未上线：" + datasetId);
    }
    return detail.fields().stream().map(DatasetCatalogGateway::toFieldView).toList();
  }

  /** 字段清单（含数据集名称），供目录视图格式化。 */
  public DatasetSummary.DatasetFields datasetOverview(long datasetId) {
    authorization.requirePermission("data-development:read");
    var detail = datasetService.get(datasetId);
    if (detail.dataset().status() != DatasetStatus.ONLINE) {
      throw new IllegalArgumentException("[DATASET_OFFLINE] 数据集未上线：" + datasetId);
    }
    return new DatasetSummary.DatasetFields(
        detail.dataset().id(),
        detail.dataset().name(),
        detail.fields().stream().limit(200).map(DatasetCatalogGateway::toFieldView).toList(),
        detail.currentVersion() == null ? null : detail.currentVersion().versionNo(),
        detail.fields().size() > 200);
  }

  private static DatasetSummary toSummary(Dataset dataset) {
    boolean online = dataset.status() == DatasetStatus.ONLINE;
    return new DatasetSummary(dataset.id(), dataset.name(), dataset.description(), online);
  }

  private static DatasetSummary.FieldView toFieldView(DatasetField field) {
    return new DatasetSummary.FieldView(
        field.fieldId(),
        field.displayName(),
        field.dataType() == null ? null : field.dataType().name(),
        field.defaultRole() == null ? null : field.defaultRole().name(),
        field.nullable(),
        field.description());
  }
}
