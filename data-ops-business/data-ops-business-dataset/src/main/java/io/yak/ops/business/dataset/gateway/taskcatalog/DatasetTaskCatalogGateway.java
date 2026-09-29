package io.yak.ops.business.dataset.gateway.taskcatalog;

/** Dataset-owned view of the Task Catalog facts required by publication and query runtime. */
public interface DatasetTaskCatalogGateway {

  DatasetTaskAssetSnapshot get(long assetId);

  DatasetTaskRevisionSnapshot resolveRevision(long assetId, long revisionId);

  enum SourceOrigin {
    DATA_DEVELOPMENT,
    OTHER
  }

  enum SourceAvailability {
    ONLINE,
    NOT_ONLINE
  }

  record DatasetTaskAssetSnapshot(
      long id,
      String name,
      String sourceRef,
      SourceOrigin sourceOrigin,
      SourceAvailability availability,
      String taskType,
      long currentRevisionId,
      int currentRevisionNo) {}

  record DatasetTaskRevisionSnapshot(
      long assetId,
      long revisionId,
      int revisionNo,
      String taskType,
      String content,
      String configJson) {}
}
