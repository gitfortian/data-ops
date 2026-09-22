package io.yak.ops.business.agent.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 观测载荷信封（设计稿 §五）：request_json / response_json 列的统一自描述格式。
 *
 * <p>四档模式：{@code INLINE}（原文，可截断）/ {@code SUMMARY_HASH}（结构摘要+指纹，
 * 原文不落库）/ {@code HASH_ONLY}（仅指纹）/ {@code OMITTED}（策略性省略，显式标记，
 * 不冒充不存在）。以 JSON 字符串落库，读取侧按 {@code mode} 分支解释。</p>
 */
public record PayloadEnvelope(
    String mode,
    String content,
    Boolean truncated,
    Long size,
    String sha256,
    Integer messageCount,
    String preview) {

  public static final String MODE_INLINE = "INLINE";
  public static final String MODE_SUMMARY_HASH = "SUMMARY_HASH";
  public static final String MODE_HASH_ONLY = "HASH_ONLY";
  public static final String MODE_OMITTED = "OMITTED";

  private static final ObjectMapper JSON = new ObjectMapper();

  /** 序列化为落库字符串；失败退化为 OMITTED 占位（不丢帧序，不阻断执行事实）。 */
  public String encode() {
    try {
      return JSON.writeValueAsString(this);
    } catch (Exception e) {
      return "{\"mode\":\"" + MODE_OMITTED + "\"}";
    }
  }

  /**
   * 读侧解码：O1 起落库为信封 JSON；存量行（O1 前的裸截断文本）降级为 INLINE 原文，
   * truncated 置 null（截断事实不可考）。null 入参返回 null。
   */
  public static PayloadEnvelope decode(String stored) {
    if (stored == null || stored.isBlank()) {
      return null;
    }
    try {
      PayloadEnvelope envelope = JSON.readValue(stored, PayloadEnvelope.class);
      return envelope.mode() == null || envelope.mode().isBlank() ? legacy(stored) : envelope;
    } catch (Exception e) {
      return legacy(stored);
    }
  }

  private static PayloadEnvelope legacy(String raw) {
    return new PayloadEnvelope(MODE_INLINE, raw, null, (long) raw.length(), null, null, null);
  }
}
