package io.yak.ops.business.dataservice.documentation;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ParameterDoc;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ResponseFieldDoc;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.repository.DataServiceDocumentationRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceDocumentationManager {
  private static final Set<String> PARAMETER_TYPES =
      Set.of("STRING", "INTEGER", "NUMBER", "BOOLEAN", "DATE", "DATETIME");
  private static final Set<String> RESPONSE_TYPES =
      Set.of("STRING", "INTEGER", "NUMBER", "BOOLEAN", "DATE", "DATETIME", "OBJECT");
  private final DataServiceReader dataServiceReader;
  private final DataServiceDocumentationRepository repository;
  private final DataServiceDocumentationReader reader;
  private final DataServiceRequestParameterContract requestParameterContract;
  private final DocumentationFingerprint fingerprint;

  @Transactional
  public ApiDocumentation save(Long apiId, DocumentationInput input) {
    if (input == null) throw new IllegalArgumentException("API 文档配置不能为空");
    DataServiceDefinition definition = dataServiceReader.require(apiId);
    List<ParameterDoc> currentParameters = requestParameterContract.resolve(definition);
    List<ParameterDoc> parameters = normalizeParameters(currentParameters, input.parameters());
    List<ResponseFieldDoc> responseFields = normalizeResponseFields(input.responseFields());
    repository.save(new DataServiceDocumentation(
        apiId,
        fingerprint.sqlHash(definition.runtimeSnapshot().sql()),
        parameters,
        responseFields,
        LocalDateTime.now()));
    return reader.get(apiId);
  }

  @Transactional
  public void deleteForApi(Long apiId) {
    repository.delete(apiId);
  }

  private List<ParameterDoc> normalizeParameters(
      List<ParameterDoc> currentParameters, List<ParameterDoc> input) {
    List<ParameterDoc> supplied = input == null ? List.of() : input;
    Map<String, ParameterDoc> current = new LinkedHashMap<>();
    for (ParameterDoc parameter : currentParameters == null ? List.<ParameterDoc>of() : currentParameters) {
      current.put(parameter.name(), parameter);
    }

    Set<String> seen = new LinkedHashSet<>();
    Map<String, ParameterDoc> byName = new LinkedHashMap<>();
    for (ParameterDoc parameter : supplied) {
      if (parameter == null || !StringUtils.hasText(parameter.name())) {
        throw new IllegalArgumentException("参数名称不能为空");
      }
      String name = parameter.name().trim();
      ParameterDoc canonical = current.get(name);
      if (canonical == null) throw new IllegalArgumentException("SQL 中不存在参数：" + name);
      if (!seen.add(name)) throw new IllegalArgumentException("参数文档重复：" + name);

      String type = requestParameterContract.isRuntimeManaged(name)
          ? canonical.type()
          : normalizeType(parameter.type(), PARAMETER_TYPES);
      byName.put(name, new ParameterDoc(
          name,
          type,
          canonical.required(),
          firstText(trim(parameter.description(), 500), canonical.description()),
          firstText(trim(parameter.example(), 500), canonical.example())));
    }

    List<ParameterDoc> result = new ArrayList<>();
    for (ParameterDoc canonical : current.values()) {
      result.add(byName.getOrDefault(canonical.name(), canonical));
    }
    return result;
  }

  private List<ResponseFieldDoc> normalizeResponseFields(List<ResponseFieldDoc> input) {
    List<ResponseFieldDoc> supplied = input == null ? List.of() : input;
    if (supplied.size() > 200) throw new IllegalArgumentException("响应字段不能超过 200 个");
    Set<String> seen = new LinkedHashSet<>();
    List<ResponseFieldDoc> result = new ArrayList<>();
    for (ResponseFieldDoc field : supplied) {
      if (field == null || !StringUtils.hasText(field.name())) {
        throw new IllegalArgumentException("响应字段名称不能为空");
      }
      String name = field.name().trim();
      if (!seen.add(name)) throw new IllegalArgumentException("响应字段重复：" + name);
      result.add(new ResponseFieldDoc(
          name,
          normalizeType(field.type(), RESPONSE_TYPES),
          field.nullable(),
          trim(field.description(), 500),
          trim(field.example(), 500)));
    }
    return result;
  }

  private String normalizeType(String type, Set<String> allowed) {
    String normalized = StringUtils.hasText(type) ? type.trim().toUpperCase() : "STRING";
    if (!allowed.contains(normalized)) {
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
