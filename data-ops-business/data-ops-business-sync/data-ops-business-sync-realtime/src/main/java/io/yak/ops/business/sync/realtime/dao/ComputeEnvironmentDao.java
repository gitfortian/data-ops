package io.yak.ops.business.sync.realtime.dao;

import io.yak.ops.business.sync.realtime.dao.model.ComputeEnvironmentPO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ComputeEnvironmentDao {
  List<ComputeEnvironmentPO> list();
  Optional<ComputeEnvironmentPO> find(long id);
  Optional<ComputeEnvironmentPO> defaultEnvironment();
  long insert(ComputeEnvironmentPO environment);
  int update(long id, String name, String submitterType, String configJson, boolean enabled);
  int setEnabled(long id, boolean enabled);
  int saveDiagnosis(long id, String status, String message, LocalDateTime checkedAt);
  void clearDefault();
  int setDefault(long id);
  int delete(long id);
  boolean hasRuntimeEnvironmentReferences(long id);
}
