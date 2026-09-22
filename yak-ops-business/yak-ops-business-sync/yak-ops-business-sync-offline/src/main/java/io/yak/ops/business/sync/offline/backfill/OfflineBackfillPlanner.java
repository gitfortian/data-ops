package io.yak.ops.business.sync.offline.backfill;

import io.yak.ops.common.enums.PublishState;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.config.OfflineSyncProperties;
import io.yak.ops.business.sync.offline.cursor.OfflineCursorGateway;
import io.yak.ops.business.sync.offline.definition.OfflineJobDefinitionService;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineSchedule;
import io.yak.ops.business.sync.offline.domain.OfflineSyncCursor;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.execution.OfflineExecutionScopeValidator;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import io.yak.ops.common.bean.dto.sync.offline.OfflineBackfillRequestDTO;
import io.yak.ops.common.bean.dto.sync.offline.OfflineBackfillScopeDTO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Plans Backfill Scope, Cursor and one shared frozen ExecutionSnapshot without persisting Batch rows. */
@ConditionalOnOfflineSyncEnabled
@Component
@RequiredArgsConstructor
public class OfflineBackfillPlanner {

  private static final Pattern SAFE_IDENTIFIER =
      Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*(\\.[A-Za-z_][A-Za-z0-9_$]*)*");

  private final OfflineJobDefinitionService definitionService;
  private final OfflineBatchExecutionRepository batchRepository;
  private final OfflineScheduleRepository scheduleRepository;
  private final OfflineCursorGateway cursorGateway;
  private final OfflineExecutionScopeValidator scopeValidator;
  private final OfflineSyncProperties properties;

  public PreparedRequest prepare(OfflineBackfillRequestDTO request) {
    if (request == null) {
      throw new IllegalArgumentException("Backfill request 不能为空");
    }
    String requestId = requireText(request.getRequestId(), "requestId 不能为空");
    if (request.getScopes() == null || request.getScopes().isEmpty()) {
      throw new IllegalArgumentException("scopes 不能为空");
    }
    return new PreparedRequest(requestId, List.copyOf(request.getScopes()));
  }

  public Plan plan(long taskId, OfflineJobDefinition definition, PreparedRequest request) {
    List<ScopePlan> scopes = normalizeScopes(request.scopes());
    ExistingBatches existing = findExistingBatches(taskId, request.requestId(), scopes);
    if (existing.hasMissing() && !PublishState.PUBLISHED.matches(definition.getReleaseState())) {
      throw new IllegalStateException("请先上线任务，再创建新的 Backfill Batch");
    }

    ExecutionSnapshot snapshot =
        resolveSharedSnapshot(definition, existing.byFingerprint().values().stream().toList());
    validateCursorPlans(
        taskId,
        scopes,
        existing.byFingerprint(),
        existing.hasMissing());
    validateExecutionScopes(taskId, scopes, snapshot.logicalJobSpec());

    return new Plan(
        request.requestId(),
        scopes,
        Collections.unmodifiableMap(new LinkedHashMap<>(existing.byFingerprint())),
        snapshot);
  }

  private ExistingBatches findExistingBatches(
      long taskId, String requestId, List<ScopePlan> scopes) {
    Map<String, BatchExecution> existingByFingerprint = new LinkedHashMap<>();
    boolean hasMissing = false;
    for (ScopePlan plan : scopes) {
      BatchKey key = BatchKey.backfill(requestId, plan.scope().fingerprint());
      BatchExecution existing = batchRepository.findByTaskIdAndBatchKey(taskId, key).orElse(null);
      if (existing == null) {
        hasMissing = true;
        continue;
      }
      validateExisting(existing, plan.scope());
      existingByFingerprint.put(plan.scope().fingerprint(), existing);
    }
    return new ExistingBatches(existingByFingerprint, hasMissing);
  }

  private ExecutionSnapshot resolveSharedSnapshot(
      OfflineJobDefinition definition, List<BatchExecution> existing) {
    if (existing.isEmpty()) {
      return freezeSnapshot(definition);
    }

    ExecutionSnapshot snapshot = existing.get(0).snapshot();
    for (BatchExecution batch : existing) {
      if (!snapshot.equals(batch.snapshot())) {
        throw new IllegalStateException("同一 Backfill request 已存在不一致的 ExecutionSnapshot");
      }
    }
    return snapshot;
  }

