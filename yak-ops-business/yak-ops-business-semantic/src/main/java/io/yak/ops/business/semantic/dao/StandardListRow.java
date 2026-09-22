package io.yak.ops.business.semantic.dao;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 统一分页查询投影(32.1):五类标准为原始行,CODE 类按码集聚合为组行。
 * 组行 id 为 NULL,code=分组键(码集编码;存量空码集行为 std_code),
 * valueCount=组内码值数;原始行 valueCount 为 NULL。
 */
@Data
public class StandardListRow {

  /** 主键;码集聚合组行为 NULL。 */
  private Long id;

  /** 标准类别:NAMING/TYPE/CODE/UNIT/CALIBER/SECURITY(组行恒为 CODE)。 */
  private String kind;

  /** 标准编码;组行 = 码集编码。 */
  private String code;

  /** 名称;组行 = MAX(std_name) = 码集名称。 */
  private String name;

  /** 状态;组行 = 组内含停用即 DISABLED。 */
  private String status;

  /** 版本;组行 = MAX(version)。 */
  private Integer version;

  /** 排序;组行 = MIN(sort_order)。 */
  private Integer sortOrder;

  /** 预置标识;组行 = MAX(is_preset)。 */
  private Boolean preset;

  /** 描述。 */
  private String description;

  /** 更新时间;组行 = MAX(update_time)。 */
  private LocalDateTime updateTime;

  /** 码值数;仅组行有值。 */
  private Integer valueCount;

  /** 码集编码;组行 = 分组键。 */
  private String codeSetCode;

  private String scope;

  private String layer;

  private String ruleExpr;

  private String example;

  private String typeCode;

  private String stdType;

  private String sourceMapping;

  private String codeValue;

  private String codeLabel;

  private String unitCode;

  private String unitType;

  private String caliberCode;

  private String calRule;

  private String businessDesc;

  private String levelCode;

  private String maskRule;
}
