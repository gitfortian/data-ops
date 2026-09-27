package io.yak.ops.business.metric.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.metric.controller.v1.dto.MetricQueryDTO;
import io.yak.ops.business.metric.controller.v1.vo.MetricVO;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Phase 5 additive-compatibility guard for the pre-existing Metric REST surface.
 *
 * <p>New Validation / Publication / Impact routes and response fields may be added, but the
 * established CRUD/page/stats routes and core JSON bean properties below must not disappear or
 * change method/path semantics without an explicit versioned API decision.
 */
class MetricExternalApiCompatibilityTest {

  @Test
  void keepsEstablishedMetricCrudAndDiscoveryRoutes() throws Exception {
    RequestMapping root = MetricController.class.getAnnotation(RequestMapping.class);
    assertThat(root).isNotNull();
    assertThat(root.value()).containsExactly("/api/v1/metrics");

    assertPost("create", "");
    assertGet("get", "/{id}");
    assertPost("page", "/page");
    assertPut("update", "/{id}");
    assertPost("changeStatus", "/{id}/status");
    assertDelete("delete", "/{id}");
    assertGet("stats", "/stats");
  }

  @Test
  void keepsLegacyPagingRequestDefaultsAndFieldsWhileProcessIdRemainsAdditive() {
    MetricQueryDTO query = new MetricQueryDTO();
    assertThat(query.getPageNo()).isEqualTo(1);
    assertThat(query.getPageSize()).isEqualTo(20);
    assertThat(query.getProcessId()).isNull();

    Set<String> properties = beanGetterProperties(MetricQueryDTO.class);
    assertThat(properties).contains(
        "pageNo", "pageSize", "domainId", "metricType", "status", "owner", "tagIds", "keyword");
    assertThat(properties).contains("processId");
  }

  @Test
  void keepsEstablishedMetricResponsePropertiesAndOnlyAddsPhase5Context() {
    Set<String> properties = beanGetterProperties(MetricVO.class);
    assertThat(properties).contains(
        "id", "metricCode", "metricName", "domainId", "domainName", "processId", "processName",
        "metricType", "caliberId", "caliberName", "calRule", "measureExpr", "filterExpr",
        "dimModelIds", "refMetricId", "refMetricName", "dimConstraint", "qualifiersJson", "modelId",
        "modelName", "statDimensions", "statPeriod", "unitId", "unitName", "businessDesc", "owner",
        "status", "version", "createdBy", "updatedBy", "createTime", "updateTime", "compositions");

    // Phase 5 context is additive: old clients can ignore these JSON properties.
    assertThat(properties).contains(
        "definitionViewType", "editable", "dependencyChanges", "authoringNextStep");
  }

  private static void assertGet(String methodName, String path) throws Exception {
    Method method = method(methodName);
    GetMapping mapping = method.getAnnotation(GetMapping.class);
    assertThat(mapping).as(methodName + " must remain GET").isNotNull();
    assertPath(mapping.value(), path);
  }

  private static void assertPost(String methodName, String path) throws Exception {
    Method method = method(methodName);
    PostMapping mapping = method.getAnnotation(PostMapping.class);
    assertThat(mapping).as(methodName + " must remain POST").isNotNull();
    assertPath(mapping.value(), path);
  }

  private static void assertPut(String methodName, String path) throws Exception {
    Method method = method(methodName);
    PutMapping mapping = method.getAnnotation(PutMapping.class);
    assertThat(mapping).as(methodName + " must remain PUT").isNotNull();
    assertPath(mapping.value(), path);
  }

  private static void assertDelete(String methodName, String path) throws Exception {
    Method method = method(methodName);
    DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
    assertThat(mapping).as(methodName + " must remain DELETE").isNotNull();
    assertPath(mapping.value(), path);
  }

  private static Method method(String name) {
    return Arrays.stream(MetricController.class.getDeclaredMethods())
        .filter(candidate -> candidate.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing MetricController method: " + name));
  }

  private static void assertPath(String[] values, String expected) {
    if (expected.isEmpty()) {
      assertThat(values).isEmpty();
    } else {
      assertThat(values).containsExactly(expected);
    }
  }

  private static Set<String> beanGetterProperties(Class<?> type) {
    return Arrays.stream(type.getMethods())
        .filter(method -> !method.getName().equals("getClass"))
        .map(MetricExternalApiCompatibilityTest::beanProperty)
        .filter(property -> property != null)
        .collect(Collectors.toSet());
  }

  private static String beanProperty(Method method) {
    String name = method.getName();
    if (method.getParameterCount() == 0 && name.startsWith("get") && name.length() > 3) {
      return Character.toLowerCase(name.charAt(3)) + name.substring(4);
    }
    if (method.getParameterCount() == 0
        && method.getReturnType() == boolean.class
        && name.startsWith("is")
        && name.length() > 2) {
      return Character.toLowerCase(name.charAt(2)) + name.substring(3);
    }
    return null;
  }
}
