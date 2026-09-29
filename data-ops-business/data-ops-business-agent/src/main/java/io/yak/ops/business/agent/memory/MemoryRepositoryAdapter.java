package io.yak.ops.business.agent.memory;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.dao.mapper.AgentMemoryMapper;
import io.yak.ops.business.agent.dao.model.AgentMemoryPO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/** 长期记忆读适配：只读查询 + LEDGER 写入（写入口唯一收敛在 MemoryFlushService）。 */
@Slf4j
@ConditionalOnAgentEnabled
@Repository
@RequiredArgsConstructor
public class MemoryRepositoryAdapter implements MemoryRepository {

  private final AgentMemoryMapper mapper;

  @Override
  public Long insertLedger(MemoryRecord record) {
    try {
      AgentMemoryPO po = new AgentMemoryPO();
      po.setScope(record.scope());
      po.setScopeKey(record.scopeKey());
      po.setMemoryType(record.memoryType());
      po.setLayer(record.layer());
      po.setContent(record.content());
      po.setKeywords(record.keywords());
      po.setConfidence(java.math.BigDecimal.valueOf(record.confidence()));
      po.setHitCount(record.hitCount());
      po.setLastHitAt(record.lastHitAt());
      po.setSourceTurnId(record.sourceTurnId());
      po.setSourceSession(null);
      po.setStatus(record.status());
      mapper.insert(po);
      return po.getId();
    } catch (Exception e) {
      log.warn("failed to insert agent memory: scope={}, type={}", record.scope(), record.memoryType(), e);
      return null;
    }
  }

  @Override
  public List<MemoryRecord> listActiveForUser(String userId) {
    return mapper.selectList(Wrappers.<AgentMemoryPO>lambdaQuery()
            .in(AgentMemoryPO::getScope, MemoryRecord.SCOPE_USER, MemoryRecord.SCOPE_GLOBAL)
            .and(w -> w.eq(AgentMemoryPO::getScopeKey, userId)
                .or().eq(AgentMemoryPO::getScopeKey, "-"))
            .eq(AgentMemoryPO::getStatus, MemoryRecord.STATUS_ACTIVE)
            .orderByAsc(AgentMemoryPO::getId))
        .stream()
        .map(MemoryRepositoryAdapter::toRecord)
        .toList();
  }

