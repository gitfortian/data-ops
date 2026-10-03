package io.yak.ops.business.semantic.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 业务语义预置标准模板持久化对象(平台级,无 project_id)。 */
@Data
@TableName("yak_semantic_preset_template")
public class SemanticPresetTemplatePO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 标准类别:NAMING/TYPE/CODE/UNIT/CALIBER/SECURITY。 */
  private String kind;

  /** 标准编码(与标准表同规则)。 */
  private String stdCode;

  /** 标准名称。 */
  private String stdName;

  /** 描述。 */
  private String description;

  /** 排序,小在前。 */
  private Integer sortOrder;

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
}
