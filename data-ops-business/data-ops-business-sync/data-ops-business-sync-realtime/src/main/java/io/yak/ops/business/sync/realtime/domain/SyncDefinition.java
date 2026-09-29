package io.yak.ops.business.sync.realtime.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Engine-neutral realtime synchronization definition.
 *
 * <p>This is the target Core Domain value object introduced by Stage 6 Wave 0. Existing REST/YAML
 * compatibility continues to use {@link CdcPipelineSpec} until later migration waves.
 */
public record SyncDefinition(
    SourceEndpoint source,
    SinkEndpoint sink,
    List<SyncRoute> routes,
    SyncPolicy syncPolicy,
    ExecutionPolicy executionPolicy) {

  public SyncDefinition {
    source = Objects.requireNonNull(source, "SourceEndpoint 不能为空");
    sink = Objects.requireNonNull(sink, "SinkEndpoint 不能为空");
    routes = routes == null ? List.of() : List.copyOf(routes);
    if (routes.isEmpty()) {
      throw new IllegalArgumentException("SyncDefinition 至少需要一条 SyncRoute");
    }
    if (source.dataSourceRef() == sink.dataSourceRef()) {
      throw new IllegalArgumentException("Source 与 Sink 不能引用同一个数据源");
    }
    syncPolicy = Objects.requireNonNull(syncPolicy, "SyncPolicy 不能为空");
    executionPolicy = Objects.requireNonNull(executionPolicy, "ExecutionPolicy 不能为空");
  }

  public record SourceEndpoint(long dataSourceRef) {
    public SourceEndpoint {
      if (dataSourceRef <= 0) throw new IllegalArgumentException("Source DataSourceRef 必须大于 0");
    }
  }

  public record SinkEndpoint(long dataSourceRef) {
    public SinkEndpoint {
      if (dataSourceRef <= 0) throw new IllegalArgumentException("Sink DataSourceRef 必须大于 0");
    }
  }

  public record SyncRoute(SourceSelector source, SinkTarget target, ReplayKey replayKey) {
    public SyncRoute {
      source = Objects.requireNonNull(source, "SourceSelector 不能为空");
      target = Objects.requireNonNull(target, "SinkTarget 不能为空");
      replayKey = Objects.requireNonNull(replayKey, "ReplayKey 不能为空");
    }
  }

  public sealed interface SourceSelector permits ExactTableSelector, TablePatternSelector {
    String expression();
  }

  public record ExactTableSelector(String table) implements SourceSelector {
    public ExactTableSelector {
      table = requireText(table, "Exact table");
    }

    @Override
    public String expression() {
      return table;
    }
  }

  public record TablePatternSelector(String pattern) implements SourceSelector {
    public TablePatternSelector {
      pattern = requireText(pattern, "Table pattern");
    }

    @Override
    public String expression() {
      return pattern;
    }
  }

  public sealed interface SinkTarget permits TableTarget {}

  public record TableTarget(String table) implements SinkTarget {
    public TableTarget {
      table = requireText(table, "Sink table");
    }
  }

  public record ReplayKey(List<String> fields) {
    public ReplayKey {
      fields = fields == null ? List.of() : fields.stream().map(String::trim).toList();
      if (fields.isEmpty() || fields.stream().anyMatch(String::isBlank)) {
        throw new IllegalArgumentException("ReplayKey 必须包含至少一个非空字段");
      }
      Set<String> unique = new HashSet<>(fields);
      if (unique.size() != fields.size()) {
        throw new IllegalArgumentException("ReplayKey 字段不能重复");
      }
      fields = List.copyOf(fields);
    }
  }

  public record SyncPolicy(
      StartupPolicy startupPolicy, SchemaEvolutionPolicy schemaEvolutionPolicy) {
    public SyncPolicy {
      startupPolicy = Objects.requireNonNull(startupPolicy, "StartupPolicy 不能为空");
      schemaEvolutionPolicy =
          Objects.requireNonNull(schemaEvolutionPolicy, "SchemaEvolutionPolicy 不能为空");
    }
  }

  public enum StartupPolicy {
    INITIAL_AND_CONTINUOUS,
    CHANGES_ONLY
  }

  public enum SchemaEvolutionPolicy {
    EVOLVE,
    IGNORE,
    FAIL
  }

  public record ExecutionPolicy(
      int parallelism,
      CheckpointPolicy checkpointPolicy,
      RestartPolicy restartPolicy,
      SinkWritePolicy sinkWritePolicy) {
    public ExecutionPolicy {
      if (parallelism < 1) throw new IllegalArgumentException("Parallelism 必须大于 0");
      checkpointPolicy = Objects.requireNonNull(checkpointPolicy, "CheckpointPolicy 不能为空");
      restartPolicy = Objects.requireNonNull(restartPolicy, "RestartPolicy 不能为空");
      sinkWritePolicy = Objects.requireNonNull(sinkWritePolicy, "SinkWritePolicy 不能为空");
    }
  }

  public record CheckpointPolicy(long intervalMs) {
    public CheckpointPolicy {
      if (intervalMs < 10_000) {
        throw new IllegalArgumentException("Checkpoint interval 不能小于 10000ms");
      }
    }
  }

  public sealed interface RestartPolicy permits NoRestart, FixedDelayRestart {}

  public record NoRestart() implements RestartPolicy {}

  public record FixedDelayRestart(int maxAttempts, long delayMs) implements RestartPolicy {
    public FixedDelayRestart {
      if (maxAttempts < 0 || delayMs < 0) {
        throw new IllegalArgumentException("FixedDelayRestart 参数不能小于 0");
      }
    }
  }

  public record SinkWritePolicy(
      int maxRetries, int batchSize, long flushIntervalMs, long maxBatchBytes) {
    public SinkWritePolicy {
      if (maxRetries < 0 || batchSize < 1 || flushIntervalMs < 1 || maxBatchBytes < 1) {
        throw new IllegalArgumentException("SinkWritePolicy 参数无效");
      }
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " 不能为空");
    }
    return value.trim();
  }
}
