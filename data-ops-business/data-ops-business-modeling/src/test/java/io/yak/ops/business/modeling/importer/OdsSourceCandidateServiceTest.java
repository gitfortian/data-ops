package io.yak.ops.business.modeling.importer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogTable;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OdsSourceCandidateServiceTest {
  private DataSourceCatalogReader catalog = mock(DataSourceCatalogReader.class);
  private ModelRepository models = mock(ModelRepository.class);
  private OdsSourceCandidateService service = new OdsSourceCandidateService(
      catalog, models, mock(ActionAuthorization.class));

  private Model model(String code, String db, String schema) {
    return Model.create(code, code, ModelDialect.MYSQL, "", null, "ODS", null)
        .withPersisted((long) code.hashCode() & 0x7fffffffL, "tester", null, null)
        .withSource(10L, db, "orders").withSourceSchema(schema);
  }

  @BeforeEach
  void setup() {
    reset(catalog, models);
    when(catalog.listTables(eq(10L), eq("trade_db"), any(), eq("orders")))
        .thenReturn(List.of(new CatalogTable("trade_db", "public", "orders", "TABLE", "")));
    when(models.listBySource(10L, "orders")).thenReturn(List.of());
  }

  @Test
  void noCandidateIsARealZeroOnlyAfterLivePhysicalVerification() {
    var result = service.find(10L, "trade_db", "public", "orders");
    assertEquals("READY", result.state());
    assertTrue(result.candidates().isEmpty());
  }

  @Test
  void exactSourceIgnoresOtherDatabaseAndSchemaWithoutMatchingByName() {
    when(models.listBySource(10L, "orders")).thenReturn(List.of(
        model("ods_other_db", "other_db", "public"),
        model("ods_other_schema", "trade_db", "archive"),
        model("ods_correct", "trade_db", "public")));
    var result = service.find(10L, "trade_db", "public", "orders");
    assertEquals("READY", result.state());
    assertEquals(1, result.candidates().size());
    assertEquals("ods_correct", result.candidates().get(0).code());
  }

  @Test
  void multipleExactModelsAreSurfacedForUserChoice() {
    when(models.listBySource(10L, "orders")).thenReturn(List.of(
        model("ods_first", "trade_db", "public"), model("ods_second", "trade_db", "public")));
    assertEquals(2, service.find(10L, "trade_db", "public", "orders").candidates().size());
  }

  @Test
  void legacyUnknownSchemaIsNotRecastAsAQualifiedMatch() {
    when(models.listBySource(10L, "orders")).thenReturn(List.of(model("legacy", "trade_db", null)));
    var result = service.find(10L, "trade_db", "public", "orders");
    assertEquals("UNVERIFIED", result.state());
    assertTrue(result.candidates().isEmpty());
  }

  @Test
  void ambiguousOrAbsentPhysicalEvidenceIsNotZeroCandidates() {
    when(catalog.listTables(10L, "trade_db", "public", "orders")).thenReturn(List.of());
    assertEquals("UNVERIFIED", service.find(10L, "trade_db", "public", "orders").state());
    verify(models, never()).listBySource(any(), any());
  }
}
