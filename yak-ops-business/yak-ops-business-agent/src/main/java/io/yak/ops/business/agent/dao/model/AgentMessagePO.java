package io.yak.ops.business.agent.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Agent 消息树 PO（parentId 链接支撑编辑分叉 / regenerate / 分支导航）。 */
@Data
@TableName("yak_agent_message")
public class AgentMessagePO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 会话ID。 */
  private String sessionId;

  /** 消息ID（uuid）。 */
  private String messageId;

  /** 父消息ID，根为 NULL。 */
  private String parentId;

  /** 角色：user/assistant。 */
  private String role;

  /** 正文。 */
  private String content;

  /** assistant 消息的模型名。 */
  private String modelName;

  /** 该回复消耗 token 总量。 */
  private Long totalTokens;

  /** 生成完成标记：0-流式中 1-终态。 */
  private Integer done;

  /** 逻辑删除。 */
  private Integer isDeleted;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
