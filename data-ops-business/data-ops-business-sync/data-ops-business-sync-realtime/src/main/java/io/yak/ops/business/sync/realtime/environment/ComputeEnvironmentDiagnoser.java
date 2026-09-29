package io.yak.ops.business.sync.realtime.environment;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentDiagnosis;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.engine.FlinkRuntimeEnvironmentProbe;
import io.yak.ops.business.sync.realtime.repository.ComputeEnvironmentStore;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Diagnoses saved and preview Compute Environments without owning their lifecycle. */
@Component
public class ComputeEnvironmentDiagnoser {

  private final ComputeEnvironmentManager manager;
  private final ComputeEnvironmentStore store;
  private final ComputeEnvironmentConfigNormalizer normalizer;
  private final FlinkRuntimeEnvironmentProbe probe;

  public ComputeEnvironmentDiagnoser(
      ComputeEnvironmentManager manager,
      ComputeEnvironmentStore store,
      ComputeEnvironmentConfigNormalizer normalizer,
      FlinkRuntimeEnvironmentProbe probe) {
    this.manager = manager;
    this.store = store;
    this.normalizer = normalizer;
    this.probe = probe;
  }

  /** Diagnoses a saved environment and stores only the summary shown on its settings card. */
  public ComputeEnvironmentDiagnosis diagnose(long id) {
    ComputeEnvironment environment = manager.require(id);
    ComputeEnvironmentDiagnosis result =
        probe.diagnose(ComputeEnvironmentSnapshot.from(environment));
    store.saveDiagnosis(id, result.status(), result.summary(), result.checkedAt());
    return result;
  }

  /** Preview checks never write to the database. */
  public ComputeEnvironmentDiagnosis diagnosePreview(
      String name, String submitterType, RuntimeConfig config) {
    String normalizedSubmitter =
        normalizer.normalizeSubmitterType(submitterType, ComputeEnvironment.SUBMITTER_LOCAL);
    RuntimeConfig normalizedConfig = normalizer.normalizeConfig(config, normalizedSubmitter, false);
    String normalizedName =
        StringUtils.hasText(name) ? normalizer.normalizeName(name) : "未保存环境";
    ComputeEnvironmentSnapshot preview =
        new ComputeEnvironmentSnapshot(
            0L,
            normalizedName,
            ComputeEnvironment.ENGINE_FLINK_CDC,
            ComputeEnvironment.DEPLOYMENT_REMOTE,
            normalizedSubmitter,
            normalizedConfig,
            0);
    return probe.diagnose(preview);
  }
}
