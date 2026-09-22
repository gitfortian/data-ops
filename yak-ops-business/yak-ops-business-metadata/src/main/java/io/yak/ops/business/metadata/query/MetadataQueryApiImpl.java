package io.yak.ops.business.metadata.query;

import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.ops.business.metadata.api.EntityDTO;
import io.yak.ops.business.metadata.api.EntityQuery;
import io.yak.ops.business.metadata.api.MetadataQueryApi;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * {@link MetadataQueryApi} 的实现（ticket 118，plan §5.2）。
 *
 * <p>本类<b>不读库</b>，这是"实现里禁止出现独立表、第二套行映射"的结构保证：
 * 检索走 {@link MetadataSearchService} 的同一条计划装配路径，按键直走 {@link CatalogQueryService}，
 * 两者又共用 {@link CatalogRowRead}。这里只剩入参拆装与分页换算。
 *
 * <p>与 HTTP 面的唯一差别是不出 facet/explain——进程内消费方要的是行，聚合计数是展示需求。
 */
@Service
public class MetadataQueryApiImpl implements MetadataQueryApi {

  private static final String PHYSICAL_TABLE = "table";

  private final MetadataSearchService searchService;
  private final CatalogQueryService catalogQuery;

  public MetadataQueryApiImpl(MetadataSearchService searchService, CatalogQueryService catalogQuery) {
    this.searchService = searchService;
    this.catalogQuery = catalogQuery;
  }

  @Override
  public PagingData<EntityDTO> search(EntityQuery query) {
    MetadataSearchService.SearchResultView view =
        searchService.search(new MetadataSearchService.SearchRequest(
            query.q(), query.typeNames(), query.filters(), null, null, null, null, null, null,
            (query.pageNo() - 1) * query.pageSize(), query.pageSize(), false, false, false));
    List<EntityDTO> records = view.items().stream().map(catalogQuery::toEntityDto).toList();
    return PagingData.from(
        PageData.of(records, view.total(), query.pageNo(), query.pageSize()));
  }

  @Override
  public Optional<EntityDTO> getEntity(long id) {
    return catalogQuery.byId(id);
  }

  @Override
  public List<EntityDTO> listEntities(List<Long> ids) {
    return catalogQuery.byIds(ids);
  }

  @Override
  public List<EntityDTO> listChildren(long parentId, String typeName) {
    String childTypeName = typeName;
    if (childTypeName == null || childTypeName.isBlank()) {
      // 未点名子类型时按元模型推导：调用方只说"给我这个实体的下一层"。
      Optional<EntityDTO> parent = catalogQuery.byId(parentId);
      if (parent.isEmpty()) {
        return List.of();
      }
      childTypeName = catalogQuery.childTypeOf(parent.get().typeName()).orElse(null);
    }
    return catalogQuery.children(parentId, childTypeName);
  }

  @Override
  public List<EntityDTO> listPhysicalColumns(String datasourceId, String database, String table) {
    return catalogQuery.physicalColumns(datasourceId, database, table);
  }

  @Override
  public Optional<EntityDTO> findPhysicalTable(String assetKey) {
    if (assetKey == null || assetKey.isBlank()) {
      return Optional.empty();
    }
    return catalogQuery.byAssetKey(assetKey)
        .filter(entity -> PHYSICAL_TABLE.equals(entity.typeName()));
  }
}