  private List<ScopePlan> normalizeScopes(List<OfflineBackfillScopeDTO> values) {
    LinkedHashMap<String, ScopePlan> unique = new LinkedHashMap<>();
    for (OfflineBackfillScopeDTO value : values) {
      if (value == null) {
        throw new IllegalArgumentException("scope 不能为空");
      }
      ScopePlan plan = normalizeScope(value);
      String fingerprint = plan.scope().fingerprint();
      ScopePlan duplicate = unique.get(fingerprint);
      if (duplicate != null && !Objects.equals(duplicate.cursorColumn(), plan.cursorColumn())) {
        throw new IllegalArgumentException("相同 BatchScope 不能绑定不同 cursorColumn");
      }
      unique.putIfAbsent(fingerprint, plan);
    }
    return List.copyOf(unique.values());
  }

  private ScopePlan normalizeScope(OfflineBackfillScopeDTO value) {
    String type = requireText(value.getType(), "scope.type 不能为空").toUpperCase(Locale.ROOT);
    return switch (type) {
      case "FULL_SELECTION" -> new ScopePlan(BatchScope.fullSelection(), null);
      case "DATA_WINDOW" -> dataWindow(value);
      case "PARTITION_SCOPE", "PARTITIONS" -> partitions(value);
      case "CURSOR_RANGE" -> cursorRange(value);
      default -> throw new IllegalArgumentException("未知 Backfill scope.type：" + type);
    };
  }

  private ScopePlan dataWindow(OfflineBackfillScopeDTO value) {
    if (value.getStartInclusive() == null || value.getEndExclusive() == null) {
      throw new IllegalArgumentException("DATA_WINDOW 需要 startInclusive / endExclusive");
    }
    return new ScopePlan(
        BatchScope.dataWindow(value.getStartInclusive(), value.getEndExclusive()), null);
  }

  private ScopePlan partitions(OfflineBackfillScopeDTO value) {
    if (value.getPartitions() == null || value.getPartitions().isEmpty()) {
      throw new IllegalArgumentException("PARTITION_SCOPE 需要 partitions");
    }
    return new ScopePlan(BatchScope.partitions(value.getPartitions()), null);
  }

  private ScopePlan cursorRange(OfflineBackfillScopeDTO value) {
    String cursorId = requireText(value.getCursorId(), "CURSOR_RANGE cursorId 不能为空");
    String sourceColumn =
        safeColumn(requireText(value.getCursorColumn(), "CURSOR_RANGE cursorColumn 不能为空"));
    return new ScopePlan(
        BatchScope.cursorRange(
            cursorId,
            requireText(value.getAfterExclusive(), "CURSOR_RANGE afterExclusive 不能为空"),
            requireText(value.getThroughInclusive(), "CURSOR_RANGE throughInclusive 不能为空")),
        sourceColumn);
  }

  private void validateCursorPlans(
      long taskId,
      List<ScopePlan> plans,
      Map<String, BatchExecution> existingByFingerprint,
      boolean hasMissing) {
    for (Map.Entry<String, List<ScopePlan>> entry : groupCursorPlans(plans).entrySet()) {
      validateCursorSequence(entry.getKey(), entry.getValue());
      initializeCursor(taskId, entry.getKey(), entry.getValue(), existingByFingerprint, hasMissing);
    }
  }

  private Map<String, List<ScopePlan>> groupCursorPlans(List<ScopePlan> plans) {
    Map<String, List<ScopePlan>> byCursor = new LinkedHashMap<>();
    for (ScopePlan plan : plans) {
      if (plan.scope() instanceof BatchScope.CursorRange range) {
        byCursor.computeIfAbsent(range.cursorId(), ignored -> new ArrayList<>()).add(plan);
      }
    }
    return byCursor;
  }

