package io.yak.ops.business.approval.registry;

import io.yak.ops.business.approval.api.ApprovalFlowHandler;
import io.yak.ops.business.approval.exception.ApprovalException;
import io.yak.ops.common.enums.approval.ApprovalErrorCode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 收集容器内全部 {@link ApprovalFlowHandler} 按 flowCode 索引(同 AssetProviderRegistry 范式)。
 * 启动不强制存在(允许流程先行配置),发起时才要求。
 */
@Component
public class ApprovalFlowRegistry {

  private final ObjectProvider<ApprovalFlowHandler> discovered;
  /** Built on first lookup so callback implementations cannot form a startup bean cycle. */
  private volatile Map<String, ApprovalFlowHandler> handlers;

  public ApprovalFlowRegistry(ObjectProvider<ApprovalFlowHandler> discovered) {
    this.discovered = discovered;
  }

  public Optional<ApprovalFlowHandler> find(String flowCode) {
    return Optional.ofNullable(handlers().get(flowCode));
  }

  /** 发起时校验:无 handler 的流程批完也没人生效 → 49007. */
  public ApprovalFlowHandler require(String flowCode) {
    return find(flowCode).orElseThrow(() ->
        new ApprovalException(ApprovalErrorCode.HANDLER_NOT_REGISTERED, flowCode));
  }

  private Map<String, ApprovalFlowHandler> handlers() {
    Map<String, ApprovalFlowHandler> current = handlers;
    if (current != null) {
      return current;
    }
    synchronized (this) {
      current = handlers;
      if (current == null) {
        Map<String, ApprovalFlowHandler> discoveredHandlers = new HashMap<>();
        discovered.stream().forEach(handler -> {
          ApprovalFlowHandler previous = discoveredHandlers.putIfAbsent(handler.flowCode(), handler);
          if (previous != null) {
            throw new IllegalStateException("Duplicate ApprovalFlowHandler for " + handler.flowCode()
                + ": " + previous.getClass().getName() + " / " + handler.getClass().getName());
          }
        });
        current = Map.copyOf(discoveredHandlers);
        handlers = current;
      }
    }
    return current;
  }
}
