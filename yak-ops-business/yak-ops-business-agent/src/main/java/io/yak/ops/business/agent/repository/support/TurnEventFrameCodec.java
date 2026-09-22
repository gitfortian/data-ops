package io.yak.ops.business.agent.repository.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.agent.domain.ChatTurnEvent;

/** 轮次事件帧的投递投影 codec。只服务于 yak_agent_turn_event 存取，不是第二套业务契约。 */
public final class TurnEventFrameCodec {

  private static final ObjectMapper JSON = new ObjectMapper();

  private TurnEventFrameCodec() {}

  public static String encode(ChatTurnEvent event) {
    try {
      return JSON.writeValueAsString(event);
    } catch (Exception e) {
      // 记录失败不能丢帧序：退化为类型占位帧，replay 端至少保序可见事件类型
      return "{\"type\":\"" + event.type().name() + "\"}";
    }
  }

  public static ChatTurnEvent decode(String json, String eventType) {
    try {
      return JSON.readValue(json, ChatTurnEvent.class);
    } catch (Exception e) {
      return ChatTurnEvent.of(ChatTurnEvent.TurnEventType.valueOf(eventType));
    }
  }
}
