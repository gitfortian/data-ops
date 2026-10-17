package io.yak.ops.business.development.handoff;

import io.yak.ops.business.job.task.TaskExecutionGateway;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Opt-in published SQL Runtime dispatch. NEVER retries a reserved, dispatching,
 * unknown, or acknowledged request. A crash after dispatch but before receipt
 * is deliberately UNKNOWN rather than an excuse to submit SQL twice.
 *
 * The security-owned target-write policy is checked inside the committed
 * reservation transaction, and the runtime sees only the frozen published
 * TaskVersionSnapshot. No editor SQL or browser supplied SQL can enter here.
 */
@Component
@ConditionalOnProperty(prefix = "data-ops.dwd",
    name = {"binding-write-authorized", "execution-write-authorized"},
    havingValue = "true", matchIfMissing = false)
public class DwdFrozenExecutionDispatchCoordinator {
  private final CurrentProject projects;
  private final DwdFrozenExecutionReservationCoordinator reservations;
  private final DwdFrozenExecutionAttemptRepository attempts;
  private final TaskExecutionGateway runtime;

  public DwdFrozenExecutionDispatchCoordinator(
      CurrentProject projects,
      DwdFrozenExecutionReservationCoordinator reservations,
      DwdFrozenExecutionAttemptRepository attempts,
      TaskExecutionGateway runtime) {
    this.projects = projects;
    this.reservations = reservations;
    this.attempts = attempts;
    this.runtime = runtime;
  }

  public DwdFrozenExecutionAttempt submit(long bindingId, String requestKey, boolean confirmed) {
    var reserved = reservations.reserve(bindingId, requestKey, confirmed);
    if (!reserved.newlyReserved()) return reserved.attempt();
    long attemptId = reserved.attempt().id();
    if (!attempts.markDispatching(attemptId)) {
      // Once a reservation has been taken by another process, there is no
      // safe automatic retry of an external target write.
      return read(attemptId);
    }
    try {
      if (!runtime.supports("SQL")) {
        attempts.markUnknown(attemptId);
        return read(attemptId);
      }
      var response = runtime.start(
          reserved.snapshot(), TaskExecutionTrigger.MANUAL,
          "dwd:" + reserved.attempt().projectId() + ":" + reserved.attempt().requestKey(),
          Map.of("bindingId", String.valueOf(bindingId),
              "executionAttemptId", String.valueOf(attemptId),
              "modelTaskBindingDigest", reserved.attempt().bindingDigest()));
      attempts.acknowledge(attemptId, response.executionId(),
          response.status() == null ? "UNKNOWN" : response.status());
    } catch (Exception uncertain) {
      // The Runtime may have accepted the SQL before a timeout/exception.
      // Do not leak SQL in errors or reuse this request key to start again.
      attempts.markUnknown(attemptId);
    }
    return read(attemptId);
  }

  public DwdFrozenExecutionAttempt read(long attemptId) {
    if (attemptId <= 0) throw new IllegalArgumentException("非法执行请求 ID");
    projects.requireProjectId();
    return attempts.byId(attemptId).orElseThrow(
        () -> new IllegalArgumentException("当前项目没有指定的冻结执行请求"));
  }
}
