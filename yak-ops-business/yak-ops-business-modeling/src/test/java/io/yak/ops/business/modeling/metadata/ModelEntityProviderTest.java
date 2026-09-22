package io.yak.ops.business.modeling.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metadata.api.EntityProjection;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper;
import io.yak.ops.business.modeling.lineage.ModelingLineageRegistrationService;
import io.yak.ops.business.modeling.version.ModelPublishedStructureReader;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.modeling.structure.StructureView.ColumnView;
import io.yak.ops.business.modeling.structure.StructureView.PartitionView;
import io.yak.ops.common.bean.po.modeling.ModelingModelPO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code dataModel} 实时读侧契约（metadata ticket 118，plan §1.3 / §10 测试 10）。
 *
 * <p>要钉住的是<b>分袋</b>：源域当前的列清单只进 {@code extra}，{@code attributes} 恒空——
 * 一旦这里往属性袋写东西，目录就成了表结构的第二真相，改动要么被对账误判、要么覆盖登记投影。
 */
class ModelEntityProviderTest {

  private ModelingModelMapper mapper;
  private ModelPublishedStructureReader structureReader;
  private ModelEntityProvider provider;

  @BeforeEach
  void setUp() {
    mapper = mock(ModelingModelMapper.class);
    structureReader = mock(ModelPublishedStructureReader.class);
    provider = new ModelEntityProvider(mapper, structureReader);
  }

  @Test
  void typeNameMatchesTheSeededProviderBeanContract() {
    assertEquals("dataModel", provider.typeName());
  }

  @Test
  void liveStructureGoesToExtraAndTheCatalogBagStaysEmpty() {
    givenModel(42L, false);
    when(structureReader.publishedStructure(42L))
        .thenReturn(structure(
            List.of(column("order_id", "BIGINT", null, null), column("amount", "DECIMAL", 18, 2)),
            new PartitionView("RANGE", List.of("dt"), "dt")));

    EntityProjection projection = provider.refresh("42").orElseThrow();

    assertTrue(projection.getAttributes().isEmpty(), "源域内容抄进属性袋 = 目录多出第二真相");
    List<Map<String, Object>> columns = columns(projection);
    assertEquals(List.of("order_id", "amount"),
        columns.stream().map(column -> column.get("columnName")).toList());
    assertEquals("BIGINT", columns.get(0).get("dataType"));
    assertEquals("DECIMAL(18,2)", columns.get(1).get("dataType"));
    assertEquals("CONFIRMED", columns.get(0).get("fieldRole"));
    assertEquals(7L, columns.get(0).get("stdFieldId"));
    assertEquals(List.of("dt"), partition(projection).get("columns"));
    assertEquals("ODS 订单表", projection.getDisplayName());
    assertEquals("ODS", projection.getLayerCode());
    assertEquals("bob", projection.getOwnerUser());
    assertNull(projection.getSourceHash(),
        "指纹口径归 ticket 131，这里另算一把会让两条通道互判 CHANGED");
  }

  @Test
  void assetKeyIsTheSameStringTheLineageSideRegisters() {
    givenModel(42L, false);
    when(structureReader.publishedStructure(42L)).thenReturn(structure(List.of(), null));

    assertEquals(ModelingLineageRegistrationService.modelAssetKey(42L),
        provider.refresh("42").orElseThrow().getAssetKey());
  }

  @Test
  void deletedMissingAndUnparseableSourcesReadNoStructure() {
    when(mapper.selectById(3L)).thenReturn(model(3L, true));
    assertEquals(Optional.empty(), provider.refresh("3"));
    when(mapper.selectById(4L)).thenReturn(null);
    assertEquals(Optional.empty(), provider.refresh("4"));
    assertEquals(Optional.empty(), provider.refresh("abc"));
    assertEquals(Optional.empty(), provider.refresh(" "));

    verify(structureReader, never()).publishedStructure(anyLong());
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> columns(EntityProjection projection) {
    return (List<Map<String, Object>>) projection.getExtra().get("columns");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> partition(EntityProjection projection) {
    return (Map<String, Object>) projection.getExtra().get("partition");
  }

  private void givenModel(long id, boolean deleted) {
    when(mapper.selectById(id)).thenReturn(model(id, deleted));
  }

  private static ModelingModelPO model(long id, boolean deleted) {
    ModelingModelPO po = new ModelingModelPO();
    po.setId(id);
    po.setProjectId(1L);
    po.setModelCode("dwd_order");
    po.setModelName("ODS 订单表");
    po.setDescription("订单明细");
    po.setDialect("DORIS");
    po.setLayerCode("ODS");
    po.setStatus("PUBLISHED");
    po.setLatestVersionNo(3);
    po.setDomainId(9L);
    po.setCreatedBy("alice");
    po.setUpdatedBy("bob");
    po.setUpdateTime(LocalDateTime.now());
    po.setDeleted(deleted);
    return po;
  }

  private static StructureView structure(List<ColumnView> columns, PartitionView partition) {
    return new StructureView(
        42L, "dwd_order", "ODS 订单表", "DORIS", "PUBLISHED", "订单明细",
        "dwd_order_df", "订单明细表", columns, List.of("order_id"), List.of(), partition, Map.of());
  }

  private static ColumnView column(String name, String dataType, Integer length, Integer scale) {
    return new ColumnView(
        1L, name, dataType, length, scale, false, null, "订单号", "订单唯一编号", 0,
        null, null, null, null, null, null, "BIGINT".equals(dataType) ? 7L : null,
        "CONFIRMED", null, null);
  }
}
