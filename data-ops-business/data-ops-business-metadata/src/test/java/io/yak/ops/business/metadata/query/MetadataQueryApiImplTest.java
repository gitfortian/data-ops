package io.yak.ops.business.metadata.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.common.PagingData;
import io.yak.ops.business.metadata.api.EntityDTO;
import io.yak.ops.business.metadata.api.EntityQuery;
import io.yak.ops.business.metadata.query.MetadataSearchService.SearchRequest;
import io.yak.ops.business.metadata.query.MetadataSearchService.SearchResultView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@code MetadataQueryApi} 只做入参拆装与分页换算（ticket 118）。
 *
 * <p>本测试钉两件事：① 进程内检索与 HTTP 检索走<b>同一条</b>计划装配路径（偏移由页码换算，
 * 不自己拼 SQL）；② 便捷方法不换读法——按键找表仍是那一条目录行读路径，只是多一个类型判据。
 */
class MetadataQueryApiImplTest {

  private final MetadataSearchService searchService = mock(MetadataSearchService.class);
  private final CatalogQueryService catalog = mock(CatalogQueryService.class);
  private final MetadataQueryApiImpl api = new MetadataQueryApiImpl(searchService, catalog);

  @Test
  void searchReusesTheHttpGetPathAndConvertsPageToOffset() {
    when(searchService.search(any(SearchRequest.class)))
        .thenReturn(new SearchResultView(
            List.of(Map.of("id", 21L, "typeName", "table")), List.of(), 7L, 20, 10, null, null));
    when(catalog.toEntityDto(any())).thenReturn(
        new EntityDTO(21L, "table", Map.of("id", 21L), Map.of(), Map.of()));

    PagingData<EntityDTO> page = api.search(new EntityQuery("订单", List.of("table"),
        Map.of("layerCode", "ODS"), 3, 10));

    ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(searchService).search(captor.capture());
    SearchRequest sent = captor.getValue();
    assertThat(sent.from()).as("页码 3 × 每页 10 = 偏移 20").isEqualTo(20);
    assertThat(sent.size()).isEqualTo(10);
    assertThat(sent.queryFilter()).containsEntry("layerCode", "ODS");
    assertThat(sent.postFilter()).as("进程内消费方不做相关性过滤")
        .isNull();
    assertThat(page.getBizData()).hasSize(1);
    assertThat(page.getPagination().getTotal()).isEqualTo(7L);
  }

  @Test
  void listChildrenWithoutATypeNarrowsByTheDeclaredParentPair() {
    when(catalog.byId(21L)).thenReturn(Optional.of(
        new EntityDTO(21L, "table", Map.of("id", 21L), Map.of(), Map.of())));
    when(catalog.childTypeOf("table")).thenReturn(Optional.of("tableColumn"));
    when(catalog.children(21L, "tableColumn")).thenReturn(List.of());

    assertThat(api.listChildren(21L, null)).isEmpty();

    verify(catalog).children(21L, "tableColumn");
  }

  @Test
  void listChildrenOfAnUnknownParentReadsNothing() {
    when(catalog.byId(404L)).thenReturn(Optional.empty());

    assertThat(api.listChildren(404L, null)).isEmpty();

    verify(catalog, never()).children(anyLong(), any());
  }

  @Test
  void findByKeyRefusesARowThatIsNotAPhysicalTable() {
    when(catalog.byAssetKey("column:1:ods:t1:id"))
        .thenReturn(Optional.of(new EntityDTO(31L, "tableColumn", Map.of(), Map.of(), Map.of())));
    when(catalog.byAssetKey("table:1:ods:t1"))
        .thenReturn(Optional.of(new EntityDTO(21L, "table", Map.of(), Map.of(), Map.of())));

    assertThat(api.findPhysicalTable("column:1:ods:t1:id")).isEmpty();
    assertThat(api.findPhysicalTable("table:1:ods:t1")).isPresent();
    assertThat(api.findPhysicalTable(" ")).isEmpty();
  }

  @Test
  void batchListingGoesThroughTheSingleInPath() {
    when(catalog.byIds(List.of(1L, 2L))).thenReturn(List.of());

    assertThat(api.listEntities(List.of(1L, 2L))).isEmpty();

    verify(catalog).byIds(List.of(1L, 2L));
  }
}
