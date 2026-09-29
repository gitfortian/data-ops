package io.yak.ops.business.semantic.controller.v1.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 标准字段视图对象。 */
@Data
public class StandardFieldVO {

  /** 主键。 */
  private Long id;

  /** 字段编码。 */
  private String code;

  /** 字段名称。 */
  private String name;

  /** 角色:PROCESS/DIMENSION/METRIC。 */
  private String role;

  /** 状态:ENABLED/DISABLED。 */
  private String status;

  /** 生效类型:引用类型标准时为 std_type 快照。 */
  private String dataType;

  /** 类型标准引用。 */
  private Long stdTypeId;

  /** 单位标准引用。 */
  private Long stdUnitId;

  /** 口径标准引用。 */
  private Long stdCaliberId;

  /** 码集编码引用。 */
  private String stdCodeSetCode;

  /** 安全标准引用。 */
  private Long stdSecurityId;

  /** 类型标准名称（名称（编码））,服务端解析(2026-09-16)。 */
  private String stdTypeName;

  /** 单位标准名称。 */
  private String stdUnitName;

  /** 口径标准名称。 */
  private String stdCaliberName;

  /** 安全标准名称。 */
  private String stdSecurityName;

  /** 业务描述。 */
  private String businessDesc;

  /** 来源:PRESET/MANUAL/CAPTURE。 */
  private String source;

  /** 乐观版本。 */
  private Integer version;

  /** 是否必需(过程装配上下文)。 */
  private Boolean required;

  /** 创建人。 */
  private String createdBy;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
