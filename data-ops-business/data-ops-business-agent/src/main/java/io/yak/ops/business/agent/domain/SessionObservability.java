package io.yak.ops.business.agent.domain;

import java.util.List;

/** 会话级观测视图（设计稿 §7.2）：turns × kinds 矩阵，一眼定位慢轮次/重试异常/失败集中工具。 */
public record SessionObservability(String sessionId, List<TurnObservability> turns) {}
