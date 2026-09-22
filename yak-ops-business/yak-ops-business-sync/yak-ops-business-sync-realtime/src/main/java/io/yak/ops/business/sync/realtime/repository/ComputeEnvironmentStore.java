package io.yak.ops.business.sync.realtime.repository;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Domain repository contract for realtime compute environments. */
public interface ComputeEnvironmentStore {

  List<ComputeEnvironment> list();

  Optional<ComputeEnvironment> find(long id);

  Optional<ComputeEnvironment> defaultEnvironment();

  long insert(
      String name,
      String engineType,
      String deploymentMode,
      String submitterType,
      RuntimeConfig config,
      boolean enabled,
      boolean defaultEnvironment);

  void update(long id, String name, String submitterType, RuntimeConfig config, boolean enabled);

  void setEnabled(long id, boolean enabled);

  void saveDiagnosis(long id, String status, String message, LocalDateTime checkedAt);

  void clearDefault();

  void setDefault(long id);

  void delete(long id);

  boolean hasRuntimeEnvironmentReferences(long id);
}
