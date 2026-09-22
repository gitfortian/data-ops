package io.yak.ops.business.analysis.gateway.dataset;

import io.yak.ops.business.dataset.definition.DatasetBindingPolicy;
import java.util.Collection;
import org.springframework.stereotype.Component;

/** Adapts Dataset's stable binding policy to the Analysis-owned gateway. */
@Component
public class DatasetAnalysisAdapter implements AnalysisDatasetGateway {

  private final DatasetBindingPolicy bindingPolicy;

  public DatasetAnalysisAdapter(DatasetBindingPolicy bindingPolicy) {
    this.bindingPolicy = bindingPolicy;
  }

  @Override
  public void requireBindable(long datasetId, Collection<String> fieldIds) {
    bindingPolicy.validateAnalysisBinding(datasetId, fieldIds);
  }
}
