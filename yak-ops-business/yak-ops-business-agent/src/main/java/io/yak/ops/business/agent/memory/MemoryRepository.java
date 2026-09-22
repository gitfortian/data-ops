package io.yak.ops.business.agent.memory;

import java.time.LocalDateTime;
import java.util.List;

/** 长期记忆持久化契约（truth owner 是 yak_agent_memory；写入口唯一收敛在 MemoryFlushService）。 */
public interface MemoryRepository {

  /** 插入一条 LEDGER 层记忆，返回 id（失败返回 null，不影响执行事实）。 */
  Long insertLedger(MemoryRecord record);

  /** 检索某用户可见的全部 ACTIVE 记忆（USER 层 + GLOBAL 层；PROJECT 层 M2 接入），id 升序。 */
  List<MemoryRecord> listActiveForUser(String userId);

  /** 命中回写（hit_count/last_hit_at，异步 best-effort，不阻塞推理）。 */
  void markHit(List<Long> ids);

  // ---- M2 巩固管线 ----

  /** 全部 ACTIVE 的 LEDGER 条目（巩固分组输入），id 升序。 */
  List<MemoryRecord> listActiveLedgers();

  /** 指定 scope/type 的 ACTIVE CURATED 条目（合并视野与配额输入）。 */
  List<MemoryRecord> listCurated(String scope, String scopeKey, String memoryType);

  /** 新建 CURATED 条目，返回 id（失败 null）。 */
  Long insertCurated(MemoryRecord record);

  /** 更新 CURATED 正文/关键词/置信度（合并进既有条目）。 */
  void updateCurated(Long id, String content, String keywords, double confidence);

  /** 批量标记 MERGED 并指向主条目。 */
  void markMerged(List<Long> ids, Long mergedInto);

  /** 状态迁移（CONFLICT/PROMOTION_PENDING/ARCHIVED 等治理动作）。 */
  void updateStatus(Long id, String status);

  /** LEDGER 过期归档（未晋升且早于截止时刻），返回影响行数。 */
  int archiveExpiredLedgers(LocalDateTime cutoff);

  /** 提升置信度（巩固时按命中数微调，cap 1.0）。 */
  void bumpConfidence(Long id, double delta);
}
