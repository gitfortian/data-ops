package io.yak.ops.business.modeling.domain;

/**
 * One physical column of a model's table. Names are unique within the model
 * (case-insensitive); sort order is assigned by the full-replace save.
 * std*Id fields are loose references into yak-ops-business-semantic
 * (no physical FK); they stay null until tickets 38/39/44 write them.
 */
public record ColumnDefinition(
    Long id,
    String columnName,
    String dataType,
    Integer length,
    Integer scale,
    Boolean nullable,
    String defaultValue,
    String comment,
    String businessDescription,
    Integer sortOrder,
    Long stdTypeId,
    Long stdNamingId,
    String stdCodeSetCode,
    Long stdUnitId,
    Long stdCaliberId,
    Long stdSecurityId,
    Long stdFieldId,
    String fieldRole,
    String aggregateFunc,
    String transformExpr) {

  /** Compatibility view without the standard-field link (pre-44 call sites and tests). */
  public ColumnDefinition(
      Long id,
      String columnName,
      String dataType,
      Integer length,
      Integer scale,
      Boolean nullable,
      String defaultValue,
      String comment,
      String businessDescription,
      Integer sortOrder,
      Long stdTypeId,
      Long stdNamingId,
      String stdCodeSetCode,
      Long stdUnitId,
      Long stdCaliberId,
      Long stdSecurityId) {
    this(id, columnName, dataType, length, scale, nullable, defaultValue, comment,
        businessDescription, sortOrder, stdTypeId, stdNamingId, stdCodeSetCode, stdUnitId,
        stdCaliberId, stdSecurityId, null, null, null, null);
  }

  /** Compatibility view of the pre-M4 arity (std references null). */
  public ColumnDefinition(
      Long id,
      String columnName,
      String dataType,
      Integer length,
      Integer scale,
      Boolean nullable,
      String defaultValue,
      String comment,
      String businessDescription,
      Integer sortOrder) {
    this(id, columnName, dataType, length, scale, nullable, defaultValue, comment,
        businessDescription, sortOrder, null, null, null, null, null, null, null, null, null, null);
  }
}
