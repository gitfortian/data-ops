package io.yak.ops.business.agent.catalog;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.agent.domain.DatasetSummary;
import io.yak.ops.business.agent.gateway.DatasetCatalogGateway;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 字段白名单行为测试：非法 fieldId 必须给出可自纠的精确错误（DOMAIN 硬规则 3）。 */
class FieldWhitelistValidatorTest {

  private DatasetCatalogGateway catalogGateway;
  private FieldWhitelistValidator validator;

  @BeforeEach
  void setUp() {
    catalogGateway = mock(DatasetCatalogGateway.class);
    validator = new FieldWhitelistValidator(catalogGateway);
    when(catalogGateway.listFields(7L))
        .thenReturn(
            List.of(
                new DatasetSummary.FieldView("region", "区域", "VARCHAR", "DIMENSION", true, null),
                new DatasetSummary.FieldView("amount", "金额", "DECIMAL", "MEASURE", true, null)));
  }

  @Test
  void unknownFieldIsRejectedWithSelfCorrectionHint() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> validator.requireKnownFields(7L, List.of("region", "revenue"), "字段引用"));

    assertTrue(error.getMessage().contains("revenue"));
    assertTrue(error.getMessage().contains("get_dataset_fields"));
  }

  @Test
  void knownFieldsPass() {
    validator.requireKnownFields(7L, List.of("region", "amount"), "字段引用");
  }
}
