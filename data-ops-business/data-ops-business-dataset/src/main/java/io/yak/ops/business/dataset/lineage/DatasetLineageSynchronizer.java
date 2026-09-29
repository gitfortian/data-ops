package io.yak.ops.business.dataset.lineage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetDetail;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetVersion;
import io.yak.ops.business.dataset.gateway.lineage.DatasetLineageGraphGateway;
import io.yak.ops.business.dataset.gateway.lineage.DatasetLineageGraphGateway.Asset;
import io.yak.ops.business.dataset.gateway.lineage.DatasetLineageGraphGateway.AssetSpec;
import io.yak.ops.business.dataset.gateway.lineage.DatasetLineageGraphGateway.AssetType;
import io.yak.ops.business.dataset.gateway.lineage.DatasetLineageGraphGateway.RelationSpec;
import io.yak.ops.business.dataset.gateway.lineage.DatasetLineageGraphGateway.RelationType;
import io.yak.ops.business.dataset.gateway.lineage.DatasetProjectionAnalyzerGateway;
import io.yak.ops.business.dataset.gateway.lineage.DatasetProjectionAnalyzerGateway.ProjectionMapping;
import io.yak.ops.business.dataset.gateway.lineage.DatasetProjectionAnalyzerGateway.ProjectionResult;
import io.yak.ops.business.dataset.gateway.lineage.DatasetProjectionAnalyzerGateway.TableRef;
import io.yak.ops.business.dataset.lineage.DatasetLineageSourceResolver.ResolvedSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Builds derived Dataset/DatasetField lineage from the immutable current DatasetVersion snapshot. */
@Component
public class DatasetLineageSynchronizer {

