package io.yak.ops.business.metadata.dao.mapper;

import io.yak.ops.business.metadata.dao.model.CatalogTableAssetRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code yak_metadata_asset} 上<b>资产供给侧</b>的只读语句（M2-2 B：直挂表入台账）。
 *
 * <p>与 {@link LineageCatalogRowMapper} 分界：那边是采集/登记的写契约，这里是 provider 的
 * 只读窗口——只出表级、在场、{@code source_type='METADATA'} 的行，任何语句都不带 SET 子句。
 */
@Mapper
public interface CatalogTableAssetProviderMapper {

  /** 游标分页：id 升序，供 {@code AssetProvider#cursorList} 对账遍历。 */
  List<CatalogTableAssetRow> selectProviderPage(
      @Param("projectId") Long projectId,
      @Param("updatedAfter") LocalDateTime updatedAfter,
      @Param("afterId") Long afterId,
      @Param("limit") int limit);

  /** 单行读回（sourceId = 本表主键）；边界谓词与分页一致，防跨类型/已软删行借道返回。 */
  CatalogTableAssetRow selectProviderRowById(@Param("id") Long id);
}
