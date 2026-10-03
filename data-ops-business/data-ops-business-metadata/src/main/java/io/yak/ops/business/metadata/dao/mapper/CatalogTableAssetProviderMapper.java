package io.yak.ops.business.metadata.dao.mapper;

import io.yak.ops.business.metadata.dao.model.CatalogTableAssetRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * {@code yak_metadata_asset} 上<b>资产供给侧</b>的只读语句（M2-2 B：直挂表入台账）。
 *
 * <p>与 {@link LineageCatalogRowMapper} 分界：那边是采集/登记的写契约，这里是 provider 的
 * 只读窗口——只出表级、在场、{@code source_type='METADATA'} 的行，任何语句都不带 SET 子句。
 *
 * <p>原 XML 已移除；三条硬边界谓词（source_type / asset_type / gone_at）逐字保留在注解里。
 */
@Mapper
public interface CatalogTableAssetProviderMapper {

  /**
   * 游标分页：id 升序，供 {@code AssetProvider#cursorList} 对账遍历。
   *
   * <p>三条谓词都是硬边界，缺一个就会把别人的实体卷进对账：
   * {@code source_type='METADATA'} lineage 自己的 DATASOURCE 行与本页共用一张表；
   * {@code asset_type='TABLE'} 列级行（COLUMN）数量是表级的几十倍，首版台账只到表粒度；
   * {@code gone_at IS NULL} 已软删（GONE 候选）的行不参与供给，否则对账会把消失当在场。
   * 指针语义与其余 provider 一致：update_time 增量 + id 游标升序，LIMIT 由上层封顶（≤500）。
   */
  @Select(
      """
      <script>
      SELECT id,
             asset_key     AS assetKey,
             name          AS name,
             display_name  AS displayName,
             summary       AS summary,
             layer_code    AS layerCode,
             owner_user    AS ownerUser,
             entity_status AS entityStatus,
             data_source_id AS dataSourceId,
             database_name AS databaseName,
             schema_name   AS schemaName,
             table_name    AS tableName,
             update_time   AS updateTime
      FROM yak_metadata_asset
      WHERE project_id = #{projectId}
        AND source_type = 'METADATA'
        AND asset_type = 'TABLE'
        AND gone_at IS NULL
      <if test="updatedAfter != null">
        AND update_time &gt; #{updatedAfter}
      </if>
      <if test="afterId != null">
        AND id &gt; #{afterId}
      </if>
      ORDER BY id
      LIMIT #{limit}
      </script>
      """)
  List<CatalogTableAssetRow> selectProviderPage(
      @Param("projectId") Long projectId,
      @Param("updatedAfter") LocalDateTime updatedAfter,
      @Param("afterId") Long afterId,
      @Param("limit") int limit);

  /** 单行读回（sourceId = 本表主键）；边界谓词与分页一致，防跨类型/已软删行借道返回。 */
  @Select(
      """
      SELECT id,
             asset_key     AS assetKey,
             name          AS name,
             display_name  AS displayName,
             summary       AS summary,
             layer_code    AS layerCode,
             owner_user    AS ownerUser,
             entity_status AS entityStatus,
             data_source_id AS dataSourceId,
             database_name AS databaseName,
             schema_name   AS schemaName,
             table_name    AS tableName,
             update_time   AS updateTime
      FROM yak_metadata_asset
      WHERE id = #{id}
        AND source_type = 'METADATA'
        AND asset_type = 'TABLE'
        AND gone_at IS NULL
      """)
  CatalogTableAssetRow selectProviderRowById(@Param("id") Long id);
}
