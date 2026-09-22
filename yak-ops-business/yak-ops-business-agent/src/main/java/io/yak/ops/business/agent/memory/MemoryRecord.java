package io.yak.ops.business.agent.memory;

import java.time.LocalDateTime;

/**
 * 长期记忆读模型（M1）：truth owner 是 yak_agent_memory。
 * scope × type × layer 的语义见 data-agent-long-term-memory-design.md §3。
 */
public record MemoryRecord(
    Long id,
    String scope,
    String scopeKey,
    String memoryType,
    String layer,
    String content,
    String keywords,
    double confidence,
    int hitCount,
    LocalDateTime lastHitAt,
    String sourceTurnId,
    String status) {

  public static final String SCOPE_USER = "USER";
  public static final String SCOPE_GLOBAL = "GLOBAL";
  public static final String LAYER_LEDGER = "LEDGER";
  public static final String LAYER_CURATED = "CURATED";
  public static final String STATUS_ACTIVE = "ACTIVE";
  public static final String STATUS_MERGED = "MERGED";
  public static final String STATUS_ARCHIVED = "ARCHIVED";
  public static final String STATUS_DISABLED = "DISABLED";
  public static final String STATUS_CONFLICT = "CONFLICT";
  public static final String STATUS_PROMOTION_PENDING = "PROMOTION_PENDING";
}
