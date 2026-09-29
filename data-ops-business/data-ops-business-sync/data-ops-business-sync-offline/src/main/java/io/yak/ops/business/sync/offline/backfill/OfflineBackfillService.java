package io.yak.ops.business.sync.offline.backfill;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.definition.OfflineJobDefinitionService;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.common.bean.dto.sync.offline.OfflineBackfillRequestDTO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineBackfillVO;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stable Backfill facade that locks the Task and materializes one planned PENDING Batch group. */
@ConditionalOnOfflineSyncEnabled
@Service
@RequiredArgsConstructor
public class OfflineBackfillService {

  private final OfflineJobDefinitionService definitionService;
  private final OfflineJobDefinitionRepository definitionRepository;
  private final OfflineBatchExecutionRepository batchRepository;
  private final OfflineBackfillPlanner planner;

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public OfflineBackfillVO submit(Long taskId, OfflineBackfillRequestDTO request) {
    long id = positive(taskId, "TaskId");
    OfflineBackfillPlanner.PreparedRequest prepared = planner.prepare(request);

    definitionRepository.lock(id);
    OfflineJobDefinition definition = definitionService.require(id);
    OfflineBackfillPlanner.Plan plan = planner.plan(id, definition, prepared);

    List<Long> batchIds = new ArrayList<>();
    int created = 0;
    int reused = 0;
    for (OfflineBackfillPlanner.ScopePlan scopePlan : plan.scopes()) {
      BatchExecution existing = plan.existing(scopePlan);
      if (existing != null) {
        batchIds.add(existing.id());
        reused++;
        continue;
      }

      BatchExecution saved = batchRepository.insert(newBackfillBatch(id, plan, scopePlan));
      if (saved.id() == null) {
        throw new IllegalStateException("创建 Backfill Batch 后缺少 ID");
      }
      batchIds.add(saved.id());
      created++;
    }

    return OfflineBackfillVO.builder()
        .jobDefinitionId(id)
        .requestId(plan.requestId())
        .batchIds(List.copyOf(batchIds))
        .createdCount(created)
        .reusedCount(reused)
        .build();
  }

  private BatchExecution newBackfillBatch(
      long taskId,
      OfflineBackfillPlanner.Plan plan,
      OfflineBackfillPlanner.ScopePlan scopePlan) {
    return new BatchExecution(
        null,
        taskId,
        BatchKey.backfill(plan.requestId(), scopePlan.scope().fingerprint()),
        BatchTrigger.BACKFILL,
        scopePlan.scope(),
        plan.snapshot(),
        BatchStatus.PENDING,
        List.of());
  }

  private long positive(Long value, String field) {
    if (value == null || value <= 0L) {
      throw new IllegalArgumentException(field + " 必须大于 0");
    }
    return value;
  }
}
