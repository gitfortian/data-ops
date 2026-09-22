package io.yak.ops.business.analysis.gateway.dataset;

import java.util.Collection;

/** Analysis-owned port for validating a reusable definition against Dataset truth. */
public interface AnalysisDatasetGateway {

  void requireBindable(long datasetId, Collection<String> fieldIds);
}
