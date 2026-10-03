package io.yak.ops.business.mdm.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 主数据清洗规则持久化对象(实体级配置,rule_expr 为 JSON 文本)。 */
@Data
@TableName("yak_mdm_clean_rule")
public class MdmCleanRulePO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 主数据实体。 */
  private Long entityId;

  /** 规则类型:DEDUP/STANDARDIZE/COMPLETE。 */
  private String ruleType;

  /** 规则名称(实体内唯一)。 */
  private String ruleName;

  /** 规则表达式(JSON):匹配字段与组合条件。 */
  private String ruleExpr;

  /** 是否启用。 */
  private Boolean enabled;

  /** 排序,小在前。 */
  private Integer sortOrder;

  /** 创建人。 */
  private String createdBy;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
