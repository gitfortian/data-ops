package io.yak.ops.business.sync.realtime.repository.support;

import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.CheckpointPolicy;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.ExactTableSelector;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.ExecutionPolicy;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.FixedDelayRestart;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.NoRestart;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.ReplayKey;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.SchemaEvolutionPolicy;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.SinkEndpoint;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.SinkWritePolicy;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.SourceEndpoint;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.SourceSelector;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.StartupPolicy;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.SyncPolicy;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.SyncRoute;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.TablePatternSelector;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.TableTarget;
import java.util.List;
import org.springframework.stereotype.Component;

/** Compatibility mapper used while persistence still stores the legacy CdcPipelineSpec snapshot. */
@Component
public class CdcPipelineSpecCompatibilityMapper {

  public MappingResult toDomain(CdcPipelineSpec spec) {
    if (spec == null) throw new IllegalArgumentException("CdcPipelineSpec 不能为空");
    if (!spec.sink().strictReplaySafety()) {
      throw new IllegalArgumentException("Core Domain 不支持 strictReplaySafety=false");
    }

    List<SyncRoute> routes = spec.tables().stream().map(this::toRoute).toList();
    SyncDefinition definition =
        new SyncDefinition(
            new SourceEndpoint(spec.sourceDataSourceRef()),
            new SinkEndpoint(spec.sinkDataSourceRef()),
            routes,
            new SyncPolicy(startupPolicy(spec.startupMode()), schemaPolicy(spec.schemaEvolution())),
            new ExecutionPolicy(
                spec.parallelism(),
                new CheckpointPolicy(spec.checkpointIntervalMs()),
                restartPolicy(spec.restart()),
                new SinkWritePolicy(
                    spec.sink().maxRetries(),
                    spec.sink().batchSize(),
                    spec.sink().flushIntervalMs(),
                    spec.sink().maxBatchBytes())));

    return new MappingResult(
        definition, new LegacyAdapterTuning(spec.sink().statementCacheSize()));
  }

  public CdcPipelineSpec toLegacy(
      SyncDefinition definition, LegacyAdapterTuning adapterTuning) {
    if (definition == null) throw new IllegalArgumentException("SyncDefinition 不能为空");
    LegacyAdapterTuning tuning =
        adapterTuning == null ? new LegacyAdapterTuning(128) : adapterTuning;

    return new CdcPipelineSpec(
        definition.source().dataSourceRef(),
        definition.sink().dataSourceRef(),
        definition.routes().stream().map(this::toLegacyRoute).toList(),
        switch (definition.syncPolicy().startupPolicy()) {
          case INITIAL_AND_CONTINUOUS -> "initial";
          case CHANGES_ONLY -> "latest-offset";
        },
        CdcPipelineSpec.SchemaEvolution.valueOf(
            definition.syncPolicy().schemaEvolutionPolicy().name()),
        definition.executionPolicy().parallelism(),
        definition.executionPolicy().checkpointPolicy().intervalMs(),
        toLegacyRestart(definition.executionPolicy().restartPolicy()),
        new CdcPipelineSpec.SinkTuning(
            definition.executionPolicy().sinkWritePolicy().maxRetries(),
            definition.executionPolicy().sinkWritePolicy().batchSize(),
            definition.executionPolicy().sinkWritePolicy().flushIntervalMs(),
            definition.executionPolicy().sinkWritePolicy().maxBatchBytes(),
            tuning.statementCacheSize(),
            true));
  }

  private SyncRoute toRoute(CdcPipelineSpec.TableRoute route) {
    SourceSelector selector =
        switch (route.matchMode()) {
          case EXACT -> new ExactTableSelector(route.sourceTable());
          case REGEX -> new TablePatternSelector(route.sourceTable());
        };
    return new SyncRoute(
        selector, new TableTarget(route.sinkTable()), new ReplayKey(route.keyColumns()));
  }

  private CdcPipelineSpec.TableRoute toLegacyRoute(SyncRoute route) {
    CdcPipelineSpec.MatchMode mode;
    String expression;
    switch (route.source()) {
      case ExactTableSelector exact -> {
        mode = CdcPipelineSpec.MatchMode.EXACT;
        expression = exact.table();
      }
      case TablePatternSelector pattern -> {
        mode = CdcPipelineSpec.MatchMode.REGEX;
        expression = pattern.pattern();
      }
    }
    TableTarget target = (TableTarget) route.target();
    return new CdcPipelineSpec.TableRoute(
        expression, target.table(), mode, route.replayKey().fields());
  }

  private StartupPolicy startupPolicy(String value) {
    return switch (value) {
      case "initial" -> StartupPolicy.INITIAL_AND_CONTINUOUS;
      case "latest-offset" -> StartupPolicy.CHANGES_ONLY;
      default -> throw new IllegalArgumentException("不支持的 legacy startupMode：" + value);
    };
  }

  private SchemaEvolutionPolicy schemaPolicy(CdcPipelineSpec.SchemaEvolution value) {
    return SchemaEvolutionPolicy.valueOf(value.name());
  }

  private SyncDefinition.RestartPolicy restartPolicy(CdcPipelineSpec.RestartPolicy restart) {
    return switch (restart.strategy()) {
      case "none" -> new NoRestart();
      case "fixed-delay" -> new FixedDelayRestart(restart.attempts(), restart.delayMs());
      case "failure-rate" ->
          throw new UnsupportedLegacyDefinitionException(
              "legacy failure-rate restart 缺少完整 failure-rate window 语义，暂不能映射为 Core RestartPolicy");
      default ->
          throw new IllegalArgumentException("不支持的 legacy restart strategy：" + restart.strategy());
    };
  }

  private CdcPipelineSpec.RestartPolicy toLegacyRestart(
      SyncDefinition.RestartPolicy restartPolicy) {
    return switch (restartPolicy) {
      case NoRestart ignored -> new CdcPipelineSpec.RestartPolicy("none", 0, 0);
      case FixedDelayRestart fixed ->
          new CdcPipelineSpec.RestartPolicy(
              "fixed-delay", fixed.maxAttempts(), fixed.delayMs());
    };
  }

  public record MappingResult(
      SyncDefinition definition, LegacyAdapterTuning legacyAdapterTuning) {}

  /** JDBC-only compatibility value kept outside the Core SyncDefinition. */
  public record LegacyAdapterTuning(int statementCacheSize) {
    public LegacyAdapterTuning {
      if (statementCacheSize < 1) {
        throw new IllegalArgumentException("statementCacheSize 必须大于 0");
      }
    }
  }

  /** Known legacy shape that is intentionally not promoted into the Core Domain. */
  public static class UnsupportedLegacyDefinitionException extends IllegalArgumentException {
    public UnsupportedLegacyDefinitionException(String message) {
      super(message);
    }
  }
}
