package io.yak.ops.business.dashboard.gateway.dataset;

/** Dashboard-owned port for proving Dataset references inside the current Project. */
public interface DashboardDatasetGateway {

  void requireExists(long datasetId);
}
