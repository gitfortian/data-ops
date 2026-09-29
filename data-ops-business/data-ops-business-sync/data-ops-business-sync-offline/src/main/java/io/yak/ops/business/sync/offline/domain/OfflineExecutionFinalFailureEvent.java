package io.yak.ops.business.sync.offline.domain;

/** Emitted after an Offline Sync attempt first enters FAILED with no retry scheduled. */
public record OfflineExecutionFinalFailureEvent(
    Long executionId,
    Long jobDefinitionId,
    String errorMessage) {
}
