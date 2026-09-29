package io.yak.ops.business.agent.domain;

/**
 * 带投递序号的轮次事件帧。eventId 来自投递日志自增主键，是 SSE Last-Event-ID 游标的唯一依据；
 * 事件本身仍由 runtime 映射的 ChatTurnEvent 承载，投递日志只是可重建投影。
 */
public record JournaledTurnEvent(long eventId, ChatTurnEvent event) {

  /** 是否为使订阅流收尾的终态帧。 */
  public boolean terminal() {
    return event.type() == ChatTurnEvent.TurnEventType.TURN_FINISHED
        || event.type() == ChatTurnEvent.TurnEventType.ERROR;
  }
}
