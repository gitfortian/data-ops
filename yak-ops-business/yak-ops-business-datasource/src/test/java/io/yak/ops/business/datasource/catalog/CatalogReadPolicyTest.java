package io.yak.ops.business.datasource.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest.ReadMode;
import io.yak.ops.business.datasource.exception.DataSourceException;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogReadPolicyTest {

  private final CatalogReadPolicy policy = new CatalogReadPolicy();

  @Test
  void rejectsNonSelectSql() {
    CatalogReadRequest request =
        new CatalogReadRequest(ReadMode.SQL, null, "DELETE FROM patient", List.of());

    assertThatThrownBy(() -> policy.validateReadOnly(request))
        .isInstanceOf(DataSourceException.class)
        .hasMessageContaining("仅允许执行单条 SELECT 查询");
  }

  @Test
  void rejectsMultipleStatements() {
    CatalogReadRequest request =
        new CatalogReadRequest(
            ReadMode.SQL,
            null,
            "SELECT * FROM patient; DROP TABLE patient",
            List.of());

    assertThatThrownBy(() -> policy.validateReadOnly(request))
        .isInstanceOf(DataSourceException.class)
        .hasMessageContaining("仅允许执行单条 SELECT 查询");
  }
}
