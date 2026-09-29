package io.yak.ops.business.semantic.controller.v1.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 数据标准视图对象(六类统一,kind 外的专有字段按需为 null)。 */
@Data
public class StandardVO {

  /** 主键。 */
  private Long id;

  /** 标准类别:NAMING/TYPE/CODE/UNIT/CALIBER/SECURITY。 */
  private String kind;

  /** 标准编码。 */
  private String code;

  /** 标准名称。 */
  private String name;

  /** 状态:ENABLED/DISABLED。 */
  private String status;

  /** 版本。 */
  private Integer version;

  /** 排序。 */
  private Integer sortOrder;

  /** 预置标识。 */
  private Boolean preset;

  /** 描述。 */
  private String description;

  /** 命名标准:适用范围。 */
  private String scope;

  /** 命名标准:适用分层。 */
  private String layer;

  /** 命名标准:规则表达式。 */
  private String ruleExpr;

  /** 命名标准:示例。 */
  private String example;

  /** 类型标准:类型编码。 */
  private String typeCode;

  /** 类型标准:标准类型。 */
  private String stdType;

  /** 类型标准:源库类型映射 JSON。 */
  private String sourceMapping;

  /** 码值标准:码集编码。 */
  private String codeSetCode;

  /** 码值标准:码值。 */
  private String codeValue;

  /** 码值标准:码值标签。 */
  private String codeLabel;

  /** 单位标准:单位编码。 */
  private String unitCode;

  /** 单位标准:单位类型。 */
  private String unitType;

  /** 口径标准:口径编码。 */
  private String caliberCode;

  /** 口径标准:口径规则。 */
  private String calRule;

  /** 口径标准:业务说明。 */
  private String businessDesc;

  /** 安全标准:等级编码。 */
  private String levelCode;

  /** 安全标准:脱敏规则。 */
  private String maskRule;

  /** 码值数;仅码集聚合组行有值(32.1)。 */
  private Integer codeValueCount;

  /** 创建人。 */
  private String createdBy;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
