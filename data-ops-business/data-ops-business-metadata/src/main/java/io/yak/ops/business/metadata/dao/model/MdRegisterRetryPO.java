package io.yak.ops.business.metadata.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 写时登记的失败重试队列（outbox）。唯一键建在"变更"上而不是"状态"上：
 * 键含 status 会让一条实体 DONE 之后再也无法重新登记。
 */
@Data
@TableName("yak_md_register_retry")
public class MdRegisterRetryPO {

  /** UUID；主键即幂等令牌。 */
  @TableId(type = IdType.INPUT)
  private String taskId;

  /** 源域上下文；worker 执行前用它恢复 ProjectContext。 */
  private Long projectId;
  private String typeName;
  /** 源域交出的键。 */
  private String assetKey;
  /** 源域内主键，重放时回查用。 */
  private String sourceId;
  /** REGISTER|UNREGISTER。 */
  private String operation;
  /** PENDING|IN_PROGRESS|DONE|DEAD。 */
  private String status;
  private Integer attempts;
  private LocalDateTime nextAttemptTime;
  private String lastError;
  /** 整份 RegisterCommand，JSON 文本；重放时不再回查源域。 */
  private String payload;
  /** 本次变更的源侧时间，兼作去重令牌；绝不允许 NULL。 */
  private LocalDateTime sourceUpdatedAt;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
