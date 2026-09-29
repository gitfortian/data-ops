package io.yak.ops.business.sync.realtime.execution;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpecValidator;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler;
import io.yak.ops.business.sync.realtime.engine.RealtimeConnectorCapabilityResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDataSourceResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDeployRequest;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.engine.ResolvedCdcPipeline;
import io.yak.ops.business.sync.realtime.environment.RealtimeRuntimeResolver;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.PublishedDefinitionRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Resolves and freezes the DefinitionVersion, runtime environment and compiled execution artifact. */
@Component
public class RealtimeExecutionPreparation {

  private static final String ARTIFACT_DIGEST_ALGORITHM = "SHA-256";

  private final RealtimeJobStore store;
  private final CdcPipelineSpecValidator specValidator;
  private final RealtimeDataSourceResolver dataSourceResolver;
  private final RealtimeConnectorCapabilityResolver capabilityResolver;
  private final PipelineYamlCompiler compiler;
  private final RealtimeEngineGateway gateway;
  private final RealtimeRuntimeResolver runtimeResolver;

  public RealtimeExecutionPreparation(
      RealtimeJobStore store,
      CdcPipelineSpecValidator specValidator,
      RealtimeDataSourceResolver dataSourceResolver,
      RealtimeConnectorCapabilityResolver capabilityResolver,
      PipelineYamlCompiler compiler,
      RealtimeEngineGateway gateway,
      RealtimeRuntimeResolver runtimeResolver) {
    this.store = store;
    this.specValidator = specValidator;
    this.dataSourceResolver = dataSourceResolver;
    this.capabilityResolver = capabilityResolver;
    this.compiler = compiler;
    this.gateway = gateway;
    this.runtimeResolver = runtimeResolver;
  }

  RealtimeExecutionPrepared preparePublished(long taskId) {
    return prepareVersion(taskId, requirePublishedDefinition(taskId));
  }

  RealtimeExecutionPrepared prepareVersion(long taskId, long definitionVersionId) {
    return prepareVersion(taskId, requireDefinitionVersion(taskId, definitionVersionId));
  }

  RealtimeExecutionPrepared prepareVersion(
      long taskId, PublishedDefinitionRow definitionVersion) {
    DefinitionRow task =
        store.definition(taskId)
            .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + taskId));
    if (definitionVersion.taskId() != taskId) {
      throw new IllegalStateException("DefinitionVersion 不属于当前实时同步任务");
    }

    CdcPipelineSpec spec = definitionVersion.spec();
    specValidator.validate(spec);
    ComputeEnvironmentSnapshot runtimeEnvironment =
        runtimeResolver.environment(definitionVersion.runtimeEnvironmentId(), true);
    ResolvedCdcPipeline resolved = dataSourceResolver.resolve(spec);
    JsonNode manifest = gateway.capabilities(runtimeEnvironment);
    capabilityResolver.requireSupported(manifest, resolved, spec);

    return new RealtimeExecutionPrepared(
        task,
        definitionVersion,
        spec,
        compiler.compile(task.name(), spec, resolved),
        runtimeEnvironment);
  }

  PublishedDefinitionRow requirePublishedDefinition(long taskId) {
    return store
        .publishedDefinition(taskId)
        .orElseThrow(() -> new IllegalStateException("请先发布至少一个可运行的定义版本"));
  }

  PublishedDefinitionRow requireDefinitionVersion(long taskId, long definitionVersionId) {
    return store
        .definitionVersion(taskId, definitionVersionId)
        .orElseThrow(
            () -> new IllegalStateException("DefinitionVersion 不存在：" + definitionVersionId));
  }

  void validate(RealtimeExecutionPrepared prepared) {
    gateway.validate(prepared.runtimeEnvironment(), prepared.compiled().yaml());
  }

  void requireCurrent(
      RealtimeExecutionPrepared prepared,
      PublishedDefinitionRow current,
      String message) {
    PublishedDefinitionRow snapshot = prepared.definitionVersion();
    if (snapshot.id() != current.id()
        || snapshot.taskId() != current.taskId()
        || snapshot.sourceDraftRevision() != current.sourceDraftRevision()
        || !Objects.equals(snapshot.sourceConfigDigest(), current.sourceConfigDigest())) {
      throw new IllegalStateException(message);
    }
  }

  String artifactDigest(RealtimeExecutionPrepared prepared) {
    try {
      byte[] bytes =
          MessageDigest.getInstance(ARTIFACT_DIGEST_ALGORITHM)
              .digest(prepared.compiled().yaml().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("运行环境不支持 SHA-256 摘要", exception);
    }
  }

  RealtimeEngineGateway.DeployResult deploy(RealtimeExecutionPrepared prepared, String key) {
    RealtimeDeployRequest.CredentialBindings credentials =
        dataSourceResolver.resolveCredentials(prepared.spec());
    try (RealtimeDeployRequest request =
        new RealtimeDeployRequest(prepared.compiled().yaml(), key, credentials)) {
      return gateway.deploy(prepared.runtimeEnvironment(), request);
    }
  }

  ComputeEnvironmentSnapshot deploymentRuntime(long taskId, DeploymentRow deployment) {
    if (deployment == null) {
      throw new IllegalStateException("任务尚无 Execution 记录");
    }
    DefinitionRow definition =
        store.definition(taskId)
            .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + taskId));
    return runtimeResolver.deployment(definition, deployment);
  }

  public JsonNode capabilities(long runtimeEnvironmentId) {
    return gateway.capabilities(runtimeResolver.environment(runtimeEnvironmentId, true));
  }
}
