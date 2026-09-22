package io.yak.ops.business.agent.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Agent 步骤级执行记录 PO。 */
@Data
@TableName("yak_agent_step")
public class AgentStepPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 会话ID。 */
  private String sessionId;

  /** 轮次ID（LLM 调用与轮次汇总绑定；工具级历史行为空）。 */
  private String turnId;

  /** 父步骤ID：TOOL_CALL 指向触发它的 LLM_CALL；LLM_CALL/轮次汇总为空（turn 内根）。 */
  private Long parentStepId;

  /** 步骤类型：LLM_CALL/TOOL_CALL/GUARD/COMPILE/CLARIFY/TURN_SUMMARY。 */
  private String kind;

  /** 模型名或工具名。 */
  private String name;

  /** 工具调用ID：与事件帧 TOOL_CALL/TOOL_RESULT 的 toolCallId 一对一关联（因果 join 键）。 */
  private String toolCallId;

  /** 状态：RUNNING/COMPLETED/FAILED/CANCELLED/REJECTED。 */
  private String status;

  /** 请求摘要（超长截断）。 */
  private String requestJson;

  /** 响应摘要（超长截断）。 */
  private String responseJson;

  /** 分类错误码：TIMEOUT/USER_ERROR/PROVIDER_ERROR/GUARD_REJECTED... */
  private String errorCode;

  /** 失败原因摘要。 */
  private String errorMessage;

  /** 计量统计 JSON：{promptTokens,completionTokens,latencyMs,retryCount}。 */
  private String statsJson;

  /** span 开始时刻（服务端权威；存量行为空，读取侧回退 stats_json.latencyMs）。 */
  private LocalDateTime startedAt;

  /** span 终态时刻。 */
  private LocalDateTime endedAt;

  /** 尝试序：重试/并行批内序号，1 起。 */
  private Integer attempt;

  /** 创建时间。 */
  private LocalDateTime createTime;
}
