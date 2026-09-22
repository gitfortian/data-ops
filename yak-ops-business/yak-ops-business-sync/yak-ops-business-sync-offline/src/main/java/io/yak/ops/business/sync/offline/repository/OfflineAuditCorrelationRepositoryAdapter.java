package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineBatchExecutionDao;
import io.yak.ops.common.bean.po.sync.offline.OfflineBatchExecutionPO;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** Stores AuditCarrier correlation without rewriting Batch runtime truth. */
@ConditionalOnOfflineSyncEnabled
@Repository
@RequiredArgsConstructor
public class OfflineAuditCorrelationRepositoryAdapter
    implements OfflineAuditCorrelationRepository {

  private final OfflineBatchExecutionDao batchDao;

  @Override
  public Optional<String> findCarrierJson(long batchId) {
    if (batchId <= 0L) return Optional.empty();
    OfflineBatchExecutionPO batch = batchDao.selectById(batchId);
    if (batch == null || !StringUtils.hasText(batch.getAuditCarrierJson())) {
      return Optional.empty();
    }
    return Optional.of(batch.getAuditCarrierJson().trim());
  }

  @Override
  public boolean updateCarrierJson(long batchId, String carrierJson) {
    if (batchId <= 0L || !StringUtils.hasText(carrierJson)) return false;
    return batchDao.updateAuditCarrier(batchId, carrierJson.trim(), LocalDateTime.now());
  }
}