  private void validateCursorSequence(String cursorId, List<ScopePlan> plans) {
    String sourceColumn = plans.get(0).cursorColumn();
    for (int index = 1; index < plans.size(); index++) {
      ScopePlan previousPlan = plans.get(index - 1);
      ScopePlan currentPlan = plans.get(index);
      BatchScope.CursorRange previous = (BatchScope.CursorRange) previousPlan.scope();
      BatchScope.CursorRange current = (BatchScope.CursorRange) currentPlan.scope();
      if (!Objects.equals(sourceColumn, currentPlan.cursorColumn())) {
        throw new IllegalArgumentException("同一个 cursorId 不能绑定多个 sourceColumn：" + cursorId);
      }
      if (!previous.throughInclusive().equals(current.afterExclusive())) {
        throw new IllegalArgumentException("同一 Cursor 的 Backfill ranges 必须连续：" + cursorId);
      }
    }
  }

  private void initializeCursor(
      long taskId,
      String cursorId,
      List<ScopePlan> plans,
      Map<String, BatchExecution> existingByFingerprint,
      boolean hasMissing) {
    ScopePlan firstPlan = plans.get(0);
    BatchScope.CursorRange first = (BatchScope.CursorRange) firstPlan.scope();
    OfflineSyncCursor existingCursor = cursorGateway.find(taskId, cursorId).orElse(null);
    if (existingCursor != null
        && hasMissing
        && !existingCursor.position().equals(first.afterExclusive())
        && !allBatchesExist(plans, existingByFingerprint)) {
      throw new IllegalStateException(
          "Cursor 已离开本 Backfill 起点，禁止追加会改变 Cursor 顺序的新 Batch：" + cursorId);
    }

    cursorGateway.initializeIfAbsent(
        taskId, cursorId, firstPlan.cursorColumn(), first.afterExclusive());
  }

  private boolean allBatchesExist(
      List<ScopePlan> plans, Map<String, BatchExecution> existingByFingerprint) {
    return plans.stream()
        .allMatch(plan -> existingByFingerprint.containsKey(plan.scope().fingerprint()));
  }

  private void validateExecutionScopes(
      long taskId, List<ScopePlan> plans, String logicalJobSpec) {
    for (ScopePlan plan : plans) {
      scopeValidator.validate(taskId, logicalJobSpec, plan.scope());
    }
  }

  private ExecutionSnapshot freezeSnapshot(OfflineJobDefinition definition) {
    String logicalJobSpec = definitionService.resolveLogicalJobSpec(definition);
    OfflineSchedule schedule = scheduleRepository.findSchedule(definition.getId());
    int maxAttempts =
        schedule == null
            ? properties.getControl().getDefaultMaxAttempts()
            : schedule.retryMaxAttempts();
    int backoff =
        schedule == null
            ? properties.getControl().getDefaultRetryBackoffSeconds()
            : schedule.retryBackoffSeconds();
    return new ExecutionSnapshot(
        requireText(definition.getDefinitionJson(), "definitionSnapshot 不能为空"),
        Math.max(1, definition.getVersion() == null ? 1 : definition.getVersion()),
        new RetryPolicySnapshot(Math.max(1, maxAttempts), Math.max(0, backoff)),
        requireText(definition.getConfigDigest(), "configDigest 不能为空"),
        logicalJobSpec);
  }

  private void validateExisting(BatchExecution batch, BatchScope scope) {
    if (batch.trigger() != BatchTrigger.BACKFILL) {
      throw new IllegalStateException("Backfill BatchKey 已被非 BACKFILL Batch 占用");
    }
    if (!batch.batchScope().fingerprint().equals(scope.fingerprint())) {
      throw new IllegalStateException("Backfill BatchKey 与 BatchScope 不一致");
    }
  }

  private String safeColumn(String value) {
    if (!SAFE_IDENTIFIER.matcher(value).matches()) {
      throw new IllegalArgumentException("cursorColumn 不是安全标识符：" + value);
    }
    return value;
  }

  private String requireText(String value, String message) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  private record ExistingBatches(
      Map<String, BatchExecution> byFingerprint, boolean hasMissing) {}

  public record PreparedRequest(String requestId, List<OfflineBackfillScopeDTO> scopes) {}

  public record ScopePlan(BatchScope scope, String cursorColumn) {}

  public record Plan(
      String requestId,
      List<ScopePlan> scopes,
      Map<String, BatchExecution> existingByFingerprint,
      ExecutionSnapshot snapshot) {

    public BatchExecution existing(ScopePlan scope) {
      return existingByFingerprint.get(scope.scope().fingerprint());
    }
  }
}
