package io.yak.ops.business.dataset.lineage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataset.DatasetSourceType;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.DatasetTaskAssetSnapshot;
import io.yak.ops.business.dataset.gateway.taskcatalog.DatasetTaskCatalogGateway.DatasetTaskRevisionSnapshot;
import org.springframework.stereotype.Component;

/** Resolves one immutable DatasetVersion to the exact source snapshot used for derived lineage. */
@Component
public class DatasetLineageSourceResolver {

  private final DatasetTaskCatalogGateway taskCatalogGateway;
  private final ObjectMapper objectMapper;

  public DatasetLineageSourceResolver(
      DatasetTaskCatalogGateway taskCatalogGateway, ObjectMapper objectMapper) {
    this.taskCatalogGateway = taskCatalogGateway;
    this.objectMapper = objectMapper;
  }

  public ResolvedSource resolve(DatasetVersion version) {
    if (version.sourceType() == DatasetSourceType.SQL_QUERY) {
      if (version.dataSourceId() == null || version.dataSourceId().isBlank()) {
        throw new IllegalStateException("SQL_QUERY DatasetVersion 缺少 dataSourceId");
      }
      if (version.sql() == null || version.sql().isBlank()) {
        throw new IllegalStateException("SQL_QUERY DatasetVersion 缺少 SQL snapshot");
      }
      return new ResolvedSource(
          DatasetSourceType.SQL_QUERY,
          version.dataSourceId().trim(),
          version.sql(),
          null,
          null,
          null);
    }

    if (version.sourceType() == DatasetSourceType.QUERY_REVISION) {
      DatasetTaskAssetSnapshot asset = taskCatalogGateway.get(version.sourceTaskAssetId());
      DatasetTaskRevisionSnapshot revision =
          taskCatalogGateway.resolveRevision(
              version.sourceTaskAssetId(), version.sourceTaskRevisionId());
      if (revision.revisionId() != version.sourceTaskRevisionId()) {
        throw new IllegalStateException(
            "DatasetVersion source revisionId 不一致：dataset="
                + version.sourceTaskRevisionId()
                + ", resolved="
                + revision.revisionId());
      }
      if (revision.revisionNo() != version.sourceTaskRevisionNo()) {
        throw new IllegalStateException(
            "DatasetVersion source revisionNo 不一致：dataset="
                + version.sourceTaskRevisionNo()
                + ", resolved="
                + revision.revisionNo());
      }
      if (!"SQL".equalsIgnoreCase(revision.taskType())) {
        throw new IllegalStateException(
            "Dataset QUERY_REVISION 来源不是 SQL：" + revision.taskType());
      }
      return new ResolvedSource(
          DatasetSourceType.QUERY_REVISION,
          dataSourceId(revision.configJson()),
          revision.content(),
          asset,
          revision,
          null);
    }

    return new ResolvedSource(version.sourceType(), null, null, null, null, null);
  }

  public ResolvedSource failed(DatasetSourceType sourceType, String error) {
    return new ResolvedSource(sourceType, null, null, null, null, error);
  }

  private String dataSourceId(String configJson) {
    try {
      JsonNode root =
          objectMapper.readTree(configJson == null || configJson.isBlank() ? "{}" : configJson);
      JsonNode value = root == null ? null : root.get("dataSourceId");
      String dataSourceId = value == null || value.isNull() ? null : value.asText();
      if (dataSourceId == null || dataSourceId.isBlank()) {
        throw new IllegalArgumentException("Dataset source SQL dataSourceId 不能为空");
      }
      return dataSourceId.trim();
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Dataset source SQL configJson 不是合法 JSON", exception);
    }
  }

  public record ResolvedSource(
      DatasetSourceType sourceType,
      String dataSourceId,
      String sql,
      DatasetTaskAssetSnapshot taskAsset,
      DatasetTaskRevisionSnapshot taskRevision,
      String error) {}
}