  public static final String EVIDENCE_SOURCE_TYPE = "DATASET_SOURCE_PARSE";

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DatasetLineageSynchronizer.class);
  private static final int MAX_EXPRESSION_LENGTH = 16_000;

  private final DatasetLineageGraphGateway lineageGateway;
  private final DatasetProjectionAnalyzerGateway projectionAnalyzer;
  private final DatasetLineageSourceResolver sourceResolver;
  private final ObjectMapper objectMapper;

  public DatasetLineageSynchronizer(
      DatasetLineageGraphGateway lineageGateway,
      DatasetProjectionAnalyzerGateway projectionAnalyzer,
      DatasetLineageSourceResolver sourceResolver,
      ObjectMapper objectMapper) {
    this.lineageGateway = lineageGateway;
    this.projectionAnalyzer = projectionAnalyzer;
    this.sourceResolver = sourceResolver;
    this.objectMapper = objectMapper;
  }

  public void syncCurrent(DatasetDetail detail) {
    if (detail == null || detail.dataset() == null) {
      return;
    }

    Dataset dataset = detail.dataset();
    String evidenceId = String.valueOf(dataset.id());
    lineageGateway.clearRelationsByEvidence(EVIDENCE_SOURCE_TYPE, evidenceId);

    DatasetVersion version = detail.currentVersion();
    if (version == null) {
      registerDatasetAsset(dataset, null, null, "SKIPPED", null, null, 0);
      return;
    }

    ResolvedSource source;
    try {
      source = sourceResolver.resolve(version);
    } catch (RuntimeException exception) {
      source = sourceResolver.failed(version.sourceType(), safeMessage(exception));
    }

    ProjectionResult projection = null;
    String parseStatus;
    String parseError = source.error();
    if (parseError != null) {
      parseStatus = "FAILED";
    } else if (source.sql() == null || source.sql().isBlank()) {
      parseStatus = "SKIPPED";
    } else {
      try {
        DatasetProjectionAnalyzerGateway.Analysis analysis =
            projectionAnalyzer.analyze(source.dataSourceId(), source.sql());
        if (!analysis.available()) {
          parseStatus = "SKIPPED";
          parseError = "SqlProjectionLineageAnalyzer is not available";
        } else {
          projection = analysis.result();
          parseStatus =
              projection.unresolvedReferenceCount() > 0
                  ? (projection.mappings().isEmpty() ? "UNRESOLVED" : "PARTIAL")
                  : "SUCCESS";
        }
      } catch (RuntimeException exception) {
        parseStatus = "FAILED";
        parseError = safeMessage(exception);
        LOGGER.warn(
            "Failed to analyze Dataset lineage for dataset {} version {}: {}",
            dataset.id(),
            version.versionNo(),
            parseError);
      }
    }

    Map<String, DatasetField> fieldsByPhysicalName = new LinkedHashMap<>();
    for (DatasetField field : detail.fields()) {
      if (field != null && field.physicalName() != null) {
        fieldsByPhysicalName.put(
            field.physicalName().toLowerCase(Locale.ROOT), field);
      }
    }

    int unmatchedMappings = unmatchedMappings(projection, fieldsByPhysicalName);
    if (unmatchedMappings > 0 && "SUCCESS".equals(parseStatus)) {
      parseStatus = "PARTIAL";
    }

    Asset datasetAsset =
        registerDatasetAsset(
            dataset, version, source, parseStatus, parseError, projection, unmatchedMappings);
    Instant observedAt = version.createTime() == null ? Instant.now() : version.createTime();

    Map<String, Asset> fieldAssets = new LinkedHashMap<>();
    for (DatasetField field : detail.fields()) {
      Asset fieldAsset =
          registerDatasetFieldAsset(datasetAsset, dataset, version, source.dataSourceId(), field);
      fieldAssets.put(field.fieldId(), fieldAsset);
      lineageGateway.registerRelation(
          new RelationSpec(
              fieldAsset.id(),
              datasetAsset.id(),
              RelationType.CONTAINS,
              EVIDENCE_SOURCE_TYPE,
              evidenceId,
              null,
              BigDecimal.ONE,
              relationVersion(version, "field:" + field.sortOrder()),
              observedAt,
              fieldContainmentProperties(version, field)));
    }

    if (source.taskAsset() != null) {
      Asset task = resolveSqlTaskAsset(source, version);
      lineageGateway.registerRelation(
          new RelationSpec(
              task.id(),
              datasetAsset.id(),
              RelationType.DERIVES_FROM,
              EVIDENCE_SOURCE_TYPE,
              evidenceId,
              null,
              BigDecimal.ONE,
              relationVersion(version, "source-task"),
              observedAt,
              sourceTaskProperties(version, source)));
    }

    if (projection == null) {
      return;
    }

    registerProjectionRelations(
        dataset,
        version,
        source,
        datasetAsset,
        fieldAssets,
        fieldsByPhysicalName,
        projection,
        evidenceId,
        observedAt);
  }

  private int unmatchedMappings(
      ProjectionResult projection, Map<String, DatasetField> fieldsByPhysicalName) {
    if (projection == null) {
      return 0;
    }
    int unmatched = 0;
    for (ProjectionMapping mapping : projection.mappings()) {
      if (!fieldsByPhysicalName.containsKey(
          mapping.outputColumnName().toLowerCase(Locale.ROOT))) {
        unmatched++;
      }
    }
    return unmatched;
  }

  private void registerProjectionRelations(
      Dataset dataset,
      DatasetVersion version,
      ResolvedSource source,
      Asset datasetAsset,
      Map<String, Asset> fieldAssets,
      Map<String, DatasetField> fieldsByPhysicalName,
      ProjectionResult projection,
      String evidenceId,
      Instant observedAt) {
    Map<String, Asset> tableAssets = new LinkedHashMap<>();
    Set<String> tableRelations = new LinkedHashSet<>();
    int mappingIndex = 0;
    for (ProjectionMapping mapping : projection.mappings()) {
      mappingIndex++;
      DatasetField field =
          fieldsByPhysicalName.get(
              mapping.outputColumnName().toLowerCase(Locale.ROOT));
      if (field == null) {
        continue;
      }

      Asset table = registerTableAsset(source.dataSourceId(), mapping.sourceTable(), tableAssets);
      if (tableRelations.add(mapping.sourceTable().canonicalName())) {
        lineageGateway.registerRelation(
            new RelationSpec(
                table.id(),
                datasetAsset.id(),
                RelationType.DERIVES_FROM,
                EVIDENCE_SOURCE_TYPE,
                evidenceId,
                null,
                BigDecimal.ONE,
                relationVersion(version, "table:" + tableRelations.size()),
                observedAt,
                tableRelationProperties(version, source, mapping.sourceTable())));
      }

      Asset sourceColumn =
          registerColumnAsset(
              source.dataSourceId(),
              table,
              mapping.sourceTable(),
              mapping.sourceColumnName());
      Asset targetField = fieldAssets.get(field.fieldId());
      lineageGateway.registerRelation(
          new RelationSpec(
              sourceColumn.id(),
              targetField.id(),
              RelationType.DERIVES_FROM,
              EVIDENCE_SOURCE_TYPE,
              evidenceId,
              truncate(mapping.expression(), MAX_EXPRESSION_LENGTH),
              BigDecimal.ONE,
              relationVersion(
                  version,
                  "column:"
                      + mapping.outputOrdinal()
                      + ":"
                      + mapping.sourceOrdinal()
                      + ":"
                      + mappingIndex),
              observedAt,
              columnRelationProperties(version, source, field, mapping)));
    }
  }

  private Asset registerDatasetAsset(
      Dataset dataset,
      DatasetVersion version,
      ResolvedSource source,
      String parseStatus,
      String parseError,
      ProjectionResult projection,
      int unmatchedMappings) {
    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("status", dataset.status().name());
    if (dataset.currentVersionId() != null) {
      properties.put("currentVersionId", String.valueOf(dataset.currentVersionId()));
    }
    properties.put("lineageParseStatus", parseStatus);
    if (parseError != null && !parseError.isBlank()) {
      properties.put("lineageParseError", truncate(parseError, 1000));
    }
    if (version != null) {
      putVersionProperties(properties, version);
    }
    if (source != null && source.dataSourceId() != null) {
      properties.put("dataSourceId", source.dataSourceId());
    }
    if (projection != null) {
      properties.put("candidateOutputColumnCount", projection.candidateOutputCount());
      properties.put("columnMappingCount", projection.mappings().size());
      properties.put(
          "unresolvedColumnReferenceCount", projection.unresolvedReferenceCount());
      properties.put("unmatchedOutputMappingCount", unmatchedMappings);
    }

    return lineageGateway.registerAsset(
        new AssetSpec(
            datasetAssetKey(dataset.id()),
            AssetType.DATASET,
            dataset.name(),
            "DATASET",
            String.valueOf(dataset.id()),
            null,
            source == null ? null : source.dataSourceId(),
            null,
            null,
            null,
            null,
            properties));
  }

  private Asset registerDatasetFieldAsset(
      Asset datasetAsset,
      Dataset dataset,
      DatasetVersion version,
      String dataSourceId,
      DatasetField field) {
    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("datasetId", String.valueOf(dataset.id()));
    properties.put("datasetVersionId", String.valueOf(version.id()));
    properties.put("datasetVersionNo", version.versionNo());
    properties.put("fieldId", field.fieldId());
    properties.put("physicalName", field.physicalName());
    properties.put("displayName", field.displayName());
    properties.put("dataType", field.dataType().name());
    properties.put("nullable", field.nullable());
    properties.put("defaultRole", field.defaultRole().name());
    properties.put("sortOrder", field.sortOrder());
    if (field.description() != null) {
      properties.put("description", field.description());
    }

    return lineageGateway.registerAsset(
        new AssetSpec(
            datasetFieldAssetKey(dataset.id(), field.fieldId()),
            AssetType.DATASET_FIELD,
            field.displayName(),
            "DATASET",
            dataset.id() + ":" + field.fieldId(),
            datasetAsset.id(),
            dataSourceId,
            null,
            null,
            null,
            null,
            properties));
  }

  private Asset resolveSqlTaskAsset(ResolvedSource source, DatasetVersion version) {
    String sourceRef = source.taskAsset().sourceRef();
    String key = "sql-task:data-development:" + sourceRef;
    try {
      return lineageGateway.requireAssetByKey(key);
    } catch (IllegalArgumentException missing) {
      ObjectNode properties = objectMapper.createObjectNode();
      properties.put("taskAssetId", String.valueOf(source.taskAsset().id()));
      properties.put(
          "sourceTaskRevisionId", String.valueOf(version.sourceTaskRevisionId()));
      properties.put("sourceTaskRevisionNo", version.sourceTaskRevisionNo());
      properties.put("lineageRegistration", "DATASET_FALLBACK");
      return lineageGateway.registerAsset(
          new AssetSpec(
              key,
              AssetType.SQL_TASK,
              source.taskAsset().name(),
              "DATA_DEVELOPMENT",
              sourceRef,
              null,
              source.dataSourceId(),
              null,
              null,
              null,
              null,
              properties));
    }
  }

  private Asset registerTableAsset(
      String dataSourceId, TableRef table, Map<String, Asset> cache) {
    String key = tableAssetKey(dataSourceId, table);
    Asset cached = cache.get(key);
    if (cached != null) {
      return cached;
    }

    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("qualifiedName", table.qualifiedName());
    Asset registered =
        lineageGateway.registerAsset(
            new AssetSpec(
                key,
                AssetType.TABLE,
                table.qualifiedName(),
                "DATASOURCE",
                dataSourceId,
                null,
                dataSourceId,
                table.databaseName(),
                table.schemaName(),
                table.tableName(),
                null,
                properties));
    cache.put(key, registered);
    return registered;
  }

  private Asset registerColumnAsset(
      String dataSourceId, Asset tableAsset, TableRef table, String columnName) {
    ObjectNode properties = objectMapper.createObjectNode();
    properties.put("qualifiedName", table.qualifiedName() + "." + columnName);
    return lineageGateway.registerAsset(
        new AssetSpec(
            columnAssetKey(dataSourceId, table, columnName),
            AssetType.COLUMN,
            columnName,
            "DATASOURCE",
            dataSourceId,
            tableAsset.id(),
            dataSourceId,
            table.databaseName(),
            table.schemaName(),
            table.tableName(),
            columnName,
            properties));
  }

  private JsonNode fieldContainmentProperties(DatasetVersion version, DatasetField field) {
    ObjectNode properties = versionProperties(version);
    properties.put("lineageLevel", "STRUCTURE");
    properties.put("fieldId", field.fieldId());
    properties.put("physicalName", field.physicalName());
    return properties;
  }

  private JsonNode sourceTaskProperties(DatasetVersion version, ResolvedSource source) {
    ObjectNode properties = versionProperties(version);
    properties.put("lineageLevel", "DATASET");
    properties.put("sourceKind", "QUERY_REVISION");
    properties.put("taskAssetId", String.valueOf(source.taskAsset().id()));
    properties.put("taskSourceRef", source.taskAsset().sourceRef());
    return properties;
  }

  private JsonNode tableRelationProperties(
      DatasetVersion version, ResolvedSource source, TableRef table) {
    ObjectNode properties = versionProperties(version);
    properties.put("lineageLevel", "TABLE");
    properties.put("sourceKind", source.sourceType().name());
    properties.put("sourceTable", table.qualifiedName());
    return properties;
  }

  private JsonNode columnRelationProperties(
      DatasetVersion version,
      ResolvedSource source,
      DatasetField field,
      ProjectionMapping mapping) {
    ObjectNode properties = versionProperties(version);
    properties.put("lineageLevel", "COLUMN");
    properties.put("sourceKind", source.sourceType().name());
    properties.put("fieldId", field.fieldId());
    properties.put("outputColumn", mapping.outputColumnName());
    properties.put("mappingKind", mapping.mappingKind().name());
    properties.put("outputOrdinal", mapping.outputOrdinal());
    properties.put("sourceOrdinal", mapping.sourceOrdinal());
    properties.put("sourceTable", mapping.sourceTable().qualifiedName());
    properties.put("sourceColumn", mapping.sourceColumnName());
    return properties;
  }

  private ObjectNode versionProperties(DatasetVersion version) {
    ObjectNode properties = objectMapper.createObjectNode();
    putVersionProperties(properties, version);
    return properties;
  }

  private void putVersionProperties(ObjectNode properties, DatasetVersion version) {
    properties.put("datasetVersionId", String.valueOf(version.id()));
    properties.put("datasetVersionNo", version.versionNo());
    properties.put("sourceType", version.sourceType().name());
    properties.put("sourceTaskAssetId", String.valueOf(version.sourceTaskAssetId()));
    properties.put(
        "sourceTaskRevisionId", String.valueOf(version.sourceTaskRevisionId()));
    properties.put("sourceTaskRevisionNo", version.sourceTaskRevisionNo());
  }

  private static String tableAssetKey(String dataSourceId, TableRef table) {
    return "table:" + dataSourceId + ":" + table.canonicalName();
  }

  private static String columnAssetKey(
      String dataSourceId, TableRef table, String columnName) {
    return "column:"
        + dataSourceId
        + ":"
        + table.canonicalName()
        + "."
        + columnName.toLowerCase(Locale.ROOT);
  }

  /** DATASET 资产键全仓唯一出处(asset 台账与血缘同源,D6;禁止在别处复制字面量)。 */
  public static String datasetAssetKey(long datasetId) {
    return "dataset:" + datasetId;
  }

  /** 数据集字段资产键全仓唯一出处。 */
  public static String datasetFieldAssetKey(long datasetId, String fieldId) {
    return "dataset-field:" + datasetId + ":" + fieldId;
  }

  private static String relationVersion(DatasetVersion version, String suffix) {
    return "dataset-v" + version.versionNo() + ":" + suffix;
  }

  private static String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  private static String safeMessage(Throwable throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable == null ? "unknown lineage error" : throwable.getClass().getSimpleName();
    }
    return message.length() > 1000 ? message.substring(0, 1000) : message;
  }
}
