package io.yak.ops.business.metadata.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metadata.api.EntityDTO;
import io.yak.ops.business.metadata.api.MetadataQueryApi;
import io.yak.ops.spi.section.SectionContext;
import io.yak.ops.spi.section.SectionMapSummary;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MetadataAssetSectionProviderTest {

  private final MetadataQueryApi queryApi = mock(MetadataQueryApi.class);
  private final MetadataAssetSectionProvider provider =
      new MetadataAssetSectionProvider(queryApi);

  @Test
  void readsTableAndColumnsFromMetadataOwner() {
    String key = "table:4:warehouse.public.orders";
    SectionContext context = new SectionContext(key, "METADATA", "12");
    EntityDTO table = new EntityDTO(
        12, "table", Map.of(
            "name", "orders", "dataSourceId", "4", "databaseName", "warehouse",
            "schemaName", "public", "tableName", "orders"),
        Map.of("engine", "doris"), Map.of());
    EntityDTO column = new EntityDTO(
        13, "tableColumn", Map.of("name", "order_id", "dataType", "BIGINT"),
        Map.of(), Map.of());
    when(queryApi.findPhysicalTable(key)).thenReturn(Optional.of(table));
    when(queryApi.listPhysicalColumns("4", "warehouse", "orders"))
        .thenReturn(List.of(column));

    var result = provider.query(context);

    assertEquals(SectionType.TECHNICAL_METADATA, result.sectionType());
    assertEquals(SectionStatus.OK, result.status());
    SectionMapSummary summary = (SectionMapSummary) result.summary();
    assertEquals("orders", summary.values().get("name"));
    assertEquals(List.of(column.facts()), summary.values().get("columns"));
    assertEquals("METADATA", result.ownerDomain());
    assertTrue(result.actions().stream().anyMatch(action -> action.sourceId().equals(key)));
    verify(queryApi).listPhysicalColumns("4", "warehouse", "orders");
  }

  @Test
  void onlyClaimsMetadataPhysicalTables() {
    assertTrue(provider.supports(new SectionContext(
        "table:4:warehouse.public.orders", "METADATA", "12")));
    assertFalse(provider.supports(new SectionContext(
        "modeling:model:12", "MODEL", "12")));
  }
}