  @Override
  public void markHit(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return;
    }
    try {
      mapper.update(null, Wrappers.<AgentMemoryPO>lambdaUpdate()
          .in(AgentMemoryPO::getId, ids)
          .setSql("hit_count = hit_count + 1")
          .set(AgentMemoryPO::getLastHitAt, java.time.LocalDateTime.now()));
    } catch (Exception e) {
      log.warn("failed to mark memory hits: {}", ids, e);
    }
  }

  // ---- M2 巩固管线 ----

  @Override
  public List<MemoryRecord> listActiveLedgers() {
    return mapper.selectList(Wrappers.<AgentMemoryPO>lambdaQuery()
            .eq(AgentMemoryPO::getLayer, MemoryRecord.LAYER_LEDGER)
            .eq(AgentMemoryPO::getStatus, MemoryRecord.STATUS_ACTIVE)
            .orderByAsc(AgentMemoryPO::getId))
        .stream()
        .map(MemoryRepositoryAdapter::toRecord)
        .toList();
  }

  @Override
  public List<MemoryRecord> listCurated(String scope, String scopeKey, String memoryType) {
    return mapper.selectList(Wrappers.<AgentMemoryPO>lambdaQuery()
            .eq(AgentMemoryPO::getScope, scope)
            .eq(AgentMemoryPO::getScopeKey, scopeKey)
            .eq(AgentMemoryPO::getMemoryType, memoryType)
            .eq(AgentMemoryPO::getLayer, MemoryRecord.LAYER_CURATED)
            .eq(AgentMemoryPO::getStatus, MemoryRecord.STATUS_ACTIVE)
            .orderByAsc(AgentMemoryPO::getId))
        .stream()
        .map(MemoryRepositoryAdapter::toRecord)
        .toList();
  }

  @Override
  public Long insertCurated(MemoryRecord record) {
    try {
      AgentMemoryPO po = new AgentMemoryPO();
      po.setScope(record.scope());
      po.setScopeKey(record.scopeKey());
      po.setMemoryType(record.memoryType());
      po.setLayer(MemoryRecord.LAYER_CURATED);
      po.setContent(record.content());
      po.setKeywords(record.keywords());
      po.setConfidence(java.math.BigDecimal.valueOf(record.confidence()));
      po.setHitCount(0);
      po.setSourceTurnId(record.sourceTurnId());
      po.setStatus(MemoryRecord.STATUS_ACTIVE);
      mapper.insert(po);
      return po.getId();
    } catch (Exception e) {
      log.warn("failed to insert curated memory", e);
      return null;
    }
  }

  @Override
  public void updateCurated(Long id, String content, String keywords, double confidence) {
    try {
      mapper.update(null, Wrappers.<AgentMemoryPO>lambdaUpdate()
          .eq(AgentMemoryPO::getId, id)
          .set(AgentMemoryPO::getContent, content)
          .set(AgentMemoryPO::getKeywords, keywords)
          .set(AgentMemoryPO::getConfidence, java.math.BigDecimal.valueOf(confidence)));
    } catch (Exception e) {
      log.warn("failed to update curated memory: id={}", id, e);
    }
  }

  @Override
  public void markMerged(List<Long> ids, Long mergedInto) {
    if (ids == null || ids.isEmpty()) {
      return;
    }
    try {
      mapper.update(null, Wrappers.<AgentMemoryPO>lambdaUpdate()
          .in(AgentMemoryPO::getId, ids)
          .set(AgentMemoryPO::getStatus, MemoryRecord.STATUS_MERGED)
          .set(AgentMemoryPO::getMergedInto, mergedInto));
    } catch (Exception e) {
      log.warn("failed to mark merged: {} -> {}", ids, mergedInto, e);
    }
  }

  @Override
  public void updateStatus(Long id, String status) {
    try {
      mapper.update(null, Wrappers.<AgentMemoryPO>lambdaUpdate()
          .eq(AgentMemoryPO::getId, id)
          .set(AgentMemoryPO::getStatus, status));
    } catch (Exception e) {
      log.warn("failed to update memory status: id={}, status={}", id, status, e);
    }
  }

  @Override
  public int archiveExpiredLedgers(LocalDateTime cutoff) {
    try {
      return mapper.update(null, Wrappers.<AgentMemoryPO>lambdaUpdate()
          .eq(AgentMemoryPO::getLayer, MemoryRecord.LAYER_LEDGER)
          .eq(AgentMemoryPO::getStatus, MemoryRecord.STATUS_ACTIVE)
          .lt(AgentMemoryPO::getCreateTime, cutoff)
          .set(AgentMemoryPO::getStatus, MemoryRecord.STATUS_ARCHIVED));
    } catch (Exception e) {
      log.warn("failed to archive expired ledgers", e);
      return 0;
    }
  }

  @Override
  public void bumpConfidence(Long id, double delta) {
    try {
      mapper.update(null, Wrappers.<AgentMemoryPO>lambdaUpdate()
          .eq(AgentMemoryPO::getId, id)
          .setSql("confidence = LEAST(1.0, confidence + " + delta + ")"));
    } catch (Exception e) {
      log.warn("failed to bump confidence: id={}", id, e);
    }
  }

  private static MemoryRecord toRecord(AgentMemoryPO po) {
    return new MemoryRecord(
        po.getId(),
        po.getScope(),
        po.getScopeKey(),
        po.getMemoryType(),
        po.getLayer(),
        po.getContent(),
        po.getKeywords(),
        po.getConfidence() == null ? 0.0 : po.getConfidence().doubleValue(),
        po.getHitCount() == null ? 0 : po.getHitCount(),
        po.getLastHitAt(),
        po.getSourceTurnId(),
        po.getStatus());
  }
}
