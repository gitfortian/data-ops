package io.yak.ops.business.audit;

import java.util.ArrayList;
import java.util.List;

/** Request-thread buffer for ALLOW decisions that should follow the next business AuditOperation. */
final class AuditAuthorizationDecisionContext {

  private static final ThreadLocal<List<AuditAuthorizationDecision>> DECISIONS = new ThreadLocal<>();

  private AuditAuthorizationDecisionContext() {}

  static void defer(AuditAuthorizationDecision decision) {
    if (decision == null) return;
    List<AuditAuthorizationDecision> decisions = DECISIONS.get();
    if (decisions == null) {
      decisions = new ArrayList<>();
      DECISIONS.set(decisions);
    }
    decisions.add(decision);
  }

  static List<AuditAuthorizationDecision> drain() {
    List<AuditAuthorizationDecision> decisions = DECISIONS.get();
    DECISIONS.remove();
    return decisions == null || decisions.isEmpty() ? List.of() : List.copyOf(decisions);
  }

  static void clear() {
    DECISIONS.remove();
  }
}
