package io.yak.ops.business.dataset.definition;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetDetail;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.lineage.DatasetLineageRefreshPublisher;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Owns Dataset identity lifecycle that is independent from immutable version publication. */
@Component
public class DatasetManager {

  private final DatasetRepository repository;
  private final DatasetReader reader;
  private final DatasetLineageRefreshPublisher lineagePublisher;

  public DatasetManager(
      DatasetRepository repository,
      DatasetReader reader,
      DatasetLineageRefreshPublisher lineagePublisher) {
    this.repository = repository;
    this.reader = reader;
    this.lineagePublisher = lineagePublisher;
  }

  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail online(long datasetId) {
    Dataset dataset = reader.require(datasetId).dataset();
    if (dataset.status() != DatasetStatus.ONLINE) {
      repository.updateStatus(datasetId, DatasetStatus.ONLINE);
      lineagePublisher.request(datasetId);
    }
    return reader.require(datasetId);
  }

  @Transactional("yakBusinessTransactionManager")
  public DatasetDetail offline(long datasetId) {
    Dataset dataset = reader.require(datasetId).dataset();
    if (dataset.status() != DatasetStatus.OFFLINE) {
      repository.updateStatus(datasetId, DatasetStatus.OFFLINE);
      lineagePublisher.request(datasetId);
    }
    return reader.require(datasetId);
  }
}
