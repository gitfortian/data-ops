package io.yak.ops.business.agent.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Agent 推理轮次生命周期 PO。 */
@Data
@TableName("yak_agent_turn")
public class AgentTurnPO {

  /** 主键（入队 FIFO 序）。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 轮次ID。 */
  private String turnId;

  /** 会话ID。 */
  private String sessionId;

  /** 归属用户（提交时冻结，归属校验依据）。 */
  private Long userId;

  /** 类型：START/RESUME。 */
  private String kind;

  /** 输入投影 JSON：message 或 tool feedbacks。 */
  private String payloadJson;

  /** 状态：QUEUED/RUNNING/WAITING_INPUT/COMPLETED/FAILED/CANCELLED/INTERRUPTED。 */
  private String status;

  /** 分类错误码：TIMEOUT/USER_ERROR/PROVIDER_ERROR/GENERIC/ORPHANED_INTERRUPTED... */
  private String errorCode;

  /** 失败原因摘要。 */
  private String errorMessage;

  /** 项目空间预留（PROJECT_RUNTIME，异步上下文恢复通道）。 */
  private Long projectId;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 开始执行时间。 */
  private LocalDateTime startTime;

  /** 终态时间。 */
  private LocalDateTime endTime;
}
