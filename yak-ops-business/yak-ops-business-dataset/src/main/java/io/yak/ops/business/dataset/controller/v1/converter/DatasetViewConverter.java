package io.yak.ops.business.dataset.controller.v1.converter;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetCatalogEntry;
import io.yak.ops.business.dataset.DatasetDetail;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetQueryPerformance;
import io.yak.ops.business.dataset.DatasetQueryResult;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.controller.v1.vo.DatasetViews.DatasetCatalogVO;
import io.yak.ops.business.dataset.controller.v1.vo.DatasetViews.DatasetDetailVO;
import io.yak.ops.business.dataset.controller.v1.vo.DatasetViews.DatasetFieldVO;
import io.yak.ops.business.dataset.controller.v1.vo.DatasetViews.DatasetQueryBindingVO;
import io.yak.ops.business.dataset.controller.v1.vo.DatasetViews.DatasetQueryColumnVO;
import io.yak.ops.business.dataset.controller.v1.vo.DatasetViews.DatasetQueryPerformanceVO;
import io.yak.ops.business.dataset.controller.v1.vo.DatasetViews.DatasetQueryResultVO;
import io.yak.ops.business.dataset.controller.v1.vo.DatasetViews.DatasetVO;
import io.yak.ops.business.dataset.controller.v1.vo.DatasetViews.DatasetVersionVO;
import java.util.List;
import org.springframework.stereotype.Component;

/** Pure Domain -> HTTP view converter. */
@Component
public class DatasetViewConverter {

  public DatasetVO dataset(Dataset value) {
    if (value == null) return null;
    return new DatasetVO(value.id(), value.name(), value.description(), value.status().name(),
        value.currentVersionId(), value.createTime(), value.updateTime());
  }

  public DatasetVersionVO version(DatasetVersion value) {
    if (value == null) return null;
    return new DatasetVersionVO(value.id(), value.datasetId(), value.versionNo(),
        value.sourceType().name(), value.sourceTaskAssetId(), value.sourceTaskRevisionId(),
        value.sourceTaskRevisionNo(), value.dataSourceId(), value.sql(), value.schemaSnapshot(),
        value.createTime());
  }

  public DatasetFieldVO field(DatasetField value) {
    return new DatasetFieldVO(value.fieldId(), value.versionId(), value.physicalName(),
        value.displayName(), value.dataType().name(), value.nullable(), value.description(),
        value.defaultRole().name(), value.sortOrder());
  }

  public DatasetCatalogVO catalog(DatasetCatalogEntry value) {
    return new DatasetCatalogVO(
        dataset(value.dataset()),
        version(value.currentVersion()),
        value.fields().stream().map(this::field).toList());
  }

  public DatasetDetailVO detail(DatasetDetail value) {
    return new DatasetDetailVO(dataset(value.dataset()), version(value.currentVersion()),
        value.versions().stream().map(this::version).toList(),
        value.fields().stream().map(this::field).toList());
  }

  public DatasetQueryResultVO queryResult(DatasetQueryResult value) {
    List<DatasetQueryBindingVO> bindings = value.bindings().stream()
        .map(binding -> new DatasetQueryBindingVO(binding.key(), binding.fieldId(),
            binding.displayName(), binding.dataType().name(),
            binding.aggregation() == null ? null : binding.aggregation().name()))
        .toList();
    List<DatasetQueryColumnVO> columns = value.columns().stream()
        .map(column -> new DatasetQueryColumnVO(column.name(), column.label(), column.typeName(),
            column.jdbcType(), column.nullable()))
        .toList();
    return new DatasetQueryResultVO(value.queryId(), value.datasetId(), value.datasetVersionId(),
        value.datasetVersionNo(), bindings, columns, value.rows(), value.returnedRows(),
        value.truncated(), value.elapsedMillis());
  }

  public DatasetQueryPerformanceVO performance(DatasetQueryPerformance value) {
    return new DatasetQueryPerformanceVO(
        value.queryId(), value.datasetId(), value.datasetName(), value.datasetVersionId(),
        value.datasetVersionNo(), value.sourceType(), value.dataSourceId(), value.sql(),
        value.sqlHash(), value.status().name(), value.failureStage(), value.errorType(),
        value.errorMessage(), value.waitMillis(), value.prepareMillis(), value.executeMillis(),
        value.transferMillis(), value.totalMillis(), value.returnedRows(), value.truncated(),
        value.startedAt(), value.finishedAt());
  }
}
