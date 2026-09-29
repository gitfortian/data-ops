package io.yak.ops.business.sync.realtime.definition;

import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.RealtimeValidationResult;
import io.yak.ops.business.sync.realtime.execution.RealtimeJobExecutionService;
import org.springframework.stereotype.Service;

/** Stable application entry for realtime task definition use-cases. */
@Service("realtimeJobDefinitionApplicationService")
public class RealtimeJobDefinitionService {

  private final RealtimeDefinitionManager definitions;
  private final RealtimeDefinitionPublisher publisher;
  private final RealtimeDefinitionValidator validator;
  private final RealtimeYamlCodec yaml;
  private final RealtimeJobExecutionService executions;

  public RealtimeJobDefinitionService(
      RealtimeDefinitionManager definitions,
      RealtimeDefinitionPublisher publisher,
      RealtimeDefinitionValidator validator,
      RealtimeYamlCodec yaml,
      RealtimeJobExecutionService executions) {
    this.definitions = definitions;
    this.publisher = publisher;
    this.validator = validator;
    this.yaml = yaml;
    this.executions = executions;
  }

  public long create(String name, String description, long runtimeEnvironmentId) {
    return definitions.create(name, description, runtimeEnvironmentId);
  }

  public long save(
      Long id,
      String name,
      String description,
      CdcPipelineSpec spec,
      long runtimeEnvironmentId) {
    validator.validate(spec, runtimeEnvironmentId);
    return definitions.save(id, name, description, spec, runtimeEnvironmentId);
  }

  public RealtimeValidationResult validateDefinition(
      CdcPipelineSpec spec, long runtimeEnvironmentId) {
    return validator.validate(spec, runtimeEnvironmentId);
  }

  public CdcPipelineSpec parseYaml(String source) {
    return yaml.parse(source);
  }

  public String renderYaml(CdcPipelineSpec spec) {
    return yaml.render(spec);
  }

  public void publish(long id) {
    publisher.publish(id);
  }

  public RealtimeValidationResult validate(long id) {
    return publisher.validate(id);
  }

  public void delete(long id) {
    executions.assertSafeToDelete(id);
    definitions.delete(id);
  }
}
