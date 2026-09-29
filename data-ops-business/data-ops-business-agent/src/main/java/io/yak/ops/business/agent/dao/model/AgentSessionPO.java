package io.yak.ops.business.agent.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** AI 分析会话元数据 PO。 */
@Data
@TableName("yak_agent_session")
public class AgentSessionPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 会话ID（与 StateStore 会话标识一致）。 */
  private String sessionId;

  /** 归属用户ID。 */
  private Long userId;

  /** 项目空间ID（PROJECT_RUNTIME：异步上下文恢复通道）。 */
  private Long projectId;

  /** 会话标题。 */
  private String title;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
