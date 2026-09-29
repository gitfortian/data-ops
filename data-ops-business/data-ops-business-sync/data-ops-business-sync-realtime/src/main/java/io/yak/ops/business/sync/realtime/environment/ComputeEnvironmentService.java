package io.yak.ops.business.sync.realtime.environment;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentDiagnosis;
import java.util.List;
import org.springframework.stereotype.Service;

/** Stable application entry for realtime Compute Environment use-cases. */
@Service("computeEnvironmentApplicationService")
public class ComputeEnvironmentService {

  private final ComputeEnvironmentManager manager;
  private final ComputeEnvironmentDiagnoser diagnoser;

  public ComputeEnvironmentService(
      ComputeEnvironmentManager manager, ComputeEnvironmentDiagnoser diagnoser) {
    this.manager = manager;
    this.diagnoser = diagnoser;
  }

  public List<ComputeEnvironment> list() {
    return manager.list();
  }

  public ComputeEnvironment get(long id) {
    return manager.get(id);
  }

  public long create(
      String name,
      String submitterType,
      RuntimeConfig config,
      boolean enabled,
      boolean makeDefault) {
    return manager.create(name, submitterType, config, enabled, makeDefault);
  }

  public ComputeEnvironmentDiagnosis diagnose(long id) {
    return diagnoser.diagnose(id);
  }

  public ComputeEnvironmentDiagnosis diagnosePreview(
      String name, String submitterType, RuntimeConfig config) {
    return diagnoser.diagnosePreview(name, submitterType, config);
  }

  public void update(
      long id,
      String name,
      String submitterType,
      RuntimeConfig config,
      boolean enabled,
      boolean makeDefault) {
    manager.update(id, name, submitterType, config, enabled, makeDefault);
  }

  public void setEnabled(long id, boolean enabled) {
    manager.setEnabled(id, enabled);
  }

  public void setDefault(long id) {
    manager.setDefault(id);
  }

  public void delete(long id) {
    manager.delete(id);
  }
}
