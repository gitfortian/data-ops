package io.yak.ops.business.metadata.query;

import io.yak.ops.business.metadata.api.EntityDTO;
import io.yak.ops.business.metadata.api.PhysicalScopeEvidenceQueryApi;
import io.yak.ops.core.project.CurrentProject;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * F-039 Metadata-owned catalog-only evidence. Reuses the canonical project-scoped
 * CatalogQueryService; never reaches the physical datasource or reads business data rows.
 *
 * Fail-closed: missing/partial harvest, absent content hash, stale or detached column rows,
 * count disagreement and inconsistent capture runs all prevent an "available" snapshot.
 */
@Service
public class PhysicalScopeEvidenceQueryService implements PhysicalScopeEvidenceQueryApi {
  private static final int MAX_TABLES = 20;
  private static final int MAX_COLUMNS = 500;
  private final CatalogQueryService catalog;
  private final CurrentProject currentProject;

  public PhysicalScopeEvidenceQueryService(CatalogQueryService catalog, CurrentProject currentProject) {
    this.catalog = catalog;
    this.currentProject = currentProject;
  }

  @Override
  public Evidence readSelectedTables(List<String> assetKeys) {
    if (assetKeys == null || assetKeys.isEmpty() || assetKeys.size() > MAX_TABLES
        || assetKeys.stream().anyMatch(k -> k == null || k.isBlank() || k.length() > 512)
        || new HashSet<>(assetKeys).size() != assetKeys.size()) {
      throw new IllegalArgumentException("[F039_INVALID_SOURCE_SELECTION]");
    }
    // Never derive the project from an HTTP payload or a model output.
    long projectId = currentProject.requireProjectId();
    List<String> keys = assetKeys.stream().sorted().toList();
    var tables = new ArrayList<Table>();
    String dataSource = null, database = null, schema = null, capture = null, collectedAt = null;
    int totalColumns = 0;
    for (String key : keys) {
      EntityDTO table = catalog.byAssetKey(key)
          .orElseThrow(() -> blocked("TABLE_NOT_FOUND"));
      if (!"table".equals(table.typeName())
          || !"HARVESTED".equals(table.providerType())
          || !key.equals(table.assetKey())
          || !"METADATA".equals(text(table.facts().get("sourceType")))) {
        throw blocked("NOT_HARVESTED_TABLE");
      }
      String tableSource = required(table.dataSourceId(), "MISSING_SOURCE");
      String tableDatabase = required(table.databaseName(), "MISSING_DATABASE");
      String tableSchema = table.schemaName() == null ? "" : table.schemaName();
      String tableCapture = required(text(table.facts().get("collectJobId")), "MISSING_CAPTURE");
      String tableCollected = required(text(table.facts().get("lastCollectAt")), "MISSING_CAPTURE_TIME");
      String tableHash = required(text(table.facts().get("contentHash")), "PARTIAL_STRUCTURE");
      String tableName = required(table.tableName(), "MISSING_TABLE_COORDINATE");
      if (dataSource == null) {
        dataSource = tableSource;
        database = tableDatabase;
        schema = tableSchema;
        capture = tableCapture;
        collectedAt = tableCollected;
      } else if (!dataSource.equals(tableSource) || !database.equals(tableDatabase)
          || !schema.equals(tableSchema)) {
        throw blocked("MIXED_SOURCE_SCOPE");
      } else if (!capture.equals(tableCapture)) {
        throw blocked("MIXED_CAPTURE_RUN");
      }
      Object columnCount = table.attributes().get("columnCount");
      if (!(columnCount instanceof Number expected) || expected.intValue() < 1
          || expected.intValue() > MAX_COLUMNS || expected.doubleValue() != expected.intValue()) {
        throw blocked("INCOMPLETE_COLUMN_COUNT");
      }
      int declared = expected.intValue();
      if (totalColumns + declared > MAX_COLUMNS) throw blocked("SOURCE_LIMIT_EXCEEDED");
      // Bounded child read: 501 rows is enough to discover overflow, never load all children.
      List<EntityDTO> childRows = catalog.childrenBounded(table.id(), "tableColumn", MAX_COLUMNS + 1);
      if (childRows.size() != declared) throw blocked("COLUMN_COVERAGE_MISMATCH");
      var seen = new HashSet<String>();
      var columns = new ArrayList<Column>();
      for (EntityDTO column : childRows) {
        String name = required(column.columnName(), "MISSING_COLUMN");
        if (!"tableColumn".equals(column.typeName())
            || !Objects.equals(table.id(), column.parentAssetId())
            || !Objects.equals(tableSource, column.dataSourceId())
            || !Objects.equals(tableDatabase, column.databaseName())
            || !Objects.equals(tableSchema, column.schemaName() == null ? "" : column.schemaName())
            || !Objects.equals(tableName, column.tableName())
            || !seen.add(name)) throw blocked("INVALID_COLUMN_PROJECTION");
        String hash = required(text(column.facts().get("contentHash")), "COLUMN_FINGERPRINT_MISSING");
        Map<String, Object> attrs = column.attributes();
        // Only curated schema facts: no raw attributes, data rows, connection info, or user text.
        String type = boundedText(attrs.get("dataType"), 128);
        String comment = boundedText(attrs.get("columnComment"), 512);
        columns.add(new Column(name, hash, type, Boolean.TRUE.equals(attrs.get("primaryKey")), comment));
      }
      columns.sort(Comparator.comparing(Column::name));
      tables.add(new Table(key, tableName, tableHash, declared, columns));
      totalColumns += declared;
    }
    var parts = new ArrayList<String>();
    parts.add("f039-metadata-evidence-v1");
    parts.add(Long.toString(projectId));
    parts.add(dataSource);parts.add(database);parts.add(schema);parts.add(capture);
    for (Table t : tables) {
      parts.add(t.assetKey());parts.add(t.contentHash());parts.add(Integer.toString(t.declaredColumnCount()));
      for (Column c : t.columns()) {
        parts.add(c.name());parts.add(c.contentHash());
        parts.add(c.dataType());parts.add(Boolean.toString(c.primaryKey()));parts.add(c.comment());
      }
    }
    return new Evidence(projectId, dataSource, database, schema, capture, collectedAt,
        digest(parts), tables);
  }

  private static String text(Object value) {
    return value == null ? null : value.toString();
  }
  private static String required(String value, String reason) {
    if (value == null || value.isBlank()) throw blocked(reason);
    return value;
  }
  private static String boundedText(Object value, int max) {
    String raw = value == null ? "" : value.toString();
    return raw.length() <= max ? raw : raw.substring(0, max);
  }
  private static IllegalStateException blocked(String code) {
    return new IllegalStateException("[F039_" + code + "]");
  }
  private static String digest(List<String> values) {
    try {
      var buffer = new ByteArrayOutputStream();
      try (var out = new DataOutputStream(buffer)) {
        for (String value : values) {
          byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
          out.writeInt(bytes.length);
          out.write(bytes);
        }
      }
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(buffer.toByteArray()));
    } catch (IOException | NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }
}
