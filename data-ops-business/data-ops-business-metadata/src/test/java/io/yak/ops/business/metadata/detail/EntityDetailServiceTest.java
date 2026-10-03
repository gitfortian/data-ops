package io.yak.ops.business.metadata.detail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.ops.business.metadata.api.EntityDTO;
import io.yak.ops.business.metadata.api.EntityProjection;
import io.yak.ops.business.metadata.api.EntityProvider;
import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.business.metadata.governance.MetadataGovernanceQueryService;
import io.yak.ops.business.metadata.governance.MetadataGovernanceQueryService.LabelView;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry.TypeDefinition;
import io.yak.ops.business.metadata.query.CatalogQueryService;
import io.yak.ops.business.metadata.dao.model.MdTypeDefPO;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

/**
 * 详情聚合的分区容错（ticket 118，plan §10）。
 *
 * <p>这条测试线只回答一个问题：<b>一块读不了，其它块还在不在，缺的那块说不说得清为什么缺。</b>
 * 把失败静默折成空数组是这张页最容易犯、也最难查的错——用户与运维看到的都是"没有"。
 */
class EntityDetailServiceTest {

  private final CatalogQueryService catalog = mock(CatalogQueryService.class);
  private final MetadataGovernanceQueryService governance =
      mock(MetadataGovernanceQueryService.class);
  private final MetadataTypeRegistry typeRegistry = mock(MetadataTypeRegistry.class);
  private final ApplicationContext beans = mock(ApplicationContext.class);
  private final EntityDetailService service =
      new EntityDetailService(catalog, governance, typeRegistry, beans);

  @BeforeEach
  void historyAndLabelsAreHealthyByDefault() {
    when(governance.pageChanges(anyLong(), anyInt(), anyInt()))
        .thenReturn(PageData.of(List.of(), 0L, 1, 20));
    when(governance.labels(anyLong())).thenReturn(List.of(
        new LabelView(1L, "PII", "MANUAL", "CONFIRMED", "lucas", LocalDateTime.now(), null, null)));
  }

  @Test
  void oneFailingBlockDegradesOnlyItself() {
    givenTableEntity();
    when(governance.pageChanges(anyLong(), anyInt(), anyInt()))
        .thenThrow(new IllegalStateException("history table down"));

    EntityDetailService.EntityDetailView view = service.detail(21L);

    assertThat(view.entity().tableName()).isEqualTo("ods_order");
    SectionState history = view.sections().get("history");
    assertThat(history.status()).isEqualTo(SectionState.UNAVAILABLE);
    assertThat(history.code()).isEqualTo(MetadataErrorCode.DETAIL_SECTION_UNAVAILABLE.getCode());
    assertThat(history.message()).contains("history table down");
    assertThat(view.sections().get("labels").status()).isEqualTo(SectionState.OK);
  }

  @Test
  void historyBlockGoesOutAsPagingDataNotRawPageData() {
    givenTableEntity();
    when(governance.pageChanges(anyLong(), anyInt(), anyInt()))
        .thenReturn(PageData.of(
            List.of(new MetadataGovernanceQueryService.ChangeView(
                1L, "UPDATED", "comment", "旧注", "新注", null, null, "lucas", LocalDateTime.now())),
            1L, 1, 20));

    SectionState history = service.detail(21L).sections().get("history");

    // PageData 只有 records()/total() 这类访问器，没有 JavaBean getter，直接上线就是 {}。
    assertThat(history.data()).isInstanceOf(PagingData.class);
    assertThat(((PagingData<?>) history.data()).getBizData()).hasSize(1);
    assertThat(history.status()).isEqualTo(SectionState.OK);
  }

  @Test
  void emptyTimelineReportsAbsentNotHealthy() {
    givenTableEntity();

    assertThat(service.detail(21L).sections().get("history").status())
        .as("一页没有行 = 这块确实没有，不是读通了且有数据")
        .isEqualTo(SectionState.EMPTY);
  }

