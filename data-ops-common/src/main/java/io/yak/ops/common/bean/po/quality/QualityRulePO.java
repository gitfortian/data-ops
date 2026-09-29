package io.yak.ops.common.bean.po.quality;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("yak_quality_rule")
public class QualityRulePO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long monitorId;
  private Long templateId;
  private String templateCode;
  private String ruleName;
  private String ruleType;
  private String ruleScope;
  private String qualityDimension;
  private String columnName;
  private String comparisonOperator;
  private BigDecimal thresholdValue;
  private BigDecimal thresholdEnd;
  private String enumValuesJson;
  private String customSql;
  private Boolean enabled;
  private Integer sortOrder;
  private Boolean deleted;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
