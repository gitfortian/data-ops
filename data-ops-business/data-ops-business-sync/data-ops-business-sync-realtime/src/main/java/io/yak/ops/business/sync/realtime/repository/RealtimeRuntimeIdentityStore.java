package io.yak.ops.business.sync.realtime.repository;

import java.util.Optional;

/** Repository contract for deterministic Flink runtime identities. */
public interface RealtimeRuntimeIdentityStore {

  /** Must succeed before the Flink CDC CLI is started. */
  void bind(String idempotencyKey, String runtimeJobName);

  Optional<String> findByDeploymentId(long deploymentId);
}
