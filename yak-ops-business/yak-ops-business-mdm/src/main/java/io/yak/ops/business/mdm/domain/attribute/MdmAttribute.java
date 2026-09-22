package io.yak.ops.business.mdm.domain.attribute;

import java.time.LocalDateTime;

/**
 * 主数据属性;编码为实体内稳定键,创建后不可改。类型/单位/安全引用标准
 * (松散 ID),码值引用码集编码;展示名经语义 SPI 解析。
 */
public record MdmAttribute(
    Long id,
    Long entityId,
    String code,
    String name,
    MdmAttributeType type,
    String dataType,
    Long stdTypeId,
    Long stdUnitId,
    String stdCodeSetCode,
    Long stdSecurityId,
    boolean required,
    String businessDesc,
    int sortOrder,
    String status,
    String createdBy,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  public static final String STATUS_ENABLED = "ENABLED";

  public MdmAttribute withPersisted(Long id, String operator, LocalDateTime time) {
    return new MdmAttribute(
        id, entityId, code, name, type, dataType, stdTypeId, stdUnitId, stdCodeSetCode,
        stdSecurityId, required, businessDesc, sortOrder, status, operator, time, time);
  }
}
