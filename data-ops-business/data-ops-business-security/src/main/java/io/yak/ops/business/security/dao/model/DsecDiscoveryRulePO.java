package io.yak.ops.business.security.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 敏感数据发现规则持久化对象。 */
@Data
@TableName("yak_dsec_discovery_rule")
public class DsecDiscoveryRulePO {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private String ruleCode;
  private String ruleName;
  private String matchType;
  private String pattern;
  private Long levelId;
  private Long categoryId;
  private Integer enabled;
  private String description;
  private String createdBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
