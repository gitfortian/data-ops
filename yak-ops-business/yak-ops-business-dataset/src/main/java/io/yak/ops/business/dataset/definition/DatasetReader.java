package io.yak.ops.business.dataset.definition;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetCatalogEntry;
import io.yak.ops.business.dataset.DatasetDetail;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-side access to Dataset identity, immutable versions and current schema. */
@Component
public class DatasetReader {

  private static final int MAX_CATALOG_DATASET_IDS = 500;

  private final DatasetRepository repository;

  public DatasetReader(DatasetRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public List<Dataset> list() {
    return repository.listDatasets();
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public List<DatasetCatalogEntry> catalog(Collection<Long> datasetIds) {
    return catalog(datasetIds, false);
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public List<DatasetCatalogEntry> catalog(Collection<Long> datasetIds, boolean onlineOnly) {
    List<Long> requestedIds = normalizeCatalogDatasetIds(datasetIds);
    List<Dataset> datasets = requestedIds.isEmpty()
        ? repository.listDatasets()
        : repository.listDatasetsByIds(requestedIds);
    if (onlineOnly) {
      datasets = datasets.stream()
          .filter(dataset -> dataset.status() == DatasetStatus.ONLINE)
          .toList();
    }
    if (datasets.isEmpty()) {
      return List.of();
    }

    List<Long> currentVersionIds = datasets.stream()
        .map(Dataset::currentVersionId)
        .filter(value -> value != null)
        .distinct()
        .toList();
    if (currentVersionIds.isEmpty()) {
      return datasets.stream()
          .map(dataset -> new DatasetCatalogEntry(dataset, null, List.of()))
          .toList();
    }

    Map<Long, DatasetVersion> versionsById = repository.listVersionsByIds(currentVersionIds).stream()
        .collect(Collectors.toMap(DatasetVersion::id, Function.identity()));
    Map<Long, List<DatasetField>> fieldsByVersionId =
        repository.listFieldsByVersionIds(currentVersionIds).stream()
            .collect(Collectors.groupingBy(
                DatasetField::versionId,
                LinkedHashMap::new,
                Collectors.toList()));

    return datasets.stream()
        .map(dataset -> catalogEntry(dataset, versionsById, fieldsByVersionId))
        .toList();
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public DatasetDetail require(long datasetId) {
    if (datasetId <= 0L) {
      throw new IllegalArgumentException("datasetId 必须大于 0");
    }
    Dataset dataset =
        repository
            .findDataset(datasetId)
            .orElseThrow(() -> new IllegalArgumentException("Dataset 不存在：" + datasetId));

    DatasetVersion currentVersion = null;
    List<DatasetField> fields = List.of();
    if (dataset.currentVersionId() != null) {
      currentVersion =
          repository
              .findVersion(dataset.currentVersionId())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Dataset 当前版本不存在：datasetId="
                              + datasetId
                              + ", versionId="
                              + dataset.currentVersionId()));
      fields = repository.listFields(currentVersion.id());
    }
    return new DatasetDetail(dataset, currentVersion, repository.listVersions(datasetId), fields);
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public Optional<DatasetDetail> findBySourceTaskAssetId(long sourceTaskAssetId) {
    if (sourceTaskAssetId <= 0L) {
      throw new IllegalArgumentException("sourceTaskAssetId 必须大于 0");
    }
    return repository.findDatasetBySourceTaskAssetId(sourceTaskAssetId).map(value -> require(value.id()));
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public Optional<DatasetDetail> findByDevelopmentNodeId(long developmentNodeId) {
    if (developmentNodeId <= 0L) {
      throw new IllegalArgumentException("developmentNodeId 必须大于 0");
    }
    return repository.findDatasetByDevelopmentNodeId(developmentNodeId).map(value -> require(value.id()));
  }

  private DatasetCatalogEntry catalogEntry(
      Dataset dataset,
      Map<Long, DatasetVersion> versionsById,
      Map<Long, List<DatasetField>> fieldsByVersionId) {
    Long versionId = dataset.currentVersionId();
    if (versionId == null) {
      return new DatasetCatalogEntry(dataset, null, List.of());
    }
    DatasetVersion version = versionsById.get(versionId);
    if (version == null) {
      throw new IllegalStateException(
          "Dataset 当前版本不存在：datasetId=" + dataset.id() + ", versionId=" + versionId);
    }
    return new DatasetCatalogEntry(
        dataset,
        version,
        fieldsByVersionId.getOrDefault(versionId, List.of()));
  }

  private List<Long> normalizeCatalogDatasetIds(Collection<Long> datasetIds) {
    if (datasetIds == null || datasetIds.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<Long> normalized = new LinkedHashSet<>();
    for (Long datasetId : datasetIds) {
      if (datasetId == null || datasetId <= 0L) {
        throw new IllegalArgumentException("datasetIds 必须全部大于 0");
      }
      normalized.add(datasetId);
      if (normalized.size() > MAX_CATALOG_DATASET_IDS) {
        throw new IllegalArgumentException("datasetIds 最多支持 500 个");
      }
    }
    return List.copyOf(normalized);
  }
}
