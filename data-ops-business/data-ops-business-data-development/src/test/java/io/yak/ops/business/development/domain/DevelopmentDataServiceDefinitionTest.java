package io.yak.ops.business.development.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DevelopmentDataServiceDefinitionTest {

  @Test
  void publishableDefinitionMatchesCurrentRuntimeCapabilities() {
    DevelopmentDataServiceDefinition definition = definition(
        new DevelopmentDataServiceDefinition.ParameterContract(
            "status", "STRING", true, null, null));

    assertDoesNotThrow(definition::validatePublishable);
  }

  @Test
  void publishRejectsOptionalRequestParameter() {
    DevelopmentDataServiceDefinition definition = definition(
        new DevelopmentDataServiceDefinition.ParameterContract(
            "status", "STRING", false, null, null));

    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, definition::validatePublishable);

    assertTrue(error.getMessage().contains("必填"));
  }

  @Test
  void publishRejectsObjectRequestParameter() {
    DevelopmentDataServiceDefinition definition = definition(
        new DevelopmentDataServiceDefinition.ParameterContract(
            "filter", "OBJECT", true, null, null));

    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, definition::validatePublishable);

    assertTrue(error.getMessage().contains("OBJECT"));
  }

  @Test
  void publishRejectsMissingStandaloneSql() {
    DevelopmentDataServiceDefinition definition = new DevelopmentDataServiceDefinition(
        0L,
        0L,
        0,
        "订单查询 API",
        "/orders",
        "GET",
        List.of(),
        List.of(new DevelopmentDataServiceDefinition.ResponseFieldContract(
            "id", "INTEGER", false, null, null)),
        1000,
        30,
        null,
        42L,
        "");

    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, definition::validatePublishable);

    assertTrue(error.getMessage().contains("查询 SQL"));
  }

  private DevelopmentDataServiceDefinition definition(
      DevelopmentDataServiceDefinition.ParameterContract parameter) {
    return new DevelopmentDataServiceDefinition(
        0L,
        0L,
        0,
        "订单查询 API",
        "/orders",
        "GET",
        List.of(parameter),
        List.of(new DevelopmentDataServiceDefinition.ResponseFieldContract(
            "id", "INTEGER", false, null, null)),
        1000,
        30,
        null,
        42L,
        "select id from orders where status = :status");
  }
}
