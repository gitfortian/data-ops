package io.yak.ops.business.security.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 合规检查结果持久化对象。 */
@Data
@TableName("yak_dsec_compliance_finding")
public class DsecComplianceFindingPO {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private String batchId;
  private Long ruleId;
  private String ruleType;
  private String targetKey;
  private String targetName;
  private Integer passed;
  private String finding;
  private String severity;
  private LocalDateTime checkedTime;
  private LocalDateTime createTime;
}
