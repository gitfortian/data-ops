package io.yak.ops.business.dataservice.documentation;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ParameterDoc;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.repository.DataServiceDocumentationRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceDocumentationReader {
  private static final Set<String> PARAMETER_TYPES =
      Set.of("STRING", "INTEGER", "NUMBER", "BOOLEAN", "DATE", "DATETIME");
  private final DataServiceReader dataServiceReader;
  private final DataServiceDocumentationRepository repository;
  private final DataServiceRequestParameterContract requestParameterContract;
  private final DocumentationFingerprint fingerprint;

  public ApiDocumentation get(Long apiId) {
    DataServiceDefinition definition = dataServiceReader.require(apiId);
    DataServiceDocumentation stored = repository.findByApiId(apiId).orElse(null);
    List<ParameterDoc> currentParameters = requestParameterContract.resolve(definition);
    List<ParameterDoc> parameters = mergeCurrentParameters(
        currentParameters, stored == null ? List.of() : stored.parameters());
    boolean documented = stored != null;
    boolean stale = documented
        && StringUtils.hasText(stored.sqlHash())
        && !stored.sqlHash().equals(fingerprint.sqlHash(definition.runtimeSnapshot().sql()));
    return new ApiDocumentation(
        definition.id(),
        definition.settings().name(),
        "/api/v1/data-service/runtime" + definition.settings().path(),
        definition.authMode().name(),
        definition.settings().description(),
        documented,
        stale,
        parameters,
        stored == null ? List.of() : stored.responseFields(),
        stored == null ? null : stored.updateTime());
  }

  private List<ParameterDoc> mergeCurrentParameters(
      List<ParameterDoc> currentParameters, List<ParameterDoc> saved) {
    Map<String, ParameterDoc> savedByName = (saved == null ? List.<ParameterDoc>of() : saved)
        .stream()
        .filter(item -> item != null && StringUtils.hasText(item.name()))
        .collect(Collectors.toMap(
            item -> item.name().trim(),
            Function.identity(),
            (first, ignored) -> first,
            LinkedHashMap::new));

    List<ParameterDoc> result = new ArrayList<>();
    for (ParameterDoc canonical : currentParameters == null
        ? List.<ParameterDoc>of()
        : currentParameters) {
      ParameterDoc item = savedByName.get(canonical.name());
      if (item == null) {
        result.add(canonical);
        continue;
      }
      String type = requestParameterContract.isRuntimeManaged(canonical.name())
          ? canonical.type()
          : normalizeType(item.type());
      result.add(new ParameterDoc(
          canonical.name(),
          type,
          canonical.required(),
          firstText(trim(item.description(), 500), canonical.description()),
          firstText(trim(item.example(), 500), canonical.example())));
    }
    return result;
  }

  private String normalizeType(String type) {
    String normalized = StringUtils.hasText(type) ? type.trim().toUpperCase() : "STRING";
    if (!PARAMETER_TYPES.contains(normalized)) {
      throw new IllegalArgumentException("不支持的 Schema 类型：" + normalized);
    }
    return normalized;
  }

  private String firstText(String preferred, String fallback) {
    return StringUtils.hasText(preferred) ? preferred : fallback;
  }

  private String trim(String value, int max) {
    if (!StringUtils.hasText(value)) return null;
    String result = value.trim();
    return result.length() <= max ? result : result.substring(0, max);
  }
}
