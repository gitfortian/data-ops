package io.yak.ops.business.dataset.controller.v1.converter;

import io.yak.ops.business.dataset.DatasetFilter;
import io.yak.ops.business.dataset.DatasetMetricBinding;
import io.yak.ops.business.dataset.DatasetQueryRequest;
import io.yak.ops.business.dataset.DatasetService;
import io.yak.ops.business.dataset.DatasetSort;
import io.yak.ops.business.dataset.controller.v1.dto.DatasetRequests.DatasetFieldRequest;
import io.yak.ops.business.dataset.controller.v1.dto.DatasetRequests.PublishDatasetRequest;
import io.yak.ops.business.dataset.controller.v1.dto.DatasetRequests.QueryDatasetRequest;
import java.util.List;
import org.springframework.stereotype.Component;

/** Pure HTTP input -> application command converter. */
@Component
public class DatasetRequestConverter {

  public DatasetService.PublishCommand publish(PublishDatasetRequest request) {
    return new DatasetService.PublishCommand(
        request.sourceTaskAssetId(), request.name(), request.description(), fields(request.fields()));
  }

  public List<DatasetService.FieldSpec> fields(List<DatasetFieldRequest> fields) {
    if (fields == null || fields.isEmpty()) return List.of();
    return fields.stream()
        .map(field -> new DatasetService.FieldSpec(
            field.fieldId(), field.physicalName(), field.displayName(), field.dataType(),
            field.nullable(), field.description(), field.defaultRole()))
        .toList();
  }

  public DatasetQueryRequest query(QueryDatasetRequest request) {
    if (request == null) return null;
    List<DatasetMetricBinding> metrics = request.metrics() == null ? List.of() : request.metrics().stream()
        .map(value -> new DatasetMetricBinding(value.fieldId(), value.aggregation())).toList();
    List<DatasetFilter> filters = request.filters() == null ? List.of() : request.filters().stream()
        .map(value -> new DatasetFilter(value.fieldId(), value.operator(), value.value(), value.values())).toList();
    List<DatasetSort> sorts = request.sorts() == null ? List.of() : request.sorts().stream()
        .map(value -> new DatasetSort(value.fieldId(), value.aggregation(), value.direction())).toList();
    return new DatasetQueryRequest(request.versionNo(), request.dimensions(), metrics, filters, sorts,
        request.limit(), request.timeoutSeconds());
  }
}
