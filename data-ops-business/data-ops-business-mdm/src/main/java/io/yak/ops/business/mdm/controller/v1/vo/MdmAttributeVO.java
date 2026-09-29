package io.yak.ops.business.mdm.controller.v1.vo;

import java.time.LocalDateTime;

/** 主数据属性视图对象(标准引用展示名由服务端经语义 SPI 解析)。 */
public record MdmAttributeVO(
    Long id,
    Long entityId,
    String code,
    String name,
    String type,
    String dataType,
    Long stdTypeId,
    String stdTypeName,
    Long stdUnitId,
    String stdUnitName,
    String stdCodeSetCode,
    Long stdSecurityId,
    String stdSecurityName,
    boolean required,
    String businessDesc,
    int sortOrder,
    String status,
    LocalDateTime createTime,
    LocalDateTime updateTime) {}
