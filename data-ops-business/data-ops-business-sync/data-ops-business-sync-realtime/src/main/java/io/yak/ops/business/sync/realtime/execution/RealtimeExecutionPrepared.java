package io.yak.ops.business.sync.realtime.execution;

import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler.CompiledPipeline;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.PublishedDefinitionRow;

/** Frozen command-time preparation used from validation through execution reservation. */
record RealtimeExecutionPrepared(
    DefinitionRow task,
    PublishedDefinitionRow definitionVersion,
    CdcPipelineSpec spec,
    CompiledPipeline compiled,
    ComputeEnvironmentSnapshot runtimeEnvironment) {}
