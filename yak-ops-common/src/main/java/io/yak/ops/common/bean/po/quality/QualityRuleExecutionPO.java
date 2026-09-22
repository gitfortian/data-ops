package io.yak.ops.common.bean.po.quality;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("yak_quality_rule_execution")
public class QualityRuleExecutionPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long executionId;
  private Long ruleId;
  private String ruleName;
  private String templateCode;
  private String ruleType;
  private String columnName;
  private String checkResult;
  private String metricValue;
  private String expectedValue;
  private String executedSql;
  private String errorMessage;
  private Long durationMs;
  private LocalDateTime createdAt;
}
