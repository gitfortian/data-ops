package io.yak.ops.business.dataset;

import io.yak.ops.business.dataset.definition.DatasetBindingPolicy;
import io.yak.ops.business.dataset.definition.DatasetManager;
import io.yak.ops.business.dataset.definition.DatasetOverviewReader;
import io.yak.ops.business.dataset.definition.DatasetReader;
import io.yak.ops.business.dataset.development.DevelopmentDatasetManager;
import io.yak.ops.business.dataset.publication.DatasetPublishCommand;
import io.yak.ops.business.dataset.publication.DatasetPublisher;
import io.yak.ops.business.dataset.schema.DatasetFieldSpec;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Stable Dataset application facade retained for HTTP and cross-module callers. */
@Service
public class DatasetService {

  private final DatasetReader reader;
  private final DatasetOverviewReader overviewReader;
  private final DatasetManager manager;
  private final DatasetPublisher publisher;
  private final DatasetBindingPolicy bindingPolicy;
  private final DevelopmentDatasetManager developmentManager;

  public DatasetService(
      DatasetReader reader,
      DatasetOverviewReader overviewReader,
      DatasetManager manager,
      DatasetPublisher publisher,
      DatasetBindingPolicy bindingPolicy,
      DevelopmentDatasetManager developmentManager) {
    this.reader = reader;
    this.overviewReader = overviewReader;
    this.manager = manager;
    this.publisher = publisher;
    this.bindingPolicy = bindingPolicy;
    this.developmentManager = developmentManager;
  }

  public DatasetDetail publish(PublishCommand command) {
    return publisher.publish(toPublishCommand(command));
  }

  public DatasetDetail publishFromRelease(PublishCommand command) {
    return publisher.publishFromRelease(toPublishCommand(command));
  }

  public DatasetDetail saveForDevelopmentNode(long developmentNodeId, PublishCommand command) {
    return developmentManager.saveTaskAsset(developmentNodeId, toPublishCommand(command));
  }

  public List<FieldSpec> previewReleaseFields(long sourceTaskAssetId) {
    return publisher.previewReleaseFields(sourceTaskAssetId).stream()
        .map(DatasetService::fromFieldSpec)
        .toList();
  }

  public DatasetDetail createVersion(long datasetId, List<FieldSpec> fields) {
    return publisher.createVersion(datasetId, toFieldSpecs(fields));
  }

  public List<Dataset> list() {
    return reader.list();
  }

  public List<DatasetCatalogEntry> catalog(Collection<Long> datasetIds) {
    return reader.catalog(datasetIds);
  }

  public List<DatasetCatalogEntry> catalog(Collection<Long> datasetIds, boolean onlineOnly) {
    return reader.catalog(datasetIds, onlineOnly);
  }

  public DatasetOverviewSnapshot overview(Instant from, Instant to, int listLimit) {
    return overviewReader.overview(from, to, listLimit);
  }

  public DatasetDetail get(long datasetId) {
    return reader.require(datasetId);
  }

  public Optional<DatasetDetail> findBySourceTaskAssetId(long sourceTaskAssetId) {
    return reader.findBySourceTaskAssetId(sourceTaskAssetId);
  }

  public Optional<DatasetDetail> findByDevelopmentNodeId(long developmentNodeId) {
    return reader.findByDevelopmentNodeId(developmentNodeId);
  }

  public void validateAnalysisBinding(long datasetId, Collection<String> fieldIds) {
    bindingPolicy.validateAnalysisBinding(datasetId, fieldIds);
  }

  public DatasetDetail online(long datasetId) {
    return manager.online(datasetId);
  }

  public DatasetDetail offline(long datasetId) {
    return manager.offline(datasetId);
  }

  private static DatasetPublishCommand toPublishCommand(PublishCommand command) {
    if (command == null) {
      throw new NullPointerException("command");
    }
    return new DatasetPublishCommand(
        command.sourceTaskAssetId(),
        command.name(),
        command.description(),
        toFieldSpecs(command.fields()));
  }

  private static List<DatasetFieldSpec> toFieldSpecs(List<FieldSpec> fields) {
    if (fields == null || fields.isEmpty()) {
      return List.of();
    }
    return fields.stream().map(DatasetService::toFieldSpec).toList();
  }

  private static DatasetFieldSpec toFieldSpec(FieldSpec field) {
    if (field == null) {
      throw new IllegalArgumentException("Dataset 字段不能为空");
    }
    return new DatasetFieldSpec(
        field.fieldId(),
        field.physicalName(),
        field.displayName(),
        field.dataType(),
        field.nullable(),
        field.description(),
        field.defaultRole());
  }

  private static FieldSpec fromFieldSpec(DatasetFieldSpec field) {
    return new FieldSpec(
        field.fieldId(),
        field.physicalName(),
        field.displayName(),
        field.dataType(),
        field.nullable(),
        field.description(),
        field.defaultRole());
  }

  /** Stable compatibility command used by existing HTTP/release callers. */
  public record PublishCommand(
      long sourceTaskAssetId,
      String name,
      String description,
      List<FieldSpec> fields) {}

  /** Stable compatibility field command; internal schema roles use DatasetFieldSpec. */
  public record FieldSpec(
      String fieldId,
      String physicalName,
      String displayName,
      DatasetFieldDataType dataType,
      boolean nullable,
      String description,
      DatasetFieldRole defaultRole) {}
}
