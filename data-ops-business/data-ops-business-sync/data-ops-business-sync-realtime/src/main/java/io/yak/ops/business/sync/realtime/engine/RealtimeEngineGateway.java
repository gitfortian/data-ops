package io.yak.ops.business.sync.realtime.engine;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;

/** Yak Ops-side adapter for one explicit Flink CDC compute environment. */
public interface RealtimeEngineGateway {

  JsonNode health(ComputeEnvironmentSnapshot environment);

  JsonNode capabilities(ComputeEnvironmentSnapshot environment);

  ValidationResult validate(ComputeEnvironmentSnapshot environment, String pipelineYaml);

  DeployResult deploy(ComputeEnvironmentSnapshot environment, RealtimeDeployRequest request);

  RuntimeStatus status(ComputeEnvironmentSnapshot environment, String jobId);

  void stop(ComputeEnvironmentSnapshot environment, String jobId);

  record ValidationResult(boolean valid, String deliverySemantics) {}

  record DeployResult(String jobId, String deliverySemantics) {}

  record RuntimeStatus(String jobId, State state) {
    public enum State {
      NONE,
      RUNNING,
      TERMINATED,
      UNKNOWN
    }
  }
}
