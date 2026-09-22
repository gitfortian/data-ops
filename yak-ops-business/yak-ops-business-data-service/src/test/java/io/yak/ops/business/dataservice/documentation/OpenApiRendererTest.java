package io.yak.ops.business.dataservice.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ParameterDoc;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenApiRendererTest {

  @Test
  @SuppressWarnings("unchecked")
  void rendersPaginationRuntimeParametersAsOptional() {
    ApiDocumentation documentation = new ApiDocumentation(
        7L,
        "订单查询",
        "/api/v1/data-service/runtime/orders",
        "NONE",
        null,
        true,
        false,
        List.of(
            new ParameterDoc("status", "STRING", true, "订单状态", "PAID"),
            new ParameterDoc("returnTotalNum", "BOOLEAN", false, "是否返回分页总数", "true"),
            new ParameterDoc("pageNum", "INTEGER", false, "页码", "1"),
            new ParameterDoc("pageSize", "INTEGER", false, "每页条数", "20")),
        List.of(),
        null);

    Map<String, Object> rendered = new OpenApiRenderer().render(documentation);
    Map<String, Object> paths = (Map<String, Object>) rendered.get("paths");
    Map<String, Object> path =
        (Map<String, Object>) paths.get("/api/v1/data-service/runtime/orders");
    Map<String, Object> operation = (Map<String, Object>) path.get("get");
    List<Map<String, Object>> parameters =
        (List<Map<String, Object>>) operation.get("parameters");

    assertThat(parameters).extracting(item -> item.get("name"))
        .containsExactly("status", "returnTotalNum", "pageNum", "pageSize");
    assertThat(parameters.get(0).get("required")).isEqualTo(true);
    assertThat(parameters.subList(1, 4))
        .allSatisfy(item -> assertThat(item.get("required")).isEqualTo(false));
  }
}
