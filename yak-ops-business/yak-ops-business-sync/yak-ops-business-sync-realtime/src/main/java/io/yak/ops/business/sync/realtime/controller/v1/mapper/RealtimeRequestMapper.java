package io.yak.ops.business.sync.realtime.controller.v1.mapper;

import io.yak.ops.business.sync.realtime.controller.v1.dto.ComputeEnvironmentRequests;
import io.yak.ops.business.sync.realtime.controller.v1.dto.RealtimeJobRequests;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RealtimeRequestMapper {

  public CdcPipelineSpec toSpec(RealtimeJobRequests.PipelineSpec value) {
    if (value == null) return null;
    return new CdcPipelineSpec(
        value.sourceDataSourceRef(),
        value.sinkDataSourceRef(),
        value.tables() == null ? List.of() : value.tables().stream().map(this::toRoute).toList(),
        value.startupMode(),
        value.schemaEvolution() == null ? null : CdcPipelineSpec.SchemaEvolution.valueOf(value.schemaEvolution().name()),
        value.parallelism(),
        value.checkpointIntervalMs(),
        value.restart() == null ? null : new CdcPipelineSpec.RestartPolicy(value.restart().strategy(), value.restart().attempts(), value.restart().delayMs()),
        value.sink() == null ? null : new CdcPipelineSpec.SinkTuning(
            value.sink().maxRetries(), value.sink().batchSize(), value.sink().flushIntervalMs(),
            value.sink().maxBatchBytes(), value.sink().statementCacheSize(), value.sink().strictReplaySafety()));
  }

  private CdcPipelineSpec.TableRoute toRoute(RealtimeJobRequests.TableRoute value) {
    return new CdcPipelineSpec.TableRoute(
        value.sourceTable(), value.sinkTable(),
        value.matchMode() == null ? null : CdcPipelineSpec.MatchMode.valueOf(value.matchMode().name()),
        value.keyColumns());
  }

  public ComputeEnvironment.RuntimeConfig toRuntimeConfig(ComputeEnvironmentRequests.RuntimeConfig value) {
    if (value == null) return null;
    return new ComputeEnvironment.RuntimeConfig(
        value.restUrl(), value.flinkHome(), value.flinkCdcHome(), value.javaHome(),
        value.flinkVersion(), value.flinkCdcVersion(), toSsh(value.ssh()));
  }

  private ComputeEnvironment.SshConfig toSsh(ComputeEnvironmentRequests.SshConfig value) {
    if (value == null) return null;
    return new ComputeEnvironment.SshConfig(
        value.executable(), value.host(), value.port(), value.user(), value.identityFile(),
        value.knownHostsFile(), value.strictHostKeyChecking(), value.connectTimeoutSeconds(),
        value.remoteRestAddress(), value.remoteRestPort());
  }
}
