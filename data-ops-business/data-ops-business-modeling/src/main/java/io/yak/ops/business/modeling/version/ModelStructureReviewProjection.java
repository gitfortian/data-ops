package io.yak.ops.business.modeling.version;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.modeling.api.ModelStructureReviewQueryApi;
import io.yak.ops.business.modeling.api.ModelStructureReviewQueryApi.Change;
import io.yak.ops.business.modeling.api.ModelStructureReviewQueryApi.MappingCheck;
import io.yak.ops.business.modeling.dao.model.ModelingColumnMappingPO;
import io.yak.ops.business.modeling.structure.StructureView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Explicit allowlist: no expressions, defaults, source connection metadata or business descriptions. */
final class ModelStructureReviewProjection {
  private static final ObjectMapper JSON = new ObjectMapper();
  static final List<String> GAPS = List.of("NO_HISTORICAL_MAPPING_SNAPSHOT", "SOURCE_SCHEMA_NOT_CHECKED",
      "TYPE_COMPATIBILITY_NOT_CHECKED", "DEFAULTS_EXPRESSIONS_PROPERTIES_NOT_COMPARED",
      "DESCRIPTIONS_STANDARDS_RELATIONS_NOT_COMPARED", "RENAME_NOT_INFERRED", "UNSAVED_EDITS_NOT_INCLUDED");

  private ModelStructureReviewProjection() {}

  record Column(String name, String dataType, Integer length, Integer scale, Boolean nullable, Integer sortOrder) {}
  record Index(String name, Boolean unique, String type, List<String> columns) {}
  record Partition(String type, List<String> columns) {}

  static List<Change> changes(StructureView baseline, StructureView saved) {
    var before = columns(baseline);
    var after = columns(saved);
    var changes = new ArrayList<Change>();
    add(changes, "TABLE", "tableName", text(baseline.tableName(), 128), text(saved.tableName(), 128));
    add(changes, "TABLE", "dialect", text(baseline.dialect(), 64), text(saved.dialect(), 64));
    var names = new java.util.LinkedHashSet<>(before.keySet());
    names.addAll(after.keySet());
    for (String key : names) {
      var left = before.get(key);
      var right = after.get(key);
      add(changes, "COLUMN", right == null ? left.name() : right.name(), left, right);
    }
    add(changes, "PRIMARY_KEY", "primaryKey", names(baseline.primaryKey()), names(saved.primaryKey()));
    var leftIndexes = indexes(baseline);
    var rightIndexes = indexes(saved);
    var indexNames = new java.util.LinkedHashSet<>(leftIndexes.keySet());
    indexNames.addAll(rightIndexes.keySet());
    for (String key : indexNames) {
      var left = leftIndexes.get(key);
      var right = rightIndexes.get(key);
      add(changes, "INDEX", right == null ? left.name() : right.name(), left, right);
    }
    add(changes, "PARTITION", "partition", partition(baseline), partition(saved));
    return List.copyOf(changes);
  }

  static List<MappingCheck> checks(StructureView saved, List<ModelingColumnMappingPO> mappings, List<Change> changes) {
    var columns = columns(saved);
    var byTarget = new LinkedHashMap<String, ModelingColumnMappingPO>();
    for (var mapping : mappings) {
      String key = key(required(mapping.getTargetColumn(), 128));
      if (byTarget.putIfAbsent(key, mapping) != null) throw invalid();
    }
    var changed = changes.stream().filter(change -> "COLUMN".equals(change.area()))
        .map(change -> key(change.name())).collect(java.util.stream.Collectors.toSet());
    var targets = new java.util.LinkedHashSet<>(columns.keySet());
    targets.addAll(byTarget.keySet());
    var checks = new ArrayList<MappingCheck>();
    for (String key : targets) {
      var column = columns.get(key);
      var mapping = byTarget.get(key);
      boolean transform = mapping != null && mapping.getTransformExpr() != null && !mapping.getTransformExpr().isBlank();
      var reasons = new ArrayList<String>();
      if (column == null) reasons.add("ORPHAN_MAPPING_TARGET");
      if (column != null && mapping == null) reasons.add("UNMAPPED_SAVED_COLUMN");
      if (column != null && changed.contains(key) && mapping != null) reasons.add("CHANGED_TARGET_REVIEW");
      if (transform) reasons.add("TRANSFORM_MANUAL_REVIEW");
      if (!reasons.isEmpty()) checks.add(new MappingCheck(column == null ? mapping.getTargetColumn() : column.name(),
          mapping != null, transform, List.copyOf(reasons)));
    }
    return List.copyOf(checks);
  }

  private static Map<String, Column> columns(StructureView view) {
    if (view == null || view.columns() == null || view.columns().size() > ModelStructureReviewQueryApi.COLUMN_LIMIT) throw invalid();
    var result = new LinkedHashMap<String, Column>();
    for (var column : view.columns()) {
      if (column == null) throw invalid();
      String name = required(column.columnName(), 128);
      var value = new Column(name, required(column.dataType(), 64), column.length(), column.scale(), column.nullable(), column.sortOrder());
      if (result.putIfAbsent(key(name), value) != null) throw invalid();
    }
    return result;
  }

  private static Map<String, Index> indexes(StructureView view) {
    if (view.indexes() == null || view.indexes().size() > ModelStructureReviewQueryApi.INDEX_LIMIT) throw invalid();
    var result = new LinkedHashMap<String, Index>();
    for (var index : view.indexes()) {
      if (index == null) throw invalid();
      String name = required(index.indexName(), 128);
      if (result.putIfAbsent(key(name), new Index(name, index.uniqueIndex(), text(index.indexType(), 64), names(index.columns()))) != null) throw invalid();
    }
    return result;
  }

  private static Partition partition(StructureView view) {
    return view.partition() == null ? null : new Partition(text(view.partition().type(), 64), names(view.partition().columns()));
  }

  private static List<String> names(List<String> values) {
    if (values == null || values.size() > ModelStructureReviewQueryApi.COLUMN_LIMIT) throw invalid();
    return values.stream().map(value -> required(value, 128)).toList();
  }

  private static void add(List<Change> changes, String area, String name, Object before, Object after) {
    if (!Objects.equals(before, after)) changes.add(new Change(area, name, before == null ? null : encode(before), after == null ? null : encode(after)));
  }

  static String encode(Object value) {
    try { return JSON.writeValueAsString(value); }
    catch (JsonProcessingException failure) { throw invalid(); }
  }

  static String text(String value, int limit) {
    if (value != null && value.length() > limit) throw invalid();
    return value;
  }

  private static String required(String value, int limit) {
    if (value == null || value.isBlank()) throw invalid();
    return text(value, limit);
  }

  private static String key(String name) { return name.toLowerCase(Locale.ROOT); }
  private static IllegalArgumentException invalid() { return new IllegalArgumentException("比较输入不完整或超出支持范围，请在原模型页核对"); }
}
