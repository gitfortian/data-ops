package io.yak.ops.business.audit;

import static io.yak.ops.business.audit.AuditWebOperationInfer.infer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 锁死「HTTP method + 路径模板 → 审计三元组」的推导契约（票 01）。 */
class AuditWebOperationInferContractTest {

  @ParameterizedTest
  @CsvSource({
    // CRUD：HTTP 方法兜底动作
    "POST,   /api/v1/modeling/models,             MODELING_MODELS_CREATE,      新增 modeling/models",
    "PUT,    /api/v1/modeling/models/{id},        MODELING_MODELS_UPDATE,      更新 modeling/models",
    "DELETE, /api/v1/modeling/models/{id},        MODELING_MODELS_DELETE,      删除 modeling/models",
    "PATCH,  /api/v1/semantic/layers/{id},        SEMANTIC_LAYERS_UPDATE,      更新 semantic/layers",
    // 路径尾部动词优先于 HTTP 方法
    "POST,   /api/v1/modeling/models/{id}/publish, MODELING_MODELS_PUBLISH,    发布 modeling/models",
    "POST,   /api/v1/datasource/data-source/{id}/test, DATASOURCE_DATA_SOURCE_TEST, 测试 datasource/data-source",
    "POST,   /api/v1/datasource/data-source/test-connection, DATASOURCE_DATA_SOURCE_TEST_CONNECTION, 测试连接 datasource/data-source",
    "PUT,    /api/v1/workflow/instances/{id}/pause, WORKFLOW_INSTANCES_PAUSE,   暂停 workflow/instances",
    "PUT,    /api/v1/workflow/instances/{id}/resume, WORKFLOW_INSTANCES_RESUME, 恢复 workflow/instances",
    "POST,   /api/v1/lifecycle/models/lifecycle/dispatch, LIFECYCLE_LIFECYCLE_DISPATCH, 下发 lifecycle/lifecycle",
    "DELETE, /api/v1/mdm/mdg/objects/{id},        MDM_OBJECTS_DELETE,          删除 mdm/objects",
    "POST,   /api/v1/modeling/models/{id}/rollback, MODELING_MODELS_ROLLBACK,  回滚 modeling/models",
    "POST,   /api/v1/metric/metrics/{id}/apply,   METRIC_METRICS_APPLY,        应用 metric/metrics",
    // 无资源段：动词直接跟在模块后
    "POST,   /api/v1/home/refresh,                HOME_REFRESH,                刷新 home",
    // 多级路径取最后一个静态段作资源
    "POST,   /api/v1/quality/rules/{ruleId}/monitor-bindings, QUALITY_MONITOR_BINDINGS_CREATE, 新增 quality/monitor-bindings",
    // 连字符段规范化
    "POST,   /api/v1/data-service/api-defs,       DATA_SERVICE_API_DEFS_CREATE, 新增 data-service/api-defs",
    "DELETE, /api/v1/data-development/file-resources/{id}, DATA_DEVELOPMENT_FILE_RESOURCES_DELETE, 删除 data-development/file-resources",
  })
  @DisplayName("写接口推导：类型与可读名称")
  void infersWriteOperations(String method, String pattern, String type, String name) {
    var inferred = infer(method, pattern);
    assertTrue(inferred != null, "should infer for " + method + " " + pattern);
    assertEquals(type, inferred.operationType(), pattern);
    assertEquals(name, inferred.operationName(), pattern);
  }

  @ParameterizedTest
  @CsvSource({
    "POST,   /api/v1/modeling/models/{id}/publish, MODELING_MODELS",
    "PUT,    /api/v1/modeling/models/{id},         MODELING_MODELS",
    "DELETE, /api/v1/mdm/mdg/objects/{id},         MDM_OBJECTS",
    "POST,   /api/v1/data-service/api-defs,        DATA_SERVICE_API_DEFS",
    "POST,   /api/v1/home/refresh,                 HOME",
  })
  @DisplayName("resourceType 推导")
  void infersResourceType(String method, String pattern, String resourceType) {
    assertEquals(resourceType, infer(method, pattern).resourceType(), pattern);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/v1/modeling/models/page",
        "/api/v1/semantic/business-domains/tree",
        "/api/v1/audit/operations/page",
        "/api/v1/metric/metrics/query",
        "/api/v1/datasource/data-source/options",
        "/api/v1/modeling/models/{id}/history",
        "/api/v1/modeling/models/{id}/versions",
        "/api/v1/mdm/mdg/objects/{id}/references",
        "/api/v1/quality/rules/search",
        "/api/v1/workflow/definitions/list",
      })
  @DisplayName("读语义 POST 路径不产生兜底审计（含审计中心自身的分页）")
  void skipsReadLikePaths(String pattern) {
    assertNull(infer("POST", pattern), pattern);
  }

  @ParameterizedTest
  @ValueSource(strings = {"GET", "HEAD", "OPTIONS", "TRACE", "get"})
  @DisplayName("只读方法判定：大小写不敏感")
  void readOnlyMethodsAreClassified(String method) {
    assertTrue(AuditWebOperationInfer.readOnlyMethod(method), method);
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
  @DisplayName("写方法判定")
  void writeMethodsAreNotReadOnly(String method) {
    assertTrue(!AuditWebOperationInfer.readOnlyMethod(method), method);
  }

  @Test
  @DisplayName("异常输入与非法路径返回 null，不抛错")
  void returnsNullForUnusableInput() {
    assertNull(infer(null, "/api/v1/x/y"));
    assertNull(infer("POST", null));
    assertNull(infer("POST", "  "));
    assertNull(infer("POST", "/health"));
    assertNull(infer("POST", "/api"));
    assertNull(infer("POST", "/api/v1"));
    assertNull(infer("FROB", "/api/v1/modeling/models"));
  }

  @Test
  @DisplayName("版本段可省略：/api/module/... 仍可推导")
  void infersWithoutVersionSegment() {
    var inferred = infer("POST", "/api/modeling/models");
    assertTrue(inferred != null);
    assertEquals("MODELING_MODELS_CREATE", inferred.operationType());
  }

  @Test
  @DisplayName("动词表暴露只读视图供语义补齐票核对")
  void exposesKnownVerbs() {
    List<String> expected = List.of("publish", "rollback", "dispatch", "test-connection");
    assertTrue(AuditWebOperationInfer.knownVerbs().containsAll(expected));
  }
}
