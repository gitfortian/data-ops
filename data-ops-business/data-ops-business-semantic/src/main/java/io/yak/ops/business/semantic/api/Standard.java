package io.yak.ops.business.semantic.api;

import java.time.LocalDateTime;

/**
 * 一条数据标准(六类统一模型)。编码是类别内的稳定键,创建后不可改;
 * version 随每次修改自增(32 的版本快照以此为基线)。
 */
public record Standard(
    Long id,
    StandardKind kind,
    String code,
    String name,
    StandardStatus status,
    int version,
    int sortOrder,
    boolean preset,
    String description,
    KindFields fields,
    String createdBy,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  /** 类别专有字段(NAMING/TYPE/CODE/UNIT/CALIBER/SECURITY 各取所需,其余为 null)。 */
  public record KindFields(
      String scope,
      String layer,
      String ruleExpr,
      String example,
      String typeCode,
      String stdType,
      String sourceMapping,
      String codeSetCode,
      String codeValue,
      String codeLabel,
      String unitCode,
      String unitType,
      String caliberCode,
      String calRule,
      String businessDesc,
      String levelCode,
      String maskRule) {}

  public Standard withPersisted(Long id, String operator, LocalDateTime createTime) {
    return new Standard(id, kind, code, name, status, version, sortOrder, preset, description,
        fields, operator, createTime, createTime);
  }

  public Standard withVersion(int newVersion, LocalDateTime updateTime) {
    return new Standard(id, kind, code, name, status, newVersion, sortOrder, preset, description,
        fields, createdBy, createTime, updateTime);
  }

  public Standard withStatus(StandardStatus newStatus, LocalDateTime updateTime) {
    return new Standard(id, kind, code, name, newStatus, version, sortOrder, preset, description,
        fields, createdBy, createTime, updateTime);
  }
}
