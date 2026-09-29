package io.yak.ops.business.agent.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Agent 轮次事件投递日志 PO（可重建投影，非事实源；id 兼作 SSE 游标）。 */
@Data
@TableName("yak_agent_turn_event")
public class AgentTurnEventPO {

  /** 递增 event_id（SSE Last-Event-ID 游标）。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 轮次ID。 */
  private String turnId;

  /** 帧类型：TEXT_DELTA/TOOL_CALL/.../TURN_FINISHED/ERROR。 */
  private String eventType;

  /** 帧投影 JSON。 */
  private String payloadJson;

  /** 创建时间。 */
  private LocalDateTime createTime;
}
