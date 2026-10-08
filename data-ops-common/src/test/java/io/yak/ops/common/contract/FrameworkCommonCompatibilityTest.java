package io.yak.ops.common.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.framework.common.BusinessException;
import io.yak.framework.common.ErrorCode;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.ops.common.enums.alert.AlertErrorCode;
import io.yak.ops.common.enums.approval.ApprovalErrorCode;
import io.yak.ops.common.enums.asset.AssetErrorCode;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.common.enums.lifecycle.LifecycleErrorCode;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import io.yak.ops.common.enums.metric.MetricErrorCode;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import io.yak.ops.common.enums.resource.ResourceErrorCode;
import io.yak.ops.common.enums.security.SecurityErrorCode;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A8.1c migration safety net: the product-facing enums, error/result contract,
 * and HTTP pagination shape must survive the future Common/Security transfer.
 */
class FrameworkCommonCompatibilityTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void allTwelveProductErrorCodeOwnersStillImplementTheExistingFrameworkContract() {
    List<Class<? extends Enum<?>>> types = List.of(
        AlertErrorCode.class, ApprovalErrorCode.class, AssetErrorCode.class,
        DataSourceErrorCode.class, LifecycleErrorCode.class, MdmErrorCode.class,
        MetadataErrorCode.class, MetricErrorCode.class, ModelingErrorCode.class,
        ResourceErrorCode.class, SecurityErrorCode.class, SemanticErrorCode.class);
    assertEquals(12, types.size());
    for (Class<? extends Enum<?>> type : types) {
      assertTrue(ErrorCode.class.isAssignableFrom(type), type.getName());
      Enum<?>[] constants = type.getEnumConstants();
      assertNotNull(constants, type.getName());
      assertTrue(constants.length > 0, type.getName());
      for (Enum<?> constant : constants) {
        ErrorCode code = (ErrorCode) constant;
        assertNotNull(code.getCode(), type.getName() + "." + constant.name());
        assertNotNull(code.getMessage(), type.getName() + "." + constant.name());
        Result<?> result = Result.fail(code);
        assertEquals(code.getCode(), result.getCode());
        assertEquals(code.getMessage(), result.getMessage());
      }
    }
  }

  @Test
  void frameworkBusinessExceptionAndResultPreserveStructuredBusinessError() {
    ErrorCode code = MetricErrorCode.NOT_FOUND;
    BusinessException exception = new BusinessException(code);
    assertSame(code, exception.getErrorCode());
    assertEquals(code.getCode() + "-" + code.getMessage(), exception.getMessage());

    Result<?> result = Result.fail(exception);
    assertEquals(code.getCode(), result.getCode());
    assertEquals(code.getMessage(), result.getMessage());
    assertTrue(result.failed());
    assertFalse(result.succeeded());
  }

  @Test
  void successfulResultPreservesHttpCodeMessageAndData() throws Exception {
    JsonNode json = JSON.readTree(JSON.writeValueAsString(Result.success("contract")));
    assertEquals(200, json.path("code").asInt());
    assertEquals("成功", json.path("message").asText());
    assertEquals("contract", json.path("data").asText());
  }

  @Test
  void pageDataAndPagingDataPreserveTheBizDataAndPaginationJsonContract() throws Exception {
    PageData<String> internal = PageData.of(List.of("a", "b"), 5, 2, 2);
    assertEquals(3, internal.pages());
    PagingData<String> external = PagingData.from(internal);
    JsonNode json = JSON.readTree(JSON.writeValueAsString(external));
    assertTrue(json.has("bizData"));
    assertTrue(json.has("pagination"));
    assertEquals("a", json.path("bizData").get(0).asText());
    assertEquals("b", json.path("bizData").get(1).asText());
    assertEquals(5, json.path("pagination").path("total").asLong());
    assertEquals(3, json.path("pagination").path("pages").asLong());
    assertEquals(2, json.path("pagination").path("pageNo").asLong());
    assertEquals(2, json.path("pagination").path("pageSize").asLong());
  }

  @Test
  void emptyPagingContractKeepsItsHistoricalDefaultValues() throws Exception {
    JsonNode json = JSON.readTree(JSON.writeValueAsString(PagingData.from(null)));
    assertEquals(0, json.path("bizData").size());
    assertEquals(0, json.path("pagination").path("total").asLong());
    assertEquals(0, json.path("pagination").path("pages").asLong());
    assertEquals(1, json.path("pagination").path("pageNo").asLong());
    assertEquals(0, json.path("pagination").path("pageSize").asLong());
  }
}
