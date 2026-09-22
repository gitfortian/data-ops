package io.yak.ops.business.agent.telemetry;

/**
 * 渲染提示（设计稿 §4.2 KindSpec 五要素之一）：随 trace v2 的 kinds 清单投影给前端，
 * 前端通用 Timeline 组件据此前染色/选图标/排序——新增 kind 注册后前端零改动自动渲染。
 *
 * @param color 前端标签色（antd Tag 色名）
 * @param icon  图标语义名（前端图标映射表的键）
 * @param order 展示排序（小者在前）
 */
public record RenderHint(String color, String icon, int order) {}
