package io.yak.ops.business.datasource.controller.v1.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest.ReadMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogRequestConverterTest {
  private final CatalogRequestConverter converter = new CatalogRequestConverter();

  @Test
  void legacyHttpMapIsParsedOnceIntoTypedCatalogRequest() {
    CatalogReadRequest request = converter.readRequest(Map.of(
        "readMode", "sql",
        "sql", "SELECT * FROM patient WHERE day = ${day}",
        "paramsList", List.of(Map.of("paramName", "day", "paramValue", "2026-08-23"))));
    assertThat(request.mode()).isEqualTo(ReadMode.SQL);
    assertThat(request.sql()).isEqualTo("SELECT * FROM patient WHERE day = ${day}");
    assertThat(request.variables()).hasSize(1);
    assertThat(request.variables().getFirst().name()).isEqualTo("day");
    assertThat(request.variables().getFirst().value()).isEqualTo("2026-08-23");
  }

  @Test
  void tablePathSupportsLegacyAliases() {
    assertThat(converter.tablePath(Map.of("table", "orders"))).isEqualTo("orders");
    assertThat(converter.tablePath(Map.of("tablePath", "public.orders"))).isEqualTo("public.orders");
  }
}
