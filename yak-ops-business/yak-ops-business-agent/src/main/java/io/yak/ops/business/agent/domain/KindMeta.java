package io.yak.ops.business.agent.domain;

/** 观测类型渲染投影（trace v2 kinds 清单）：前端数据驱动渲染的依据。 */
public record KindMeta(String kind, String title, String color, String icon, int order) {}
