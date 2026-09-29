package io.yak.ops.business.sync.realtime.definition;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.datasource.domain.catalog.CatalogTable;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpecValidator;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeValidationResult;
import io.yak.ops.business.sync.realtime.engine.PipelineYamlCompiler;
import io.yak.ops.business.sync.realtime.engine.RealtimeConnectorCapabilityResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeDataSourceResolver;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.engine.ResolvedCdcPipeline;
import io.yak.ops.business.sync.realtime.environment.RealtimeRuntimeResolver;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Definition-level preflight shared by Wizard/YAML payloads and Draft save flows.
 *
 * <p>This deliberately does not perform Flink REST health validation. Drafts remain editable while
 * the runtime cluster is temporarily unavailable; publish/start perform runtime validation through
 * their own boundary. Source metadata is validated because table/key drift belongs to the logical
 * definition contract.
 */
@Component
public class RealtimeDefinitionValidator {

  private final Validator beanValidator;
  private final CdcPipelineSpecValidator specValidator;
  private final RealtimeRuntimeResolver runtimeResolver;
  private final RealtimeDataSourceResolver dataSourceResolver;
  private final DataSourceCatalogReader catalogReader;
  private final RealtimeConnectorCapabilityResolver capabilityResolver;
  private final PipelineYamlCompiler compiler;
  private final RealtimeEngineGateway gateway;

  public RealtimeDefinitionValidator(
      Validator beanValidator,
      CdcPipelineSpecValidator specValidator,
      RealtimeRuntimeResolver runtimeResolver,
      RealtimeDataSourceResolver dataSourceResolver,
      DataSourceCatalogReader catalogReader,
      RealtimeConnectorCapabilityResolver capabilityResolver,
      PipelineYamlCompiler compiler,
      RealtimeEngineGateway gateway) {
    this.beanValidator = beanValidator;
    this.specValidator = specValidator;
    this.runtimeResolver = runtimeResolver;
    this.dataSourceResolver = dataSourceResolver;
    this.catalogReader = catalogReader;
    this.capabilityResolver = capabilityResolver;
    this.compiler = compiler;
    this.gateway = gateway;
  }

  public RealtimeValidationResult validate(CdcPipelineSpec spec, long runtimeEnvironmentId) {
    validateSpec(spec);
    ComputeEnvironmentSnapshot environment = runtimeResolver.environment(runtimeEnvironmentId, true);
    ResolvedCdcPipeline resolved = dataSourceResolver.resolve(spec);
    validateSourceCatalog(spec);
    JsonNode manifest = gateway.capabilities(environment);
    capabilityResolver.requireSupported(manifest, resolved, spec);

    // Compile the logical definition as part of preflight so route escaping and connector YAML shape
    // fail before persistence. No secrets are resolved and no Flink REST health call is made here.
    compiler.compile("definition-preflight", spec, resolved);

    return new RealtimeValidationResult(
        true, manifest.path("deliverySemantics").asText("at-least-once"));
  }

  public void validateSpec(CdcPipelineSpec spec) {
    if (spec == null) {
      throw new IllegalArgumentException("实时同步 Spec 不能为空");
    }
    List<String> violations =
        beanValidator.validate(spec).stream()
            .sorted(Comparator.comparing(value -> value.getPropertyPath().toString()))
            .map(this::violationMessage)
            .toList();
    if (!violations.isEmpty()) {
      throw new IllegalArgumentException("实时同步 Spec 配置无效：" + String.join("；", violations));
    }
    specValidator.validate(spec);
  }

  private void validateSourceCatalog(CdcPipelineSpec spec) {
    List<CatalogTable> physicalTables;
    try {
      physicalTables =
          catalogReader.listTables(spec.sourceDataSourceRef(), null, null, null).stream()
              .filter(this::isPhysicalTable)
              .toList();
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException(
          "Source 数据源元数据读取失败：" + message(exception), exception);
    }

    for (CdcPipelineSpec.TableRoute route : spec.tables()) {
      List<CatalogTable> matched = matchTables(route, physicalTables);
      if (matched.isEmpty()) {
        throw new IllegalArgumentException(
            route.matchMode() == CdcPipelineSpec.MatchMode.EXACT
                ? "Source 表不存在：" + route.sourceTable()
                : "Source 表正则未匹配到任何物理表：" + route.sourceTable());
      }
      for (CatalogTable table : matched) {
        validatePrimaryKey(spec.sourceDataSourceRef(), table, route.keyColumns());
      }
    }
  }

  private List<CatalogTable> matchTables(
      CdcPipelineSpec.TableRoute route, List<CatalogTable> tables) {
    if (route.matchMode() == CdcPipelineSpec.MatchMode.EXACT) {
      return tables.stream().filter(table -> route.sourceTable().equals(table.name())).toList();
    }
    Pattern pattern = Pattern.compile(route.sourceTable());
    return tables.stream()
        .filter(table -> pattern.matcher(table.name()).matches())
        .toList();
  }

  private void validatePrimaryKey(
      Long dataSourceId, CatalogTable table, List<String> configuredKeys) {
    List<CatalogColumn> columns;
    try {
      columns =
          catalogReader.listColumns(
              dataSourceId, table.database(), table.schema(), table.name());
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException(
          "Source 表字段元数据读取失败：" + table.name() + "，" + message(exception), exception);
    }
    if (columns == null || columns.isEmpty()) {
      throw new IllegalArgumentException("Source 表不存在或无可读字段：" + table.name());
    }

    List<String> actualKeys =
        columns.stream()
            .filter(CatalogColumn::primaryKey)
            .sorted(Comparator.comparingInt(CatalogColumn::ordinalPosition))
            .map(CatalogColumn::name)
            .toList();
    if (actualKeys.isEmpty()) {
      throw new IllegalArgumentException("Source 表未检测到主键：" + table.name());
    }

    Set<String> configured = new LinkedHashSet<>(configuredKeys);
    if (configured.size() != configuredKeys.size()) {
      throw new IllegalArgumentException("表规则主键字段不能重复：" + table.name());
    }
    Set<String> actual = new LinkedHashSet<>(actualKeys);
    if (!actual.equals(configured)) {
      throw new IllegalArgumentException(
          "Source 表主键与任务配置不一致："
              + table.name()
              + "，当前主键="
              + actualKeys
              + "，配置主键="
              + configuredKeys);
    }
  }

  private boolean isPhysicalTable(CatalogTable table) {
    return table != null
        && (table.type() == null
            || !table.type().toUpperCase(Locale.ROOT).contains("VIEW"));
  }

  private String violationMessage(ConstraintViolation<CdcPipelineSpec> violation) {
    String path = violation.getPropertyPath().toString();
    return (path.isBlank() ? "配置" : path) + " " + violation.getMessage();
  }

  private String message(RuntimeException exception) {
    return exception.getMessage() == null || exception.getMessage().isBlank()
        ? exception.getClass().getSimpleName()
        : exception.getMessage();
  }
}
