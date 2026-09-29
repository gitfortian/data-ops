package io.yak.ops.business.dataservice.documentation;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ParameterDoc;
import io.yak.ops.business.dataservice.query.DataServiceParameterNameReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves the complete HTTP request parameter contract exposed by one Data Service. */
@Component
@RequiredArgsConstructor
public class DataServiceRequestParameterContract {

  private static final Map<String, ParameterDoc> PAGINATION_PARAMETERS = paginationParameters();

  private final DataServiceParameterNameReader parameterNameReader;

  public List<ParameterDoc> resolve(DataServiceDefinition definition) {
    if (definition == null) throw new IllegalArgumentException("数据服务定义不能为空");

    List<String> sqlNames = parameterNameReader.parameterNames(definition.runtimeSnapshot().sql());
    if (definition.settings().paginationEnabled()) {
      for (String name : sqlNames) {
        if (isRuntimeManaged(name)) {
          throw new IllegalArgumentException("开启分页时 SQL 参数不能使用系统分页参数名：" + name);
        }
      }
    }

    List<ParameterDoc> result = new ArrayList<>();
    for (String name : sqlNames) {
      result.add(new ParameterDoc(name, "STRING", true, null, null));
    }
    if (definition.settings().paginationEnabled()) result.addAll(PAGINATION_PARAMETERS.values());
    return List.copyOf(result);
  }

  boolean isRuntimeManaged(String name) {
    return name != null && PAGINATION_PARAMETERS.containsKey(name);
  }

  private static Map<String, ParameterDoc> paginationParameters() {
    Map<String, ParameterDoc> result = new LinkedHashMap<>();
    result.put(
        "returnTotalNum",
        new ParameterDoc(
            "returnTotalNum", "BOOLEAN", false, "是否返回分页总数，默认 true", "true"));
    result.put(
        "pageNum",
        new ParameterDoc("pageNum", "INTEGER", false, "页码，从 1 开始，默认 1", "1"));
    result.put(
        "pageSize",
        new ParameterDoc(
            "pageSize", "INTEGER", false, "每页条数，默认 20，不超过服务最大行数", "20"));
    return Collections.unmodifiableMap(result);
  }
}
