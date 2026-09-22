package io.yak.ops.business.workflow.execution;

import io.yak.ops.business.workflow.runtime.WorkflowRuntime;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 同一个 WorkflowExecution 的人工恢复入口。
 *
 * <p>retry / continue 与 restart 不同：它们不会创建新的 WorkflowExecution，而是可能把一个已经终态的
 * Execution 重新拉回 RUNNING。数据库模式下必须先经过 durable reactivation guard，避免绕过
 * SERIAL_WAIT / SERIAL_DISCARD 的工作流级并发语义；database-disabled 的 focused/dev 模式保持 Runtime 直连。</p>
 */
@Service
public class WorkflowExecutionReactivator {
  private final WorkflowRuntime runtime;
  private final ObjectProvider<WorkflowExecutionReactivationGuard> guard;
  private final WorkflowExecutionAuditBridge auditBridge;

  @Autowired
  public WorkflowExecutionReactivator(
      WorkflowRuntime runtime,
      ObjectProvider<WorkflowExecutionReactivationGuard> guard,
      ObjectProvider<WorkflowExecutionAuditBridge> auditBridgeProvider) {
    this.runtime = runtime;
    this.guard = guard;
    this.auditBridge = auditBridgeProvider.getIfAvailable();
  }

  /** Focused compatibility constructor without Audit wiring. */
  public WorkflowExecutionReactivator(
      WorkflowRuntime runtime,
      ObjectProvider<WorkflowExecutionReactivationGuard> guard) {
    this.runtime = runtime;
    this.guard = guard;
    this.auditBridge = null;
  }

  public WorkflowInstanceVO continueAfterFailure(String executionId, String nodeId) {
    String id = required(executionId, "工作流实例 ID 不能为空");
    String node = required(nodeId, "工作流节点 ID 不能为空");
    return reactivate(
        id,
        "CONTINUE_AFTER_FAILURE",
        node,
        () -> runtime.continueAfterFailure(id, node));
  }

  public WorkflowInstanceVO retryFailedNode(String executionId, String nodeId) {
    String id = required(executionId, "工作流实例 ID 不能为空");
    String node = required(nodeId, "工作流节点 ID 不能为空");
    return reactivate(
        id,
        "RETRY_FAILED_NODE",
        node,
        () -> runtime.retryFailedNode(id, node));
  }

  public WorkflowInstanceVO retryFailedNodes(String executionId) {
    String id = required(executionId, "工作流实例 ID 不能为空");
    return reactivate(
        id,
        "RETRY_FAILED_NODES",
        null,
        () -> runtime.retryFailedNodes(id));
  }

  private WorkflowInstanceVO reactivate(
      String executionId,
      String operation,
      String nodeId,
      Supplier<WorkflowInstanceVO> action) {
    Supplier<WorkflowInstanceVO> guarded = () -> {
      WorkflowExecutionReactivationGuard value = guard.getIfAvailable();
      if (value == null) return action.get();
      return value.reactivateExecution(executionId, operation, action);
    };
    if (auditBridge == null) return guarded.get();
    return auditBridge.reactivate(executionId, operation, nodeId, guarded);
  }

  private String required(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    return value.trim();
  }
}
