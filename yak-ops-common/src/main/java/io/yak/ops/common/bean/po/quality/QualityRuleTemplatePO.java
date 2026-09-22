package io.yak.ops.common.bean.po.quality;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("yak_quality_rule_template")
public class QualityRuleTemplatePO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private String templateCode;
  private String templateName;
  private String description;
  private String ruleType;
  private String ruleScope;
  private String qualityDimension;
  private String parameterSchemaJson;
  private Boolean builtin;
  private Boolean enabled;
  private Integer sortOrder;
  private Long folderId;
  private String templateSql;
  private String setFlag;
  private String checkType;
  private String checkMethod;
  private String createdBy;
  private Boolean deleted;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
