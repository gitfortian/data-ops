package io.yak.ops.business.development.handoff;

import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.common.version.VersionDigests;
import io.yak.ops.core.project.CurrentProject;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable, project-scoped reservation. The Security-owned target-write policy
 * must be explicitly installed; without it neither a reservation nor an SQL
 * Runtime submission can be created.
 */
@Component
@ConditionalOnProperty(prefix = "data-ops.dwd",
    name = {"binding-write-authorized", "execution-write-authorized"},
    havingValue = "true", matchIfMissing = false)
public class DwdFrozenExecutionReservationCoordinator {
  private final CurrentProject projects;
  private final DwdFrozenExecutionAttemptRepository attempts;
  private final DevelopmentModelTaskBindingRepository bindings;
  private final FrozenDwdExecutionCandidateService frozen;
  private final ObjectProvider<DwdFrozenExecutionTargetPolicy> targetPolicies;

  public DwdFrozenExecutionReservationCoordinator(
      CurrentProject projects, DwdFrozenExecutionAttemptRepository attempts,
      DevelopmentModelTaskBindingRepository bindings,
      FrozenDwdExecutionCandidateService frozen,
      ObjectProvider<DwdFrozenExecutionTargetPolicy> targetPolicies) {
    this.projects = projects;
    this.attempts = attempts;
    this.bindings = bindings;
    this.frozen = frozen;
    this.targetPolicies = targetPolicies;
  }

  record Reservation(DwdFrozenExecutionAttempt attempt,
                     TaskVersionSnapshot snapshot, boolean newlyReserved) {}

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Reservation reserve(long bindingId, String requestKey, boolean confirmed) {
    if (bindingId <= 0 || !confirmed || !validKey(requestKey)) {
      throw new IllegalArgumentException("需要真实绑定 ID、用户确认以及有效 UUID 幂等请求键");
    }
    long projectId = projects.requireProjectId();
    attempts.lockBinding(bindingId);
    var existing = attempts.byKey(requestKey);
    if (existing.isPresent()) {
      if (existing.get().bindingId() != bindingId || existing.get().projectId() != projectId) {
        throw new IllegalStateException("执行幂等键已经归属其他 DWD 绑定，禁止重新提交");
      }
      // A repeated request NEVER returns an executable snapshot, regardless
      // of whether the first caller died before/after an external submission.
      return new Reservation(existing.get(), null, false);
    }
    var candidate = frozen.prepare(bindingId);
    var binding = bindings.byId(bindingId).orElseThrow(
        () -> new IllegalStateException("绑定在预检期间已被撤销"));
    if (binding.projectId() != projectId
        || !Objects.equals(candidate.preview().bindingDigest(), binding.bindingDigest())) {
      throw new IllegalStateException("冻结绑定摘要变化，拒绝创建执行请求");
    }
    DwdFrozenExecutionTargetPolicy policy = targetPolicies.getIfAvailable();
    if (policy == null) {
      throw new IllegalStateException("缺少经过批准的 SQL 目标写入授权策略，拒绝正式执行");
    }
    policy.requireWriteAuthorization(projectId, binding, candidate.frozenSnapshot());
    String digest = VersionDigests.sha256Fields(
        String.valueOf(projectId), String.valueOf(bindingId),
        binding.bindingDigest(), requestKey);
    var saved = attempts.insert(new DwdFrozenExecutionAttempt(
        null, projectId, bindingId, binding.bindingDigest(), requestKey,
        digest, "RESERVED", null, null));
    return new Reservation(saved, candidate.frozenSnapshot(), true);
  }

  private static boolean validKey(String key) {
    if (key == null || key.length() != 36) return false;
    try { return UUID.fromString(key).toString().equals(key.toLowerCase()); }
    catch (IllegalArgumentException ignored) { return false; }
  }
}