  @Test
  void missingEntityIsTheOnlyWholeRequestFailure() {
    when(catalog.byId(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.detail(404L))
        .isInstanceOfSatisfying(
            MetadataException.class,
            e -> assertThat(e.getErrorCode().getCode())
                .isEqualTo(MetadataErrorCode.ENTITY_NOT_FOUND.getCode()));
  }

  @Test
  void childrenBlockComesFromTheMetamodelNotATypeConstant() {
    givenTableEntity();
    when(catalog.childTypeOf("table")).thenReturn(Optional.of("tableColumn"));
    when(catalog.children(21L, "tableColumn")).thenReturn(List.of());

    assertThat(service.detail(21L).sections().get("children").status())
        .as("有子级类型但一行没有 = EMPTY（还没采到），不是读不了")
        .isEqualTo(SectionState.EMPTY);
  }

  @Test
  void typeWithoutDeclaredChildHasNoChildrenBlock() {
    givenTableEntity();

    assertThat(service.detail(21L).sections())
        .as("元模型没声明父子对 = 这类实体没有子级，整块不出")
        .doesNotContainKey("children");
  }

  @Test
  void typeWithoutLineageIdentitySaysSoInsteadOfShowingAnEmptyBlock() {
    givenTableEntity();
    when(typeRegistry.find("table"))
        .thenReturn(Optional.of(type("table", null, null, null)));

    SectionState lineage = service.detail(21L).sections().get("lineage");

    assertThat(lineage.status()).isEqualTo(SectionState.UNAVAILABLE);
    assertThat(lineage.message()).contains("尚未登记 lineage_asset_type");
  }

  @Test
  void lineageEntryCarriesTheKeyTheGraphPageReads() {
    givenTableEntity();

    SectionState lineage = service.detail(21L).sections().get("lineage");

    assertThat(lineage.status()).isEqualTo(SectionState.OK);
    assertThat(asMap(lineage.data()).get("path").toString())
        .startsWith("/data-analysis/lineage?assetKey=table%3A1%3Aods%3At1");
  }

  @Test
  void liveSourceFactsGoToExtraAndNeverIntoTheCatalogBag() {
    givenModelEntity();
    EntityProvider provider = new EntityProvider() {
      @Override
      public String typeName() {
        return "dataModel";
      }

      @Override
      public Optional<EntityProjection> refresh(String sourceId) {
        EntityProjection projection = new EntityProjection();
        projection.setSourceId(sourceId);
        projection.getExtra().put("columns", List.of(Map.of("columnName", "order_id")));
        return Optional.of(projection);
      }
    };
    givenModelProvider(provider);

    EntityDetailService.EntityDetailView view = service.detail(88L);
    SectionState source = view.sections().get("source");

    assertThat(source.status()).isEqualTo(SectionState.OK);
    assertThat(((EntityProjection) source.data()).getAttributes())
        .as("源域业务内容不许混进目录那一袋（§10 测试 10）")
        .isEmpty();
    assertThat(((EntityProjection) source.data()).getExtra()).containsKey("columns");
  }

  @Test
  void sourceGoneFromTheDomainIsAbsentNotUnavailable() {
    givenModelEntity();
    EntityProvider provider = new EntityProvider() {
      @Override
      public String typeName() {
        return "dataModel";
      }

      @Override
      public Optional<EntityProjection> refresh(String sourceId) {
        return Optional.empty();
      }
    };
    givenModelProvider(provider);

    SectionState source = service.detail(88L).sections().get("source");

    assertThat(source.status()).isEqualTo(SectionState.EMPTY);
    assertThat(source.message()).contains("ticket 135");
  }

  @Test
  void providerBeanOnTheWrongTypeIsReportedNotRendered() {
    givenModelEntity();
    EntityProvider wrong = mock(EntityProvider.class);
    when(wrong.typeName()).thenReturn("metric");
    givenModelProvider(wrong);

    SectionState source = service.detail(88L).sections().get("source");

    assertThat(source.status()).isEqualTo(SectionState.UNAVAILABLE);
    assertThat(source.code()).isEqualTo(MetadataErrorCode.PROVIDER_UNAVAILABLE.getCode());
    verify(wrong, never()).refresh(any());
  }

  @Test
  void providerAbsentFromThisProcessKeepsTheCatalogFactsReadable() {
    givenModelEntity();
    when(typeRegistry.find("dataModel"))
        .thenReturn(Optional.of(type("dataModel", null, "modelEntityProvider", "TABLE")));
    when(beans.containsBean("modelEntityProvider")).thenReturn(false);

    EntityDetailService.EntityDetailView view = service.detail(88L);

    assertThat(view.sections().get("source").code())
        .isEqualTo(MetadataErrorCode.PROVIDER_UNAVAILABLE.getCode());
    assertThat(view.sections().get("labels").status()).isEqualTo(SectionState.OK);
  }

  @Test
  void providerThatThrowsDegradesOnlyItsOwnBlock() {
    givenModelEntity();
    EntityProvider throwing = new EntityProvider() {
      @Override
      public String typeName() {
        return "dataModel";
      }

      @Override
      public Optional<EntityProjection> refresh(String sourceId) {
        throw new IllegalStateException("modeling down");
      }
    };
    givenModelProvider(throwing);

    EntityDetailService.EntityDetailView view = service.detail(88L);

    assertThat(view.sections().get("source").status()).isEqualTo(SectionState.UNAVAILABLE);
    assertThat(view.sections().get("source").message()).contains("modeling down");
    assertThat(view.sections().get("labels").status()).isEqualTo(SectionState.OK);
  }

  @Test
  void statsBlockReadsTheHarvestedBagNotALiveSystemView() {
    when(catalog.byId(21L)).thenReturn(Optional.of(tableWithStats()));
    when(typeRegistry.find("table"))
        .thenReturn(Optional.of(type("table", "database", null, "TABLE", true)));

    SectionState stats = service.detail(21L).sections().get("stats");

    assertThat(stats.status()).isEqualTo(SectionState.OK);
    Map<String, Object> data = asMap(stats.data());
    assertThat(data)
        .containsEntry("rowCountApprox", 12345L)
        .containsEntry("partitioned", Boolean.TRUE)
        .containsEntry("lastDdlTime", "2026-09-01 03:12:00")
        .containsEntry("approximate", Boolean.TRUE)
        .containsKey("lastCollectAt");
  }

  @Test
  void collectibleTypeWithoutHarvestedStatsSaysSoInsteadOfShowingAllNulls() {
    givenTableEntity();

    SectionState stats = service.detail(21L).sections().get("stats");

    assertThat(stats.status()).isEqualTo(SectionState.EMPTY);
    assertThat(stats.message()).contains("未取到统计");
  }

  @Test
  void physicalEntityHasNoSourceBlockAtAll() {
    givenTableEntity();

    assertThat(service.detail(21L).sections()).doesNotContainKey("source");
  }

  @Test
  void batchReadGoesThroughTheSingleInPath() {
    List<Long> ids = List.of(1L, 2L);
    when(catalog.byIds(ids)).thenReturn(List.of());

    assertThat(service.list(ids)).isEmpty();

    verify(catalog).byIds(ids);
    verify(catalog, never()).byId(anyLong());
  }

  @Test
  void changePageRefusesAnEntityThatIsNotInTheDocument() {
    when(catalog.byId(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.changes(404L, 1, 20))
        .isInstanceOf(MetadataException.class);
    verify(governance, never()).pageChanges(eq(404L), anyInt(), anyInt());
  }

  // ===========================================================================

  private void givenTableEntity() {
    when(catalog.byId(21L))
        .thenReturn(Optional.of(entity(21L, "table", "HARVESTED", "table:1:ods:t1", null)));
    when(typeRegistry.find("table"))
        .thenReturn(Optional.of(type("table", "database", null, "TABLE", true)));
    when(catalog.childTypeOf("table")).thenReturn(Optional.empty());
  }

  private void givenModelEntity() {
    when(catalog.byId(88L))
        .thenReturn(Optional.of(entity(88L, "dataModel", "REGISTERED", "modeling:model:88", "88")));
  }

  private void givenModelProvider(EntityProvider provider) {
    when(typeRegistry.find("dataModel"))
        .thenReturn(Optional.of(type("dataModel", null, "modelEntityProvider", "TABLE")));
    when(beans.containsBean("modelEntityProvider")).thenReturn(true);
    when(beans.getBean("modelEntityProvider", EntityProvider.class)).thenReturn(provider);
  }

  private static EntityDTO entity(
      long id, String typeName, String providerType, String assetKey, String sourceId) {
    return new EntityDTO(
        id,
        typeName,
        Map.of(
            "id", id,
            "assetKey", assetKey,
            "providerType", providerType,
            "sourceId", sourceId == null ? "" : sourceId,
            "tableName", "ods_order"),
        Map.of(),
        Map.of());
  }

  private static EntityDTO tableWithStats() {
    return new EntityDTO(
        21L,
        "table",
        Map.of(
            "id", 21L,
            "assetKey", "table:1:ods:t1",
            "providerType", "HARVESTED",
            "sourceId", "",
            "tableName", "ods_order",
            "lastCollectAt", "2026-09-20 02:00:00"),
        Map.of(
            "rowCountApprox", 12345L,
            "partitioned", true,
            "lastDdlTime", "2026-09-01 03:12:00"),
        Map.of());
  }

  private static TypeDefinition type(
      String typeName, String parentTypes, String providerBean, String lineageAssetType) {
    return type(typeName, parentTypes, providerBean, lineageAssetType, false);
  }

  private static TypeDefinition type(
      String typeName,
      String parentTypes,
      String providerBean,
      String lineageAssetType,
      boolean collectible) {
    MdTypeDefPO po = new MdTypeDefPO();
    po.setId(1L);
    po.setTypeName(typeName);
    po.setCategory("ENTITY");
    po.setStatus("ACTIVE");
    po.setParentTypes(parentTypes);
    po.setProviderBean(providerBean);
    po.setLineageAssetType(lineageAssetType);
    po.setCollectible(collectible);
    return new TypeDefinition(po, List.of());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object data) {
    return (Map<String, Object>) data;
  }
}
