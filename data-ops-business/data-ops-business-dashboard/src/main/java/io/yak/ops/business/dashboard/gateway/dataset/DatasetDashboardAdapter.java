package io.yak.ops.business.dashboard.gateway.dataset;

import io.yak.ops.business.dataset.definition.DatasetReader;
import org.springframework.stereotype.Component;

/** Adapts Dataset's project-scoped read contract to Dashboard. */
@Component
public class DatasetDashboardAdapter implements DashboardDatasetGateway {

  private final DatasetReader reader;

  public DatasetDashboardAdapter(DatasetReader reader) {
    this.reader = reader;
  }

  @Override
  public void requireExists(long datasetId) {
    reader.require(datasetId);
  }
}
