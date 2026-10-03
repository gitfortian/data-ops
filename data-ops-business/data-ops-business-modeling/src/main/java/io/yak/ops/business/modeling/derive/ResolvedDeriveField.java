package io.yak.ops.business.modeling.derive;

import io.yak.ops.business.modeling.governance.StandardFieldMatcher;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.business.modeling.derive.ModelDeriveService.AggregateSpec;

/** Immutable resolved inheritance facts shared by preview and derivation. */
record ResolvedDeriveField(
    String sourceTable,
    String tableRole,
    Long odsModelId,
    String odsTableName,
    StructureView.ColumnView odsColumn,
    String overrideDataType,
    Long stdFieldId,
    StandardField stdField,
    String matchedBy,
    String landingField,
    String transformExpr,
    boolean include,
    DeriveTechnicalColumn technicalDef,
    String conflictWith,
    StandardFieldMatcher.Match suggestion,
    DimConventions.ConventionField convention,
    AggregateSpec aggregate) {

  String columnName() {
    if (technicalDef != null) {
      return technicalDef.name();
    }
    return odsColumn != null ? odsColumn.columnName() : landingField;
  }

  String dataType() {
    if (overrideDataType != null) {
      return overrideDataType;
    }
    return odsColumn != null ? odsColumn.dataType() : technicalDef.dataType();
  }

  Integer length() {
    if (odsColumn != null) {
      return odsColumn.length();
    }
    return technicalDef != null ? technicalDef.length() : convention.length();
  }

  Integer scale() {
    return odsColumn == null ? null : odsColumn.scale();
  }

  Boolean nullable() {
    return odsColumn == null ? Boolean.TRUE : odsColumn.nullable();
  }

  String comment() {
    if (odsColumn != null) {
      return odsColumn.comment();
    }
    return technicalDef != null ? technicalDef.note() : convention.note();
  }

  boolean technical() {
    return technicalDef != null;
  }

  boolean isConvention() {
    return convention != null;
  }

  boolean isMeasure() {
    return aggregate != null && "MEASURE".equals(aggregate.fieldRole());
  }

  /** 业务字段:既非管道技术列,也非维表约定列(治理率只统计业务字段)。 */
  boolean business() {
    return technicalDef == null && convention == null;
  }

  /** 同名列冲突标注:记录该字段在哪些表里重复出现。 */
  ResolvedDeriveField withConflict(String duplicateSourceTable) {
    return new ResolvedDeriveField(
        sourceTable, tableRole, odsModelId, odsTableName, odsColumn, overrideDataType,
        stdFieldId, stdField, matchedBy, landingField, transformExpr, include, technicalDef,
        duplicateSourceTable, suggestion, convention, aggregate);
  }
}
