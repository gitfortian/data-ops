package io.yak.ops.business.metadata.dao.mapper;

import io.yak.ops.business.metadata.dao.model.CatalogAssetRow;
import io.yak.ops.business.metadata.dao.model.CatalogAssetState;
import io.yak.ops.business.metadata.dao.model.CatalogPresenceRow;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 共表 {@code yak_metadata_asset} 上<b>目录侧</b>的读写语句。
 *
 * <p>不叫 {@code MetadataAssetMapper} 而带 {@code Lineage} 前缀，是为了让"这张表的 steward 不是我们"
 * 在类名上就说清楚（模块 ARCHITECTURE.md 的共表契约）。这里只写目录 owns 的列，
 * {@code asset_type}/{@code parent_asset_id}/{@code properties}/{@code project_id} 一律不进 UPDATE 子句。
 *
 * <p>没有继承 {@code BaseMapper}：本表不能用通用 CRUD 碰（会绕过指纹分岔与列分界），
 * 每条语句都必须显式形状。原 XML 已移除，形状逐字保留在注解里——包括下面 {@link #upsertAssets}
 * 的三条不变式注释，它们说明"为什么 UPDATE 子句里没有 lineage owns 的列"。
 */
@Mapper
public interface LineageCatalogRowMapper {

  /**
   * 批量读回已存在行的指纹与 id，用于<b>写前分岔</b>与写后挂变更流水。
   *
   * <p>必须存在这一趟预读：Connector/J 默认 {@code useAffectedRows=false}（会带 CLIENT_FOUND_ROWS），
   * {@code INSERT … ON DUPLICATE} 的受影响行数区分不了"新插入"与"命中未变"，
   * 四类计数只能靠比对预读结果在 Java 侧算（plan §3.3）。
   */
  @Select(
      """
      <script>
      SELECT id,
             asset_key     AS assetKey,
             provider_type AS providerType,
             content_hash  AS contentHash,
             source_hash   AS sourceHash,
             source_updated_at AS sourceUpdatedAt,
             gone_at       AS goneAt
      FROM yak_metadata_asset
      WHERE project_id = #{projectId}
        AND asset_key IN
      <foreach collection="assetKeys" item="key" open="(" separator="," close=")">
          #{key}
      </foreach>
      </script>
      """)
  List<CatalogAssetState> selectStates(
      @Param("projectId") Long projectId, @Param("assetKeys") Collection<String> assetKeys);

  /**
   * 服务端判增量的批量 upsert（plan §3.3）。
   *
   * <p>共表写路径的三条不变式都写在这里，因为它们是"编译得过、语义已坏"那一类，只能靠 SQL 形状本身守：
   *
   * <p>① UPDATE 子句里没有 lineage owns 的列：asset_type / parent_asset_id / properties / project_id /
   * name / source_type / source_id / data_source_id…column_name。它们在 INSERT 列表里出现一次，
   * 之后不刷（§2.3 后果 1/2/5）。properties 连 INSERT 都不参与——目录的属性袋是 md_attributes。
   *
   * <p>② create_time / update_time 必须显式带值：这张表由 lineage 建，这两列 NOT NULL 且无默认值，
   * strict 模式省掉就是 1364。
   *
   * <p>③ 没变的行不产生实际写入：三个 {@code <=>} 守卫让 SET 赋回原值，MySQL 不记行变更，
   * "重跑幂等 = 除 last_collect_at 外零写入"靠这一段成立。
   *
   * @return 语句受影响行数；<b>不用于</b>分 NEW/CHANGED，见 {@link #selectStates}
   */
  @Insert(
      """
      <script>
      INSERT INTO yak_metadata_asset
          (project_id, asset_key, asset_type, name, source_type, source_id, parent_asset_id,
           data_source_id, database_name, schema_name, table_name, column_name,
           type_id, display_name, fully_qualified_name, fqn_hash, summary, entity_status,
           owner_user, domain_ids, layer_code,
           provider_type, collect_job_id, content_hash, source_hash, source_updated_at,
           first_seen_at, last_collect_at, md_attributes, updated_by, create_time, update_time)
      VALUES
      <foreach collection="rows" item="row" separator=",">
          (#{row.projectId}, #{row.assetKey}, #{row.assetType}, #{row.name}, #{row.sourceType},
           #{row.sourceId}, #{row.parentAssetId}, #{row.dataSourceId}, #{row.databaseName},
           #{row.schemaName}, #{row.tableName}, #{row.columnName},
           #{row.typeId}, #{row.displayName}, #{row.fullyQualifiedName}, #{row.fqnHash},
           #{row.summary}, #{row.entityStatus},
           #{row.ownerUser}, #{row.domainIds}, #{row.layerCode},
           #{row.providerType}, #{row.collectJobId},
           #{row.contentHash}, #{row.sourceHash}, #{row.sourceUpdatedAt}, #{row.firstSeenAt},
           #{row.lastCollectAt}, #{row.mdAttributes}, #{row.updatedBy}, NOW(6), NOW(6))
      </foreach>
      ON DUPLICATE KEY UPDATE
      <![CDATA[
          id = LAST_INSERT_ID(id),
          -- 先算这一条：三把指纹都没动就保持 update_time 不变，整行不产生写入（不变式 ③）。
          -- 放在最前是必须的——SET 从左往右求值，往后 content_hash 已是新值，比不出"变没变"。
          update_time = IF((content_hash  <=> VALUES(content_hash))
                           AND (source_hash <=> VALUES(source_hash))
                           AND (md_attributes <=> VALUES(md_attributes)),
                           update_time, NOW(6)),
          -- <=> 而不是 =：两把指纹都允许 NULL（遗留行），= 遇 NULL 恒为 NULL 会判成"变了"。
          content_hash = IF(content_hash  <=> VALUES(content_hash),  content_hash,  VALUES(content_hash)),
          source_hash  = IF(source_hash   <=> VALUES(source_hash),   source_hash,   VALUES(source_hash)),
          md_attributes = IF(md_attributes <=> VALUES(md_attributes), md_attributes, VALUES(md_attributes)),
          type_id = VALUES(type_id),
          display_name = VALUES(display_name),
          fully_qualified_name = VALUES(fully_qualified_name),
          fqn_hash = VALUES(fqn_hash),
          summary = VALUES(summary),
          -- 投影归属列（ticket 130）：IF 守卫让"这条通道没交值"不等于"把值清空"——
          -- 采集侧永远交 NULL，若无守卫，一轮物理采集就会抹掉登记通道写进的 owner/域/分层。
          owner_user = IF(VALUES(owner_user) IS NULL, owner_user, VALUES(owner_user)),
          domain_ids = IF(VALUES(domain_ids) IS NULL, domain_ids, VALUES(domain_ids)),
          layer_code = IF(VALUES(layer_code) IS NULL, layer_code, VALUES(layer_code)),
          provider_type = VALUES(provider_type),
          collect_job_id = VALUES(collect_job_id),
          updated_by = VALUES(updated_by),
          -- 在场即复活：曾 GONE 的实体回来只清标记，entity_status 不在这里改（人工治理状态不被采集刷掉）。
          gone_at = NULL
          -- 到此为止：entity_status / first_seen_at / catalog_version / last_collect_at 与 ① 里那批列都不刷。
      ]]>
      </script>
      """)
  int upsertAssets(@Param("rows") Collection<CatalogAssetRow> rows);

  /**
   * 只刷在场时间。与 upsert 分开写，是为了让"刷 {@code last_collect_at} 不触发 CHANGED"
   * 这条口径在 SQL 上无从违反（plan §3.3 末）。
   */
  @Update(
      """
      <script>
      UPDATE yak_metadata_asset
      SET last_collect_at = #{collectedAt}
      WHERE project_id = #{projectId}
        AND asset_key IN
      <foreach collection="assetKeys" item="key" open="(" separator="," close=")">
          #{key}
      </foreach>
      </script>
      """)
  int touchLastCollected(
      @Param("projectId") Long projectId,
      @Param("assetKeys") Collection<String> assetKeys,
      @Param("collectedAt") LocalDateTime collectedAt);

  /**
   * 读回<b>本模块自己</b>在当前空间里仍在场（{@code gone_at IS NULL}）的行，供 ticket 115 判 GONE。
   *
   * <p>{@code source_type='METADATA'} 这一条是硬边界：我们的 {@code source_id} 就是数据源 id，
   * 而 lineage 自己的目录行用同一个数字做 {@code source_id}（它的 {@code source_type} 是
   * {@code DATASOURCE}）。少了这个条件，一轮采集的缺席判定会去数别人的行，然后软删别人的实体。
   *
   * <p>不带 {@code asset_key} 条件：判 GONE 问的正是"哪些键<b>不</b>在本轮见过的集合里"，
   * 按缺席写条件就会把"缺席"这个事实提前烧进 SQL，Java 侧再也无法复算。
   *
   * <p>ticket 115：在场性扫描。四个条件缺一个都不能上生产——
   * {@code provider_type='HARVESTED'} 登记通道（REGISTERED）的行由源域负责缺席，采集无权判它消失；
   * {@code source_type='METADATA'} 见上；
   * {@code gone_at IS NULL} 已软删的行不再参与计数，否则一轮 GONE 会让下一轮的分母永远偏大；
   * {@code LIMIT} 扫描是"整个数据源的在场集"，行数由源侧决定，不给上限就是别人的一次
   * 误建库能把我们的一次采集跑成内存问题；触顶由上层判 SUSPECT。
   */
  @Select(
      """
      SELECT id,
             asset_key     AS assetKey,
             asset_type    AS assetType,
             database_name AS databaseName,
             schema_name   AS schemaName,
             table_name    AS tableName,
             content_hash  AS contentHash,
             last_collect_at AS lastCollectAt
      FROM yak_metadata_asset
      WHERE project_id = #{projectId}
        AND source_type = 'METADATA'
        AND source_id = #{sourceId}
        AND provider_type = 'HARVESTED'
        AND gone_at IS NULL
      ORDER BY id
      LIMIT #{limit}
      """)
  List<CatalogPresenceRow> selectPresenceRows(
      @Param("projectId") Long projectId,
      @Param("sourceId") String sourceId,
      @Param("limit") int limit);

  /**
   * 读回<b>登记通道</b>（{@code provider_type='REGISTERED'}）在某源下当前在场的行，
   * 供 unregister 圈定软删候选（ticket 130）。
   *
   * <p>与 {@link #selectPresenceRows} 分开写而不是加参数复用：两者的 {@code provider_type} 谓词
   * 相反且都是各自的硬边界——采集无权软删登记行，unregister 同样无权软删采集行。
   * 合成一条语句后"忘了传 provider_type"就是一次跨通道误删，而两条语句各错各的都过不了自己的测试。
   *
   * <p>读 source_hash：REGISTERED 行的 content_hash 恒为 NULL，GONE 流水的"消失前指纹"只能取源指纹。
   */
  @Select(
      """
      SELECT id,
             asset_key     AS assetKey,
             asset_type    AS assetType,
             content_hash  AS contentHash,
             source_hash   AS sourceHash,
             last_collect_at AS lastCollectAt
      FROM yak_metadata_asset
      WHERE project_id = #{projectId}
        AND source_type = 'METADATA'
        AND source_id = #{sourceId}
        AND type_id = #{typeId}
        AND provider_type = 'REGISTERED'
        AND gone_at IS NULL
      ORDER BY id
      LIMIT #{limit}
      """)
  List<CatalogPresenceRow> selectRegisteredRows(
      @Param("projectId") Long projectId,
      @Param("typeId") Long typeId,
      @Param("sourceId") String sourceId,
      @Param("limit") int limit);

  /**
   * 软删：只置 {@code gone_at}，<b>不物理删</b>，也不碰 {@code entity_status} 与 {@code yak_md_label}
   * （plan §3.4：一张表消失可能只是临时下线，标签与治理状态是人的判断，不由采集单方面清掉）。
   *
   * <p>WHERE 里再带一次 {@code gone_at IS NULL}：并发轮次或人工链路已先行处理时，本条就不该再生效，
   * 返回的受影响行数也让上层看得见这个差额。
   *
   * <p>软删只有这一条语句，且 SET 里只有 gone_at 与 update_time 两列：
   * 实体消失不代表它从未存在（plan §2.4.5），所以 first_seen_at / entity_status / 标签一律留在原地，
   * 等它回来时还是同一行同一段治理历史（upsert 的 gone_at = NULL 即复活）。
   */
  @Update(
      """
      <script>
      UPDATE yak_metadata_asset
      SET gone_at = #{goneAt}, update_time = #{goneAt}
      WHERE gone_at IS NULL
        AND source_type = 'METADATA'
        AND id IN
      <foreach collection="ids" item="id" open="(" separator="," close=")">
          #{id}
      </foreach>
      </script>
      """)
  int markGone(@Param("ids") Collection<Long> ids, @Param("goneAt") LocalDateTime goneAt);
}
