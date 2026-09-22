package io.yak.ops.common.bean.po.lifecycle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** TTL 策略发布版本快照(append-only;消费方读策略生效内容只经此表)。 */
@Data
@TableName("yak_lc_policy_version")
public class LifecyclePolicyVersionPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long policyId;
  private Long projectId;
  private Integer versionNo;
  private String payloadJson;
  private String checksum;
  private Integer sourceDraftRevision;
  private String createdBy;
  private LocalDateTime createTime;
}
