package io.yak.ops.business.sync.offline.engine.plan;

/** Control-plane execution shape for one frozen Offline Sync definition. */
public enum OfflineExecutionStrategy {
  SINGLE,
  NATIVE_MULTI,
  FAN_OUT
}
