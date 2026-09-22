package io.yak.ops.business.semantic.api;

import java.time.LocalDateTime;

/**
 * 一条全局标准字段;std 引用必须是匹配类别的标准 ID/码集(服务层校验)。
 * required 是过程装配时的上下文标记(经 getFieldSets 输出),非字段本体属性。
 */
public record StandardField(
    Long id,
    String code,
    String name,
    String role,
    String status,
    String dataType,
    Long stdTypeId,
    Long stdUnitId,
    Long stdCaliberId,
    String stdCodeSetCode,
    Long stdSecurityId,
    String businessDesc,
    String source,
    int version,
    boolean required,
    String createdBy,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  /** 字段角色:PROCESS 业务过程 / DIMENSION 维度 / METRIC 度量。 */
  public static final String ROLE_PROCESS = "PROCESS";
  public static final String ROLE_DIMENSION = "DIMENSION";
  public static final String ROLE_METRIC = "METRIC";

  public static final String STATUS_ENABLED = "ENABLED";
  public static final String STATUS_DISABLED = "DISABLED";

  public static final String SOURCE_PRESET = "PRESET";
  public static final String SOURCE_MANUAL = "MANUAL";
  public static final String SOURCE_CAPTURE = "CAPTURE";

  public static boolean isValidRole(String role) {
    return ROLE_PROCESS.equals(role) || ROLE_DIMENSION.equals(role) || ROLE_METRIC.equals(role);
  }

  public boolean isEnabled() {
    return STATUS_ENABLED.equals(status);
  }

  public boolean isPreset() {
    return SOURCE_PRESET.equals(source);
  }

  public StandardField withStatus(String newStatus) {
    return new StandardField(id, code, name, role, newStatus, dataType, stdTypeId, stdUnitId,
        stdCaliberId, stdCodeSetCode, stdSecurityId, businessDesc, source, version,
        required, createdBy, createTime, updateTime);
  }

  public StandardField withUpdateTime(java.time.LocalDateTime time) {
    return new StandardField(id, code, name, role, status, dataType, stdTypeId, stdUnitId,
        stdCaliberId, stdCodeSetCode, stdSecurityId, businessDesc, source, version,
        required, createdBy, createTime, time);
  }

  /** 过程装配上下文:标记 required(仅 getFieldSets 装配时为真实值)。 */
  public StandardField withRequired(boolean requiredFlag) {
    return new StandardField(id, code, name, role, status, dataType, stdTypeId, stdUnitId,
        stdCaliberId, stdCodeSetCode, stdSecurityId, businessDesc, source, version,
        requiredFlag, createdBy, createTime, updateTime);
  }
}
