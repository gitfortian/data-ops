package io.yak.ops.business.agent.runtime;

import io.yak.ops.business.agent.domain.ChatTurnEvent;
import java.util.function.Consumer;

/** 一轮推理的取消句柄。断连/超时时调用 {@link #dispose()} 终止上游推理。 */
public interface TurnSubscription {

  void dispose();
}
