package io.yak.ops.business.quality.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.quality.domain.QualityDomain.TableAssetSpec;
import io.yak.ops.business.quality.gateway.datasource.QualityDataCatalogGateway;
import io.yak.ops.business.quality.gateway.datasource.QualityDataCatalogGateway.QualityPhysicalTable;
import io.yak.ops.business.quality.repository.QualityTableAssetRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class QualityTableAssetManagerTest {
  @Mock private QualityTableAssetRepository repository;
  @Mock private QualityDataCatalogGateway catalogGateway;
  private QualityTableAssetManager manager;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    manager =
        new QualityTableAssetManager(
            repository, catalogGateway, new QualityTableTargetPolicy());
  }

  @Test
  void shouldPersistMetadataReturnedByDatasourcePlugin() {
    when(catalogGateway.listTables(1L, "demo", null, null))
        .thenReturn(List.of(table("user_info", "插件中的用户表描述")));
    when(repository.registerTableAssets(anyList())).thenReturn(1);

    var result =
        manager.register(
            new QualityTableAssetCommand.Register(
                1L,
                "测试数据源",
                "demo",
                List.of(
                    new QualityTableAssetCommand.Item(
                        "demo", null, "user_info", "CLIENT_VALUE", "客户端描述"))),
            "tester");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<TableAssetSpec>> captor = ArgumentCaptor.forClass(List.class);
    verify(repository).registerTableAssets(captor.capture());
    TableAssetSpec write = captor.getValue().get(0);
    assertThat(write.tableType()).isEqualTo("TABLE");
    assertThat(write.remarks()).isEqualTo("插件中的用户表描述");
    assertThat(write.registeredBy()).isEqualTo("tester");
    assertThat(result.registered()).isEqualTo(1);
  }

  @Test
  void shouldBlockUnregisterWhenMonitorExists() {
    when(repository.countMonitorsForTableAsset(10L)).thenReturn(1);
    assertThatThrownBy(() -> manager.unregister(10L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("先删除监控");
  }

  private QualityPhysicalTable table(String name, String remarks) {
    return new QualityPhysicalTable("demo", null, name, "TABLE", remarks);
  }
}
