package io.yak.ops.common.bean.po.semantic;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 业务语义数据标准持久化对象(六类统一表,kind 判别;类别专有列可空)。 */
@Data
@TableName("yak_semantic_standard")
public class SemanticStandardPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 标准类别:NAMING/TYPE/CODE/UNIT/CALIBER/SECURITY。 */
  private String kind;

  /** 标准编码,项目空间内 (kind, code) 唯一,创建后不可改。 */
  private String stdCode;

  /** 标准名称。 */
  private String stdName;

  /** 状态:ENABLED/DISABLED。 */
  private String status;

  /** 乐观版本,每次修改自增。 */
  private Integer version;

  /** 排序,小在前。 */
  private Integer sortOrder;

  /** 预置标识:1=来自预置初始化(31)。 */
  private Boolean isPreset;

  /** 描述。 */
  private String description;

  /** 命名标准:适用范围(TABLE/FIELD/DATABASE,可空=通用)。 */
  private String scope;

  /** 命名标准:适用分层引用(可空)。 */
  private String layer;

  /** 命名标准:规则表达式(必填)。 */
  private String ruleExpr;

  /** 命名标准:示例。 */
  private String example;

  /** 类型标准:类型编码(必填)。 */
  private String typeCode;

  /** 类型标准:标准类型(必填)。 */
  private String stdType;

  /** 类型标准:源库类型映射 JSON(38 自动映射唯一规则来源)。 */
  private String sourceMapping;

  /** 码值标准:码集编码(必填)。 */
  private String codeSetCode;

  /** 码值标准:码值(必填)。 */
  private String codeValue;

  /** 码值标准:码值标签。 */
  private String codeLabel;

  /** 单位标准:单位编码(必填)。 */
  private String unitCode;

  /** 单位标准:单位类型。 */
  private String unitType;

  /** 口径标准:口径编码。 */
  private String caliberCode;

  /** 口径标准:口径规则(必填)。 */
  private String calRule;

  /** 口径标准:业务说明。 */
  private String businessDesc;

  /** 安全标准:等级编码(必填)。 */
  private String levelCode;

  /** 安全标准:脱敏规则。 */
  private String maskRule;

  /** 创建人。 */
  private String createdBy;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
