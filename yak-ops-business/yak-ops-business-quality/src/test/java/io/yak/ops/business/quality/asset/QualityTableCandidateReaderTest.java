package io.yak.ops.business.quality.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.yak.ops.business.quality.domain.QualityDomain.TableAssetTarget;
import io.yak.ops.business.quality.gateway.datasource.QualityDataCatalogGateway;
import io.yak.ops.business.quality.gateway.datasource.QualityDataCatalogGateway.QualityPhysicalTable;
import io.yak.ops.business.quality.repository.QualityTableAssetRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class QualityTableCandidateReaderTest {
  @Mock private QualityTableAssetRepository repository;
  @Mock private QualityDataCatalogGateway catalogGateway;
  private QualityTableCandidateReader reader;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    reader =
        new QualityTableCandidateReader(
            repository, catalogGateway, new QualityTableTargetPolicy());
  }

  @Test
  void shouldReturnOnlyUnregisteredPluginTables() {
    when(repository.listTableAssetTargets(1L, "demo"))
        .thenReturn(List.of(new TableAssetTarget("demo", null, "registered_table")));
    when(catalogGateway.listTables(1L, "demo", null, null))
        .thenReturn(
            List.of(
                table("registered_table", "已注册"),
                table("order_info", "订单表"),
                table("user_info", "用户表")));

    var result = reader.candidates(1L, "demo", null, null, 1, 20);

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.records())
        .extracting(QualityPhysicalTable::tableName)
        .containsExactly("order_info", "user_info");
  }

  private QualityPhysicalTable table(String name, String remarks) {
    return new QualityPhysicalTable("demo", null, name, "TABLE", remarks);
  }
}
