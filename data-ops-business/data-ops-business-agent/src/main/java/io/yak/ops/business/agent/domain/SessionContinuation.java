package io.yak.ops.business.agent.domain;

/** Read-only latest-turn projection. Null status means no persisted turn; null reason means unblocked. */
public record SessionContinuation(
    String sessionId,
    String turnId,
    TurnStatus status,
    GovernanceTarget governanceTarget,
    Clarification clarification,
    String blockingReason) {

  /** Reproduces a question for display; SDK pending and resume CAS remain authoritative. */
  public record Clarification(String toolCallId, String toolName, String question) {}
}
