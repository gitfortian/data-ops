package io.yak.ops.business.metadata.dao.mapper;

import io.yak.ops.business.metadata.dao.model.CatalogAssetRow;
import io.yak.ops.business.metadata.dao.model.CatalogAssetState;
import io.yak.ops.business.metadata.dao.model.CatalogPresenceRow;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 共表 {@code yak_metadata_asset} 上<b>目录侧</b>的读写语句。
 *
 * <p>不叫 {@code MetadataAssetMapper} 而带 {@code Lineage} 前缀，是为了让"这张表的 steward 不是我们"
 * 在类名上就说清楚（模块 ARCHITECTURE.md 的共表契约）。这里只写目录 owns 的列，
 * {@code asset_type}/{@code parent_asset_id}/{@code properties}/{@code project_id} 一律不进 UPDATE 子句。
 *
 * <p>没有继承 {@code BaseMapper}：本表不能用通用 CRUD 碰（会绕过指纹分岔与列分界），
 * 每条语句都必须显式形状，所以只有 XML 里那五条。
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
  List<CatalogAssetState> selectStates(
      @Param("projectId") Long projectId, @Param("assetKeys") Collection<String> assetKeys);

  /**
   * 服务端判增量的批量 upsert（plan §3.3）。
   *
   * @return 语句受影响行数；<b>不用于</b>分 NEW/CHANGED，见 {@link #selectStates}
   */
  int upsertAssets(@Param("rows") Collection<CatalogAssetRow> rows);

  /**
   * 只刷在场时间。与 upsert 分开写，是为了让"刷 {@code last_collect_at} 不触发 CHANGED"
   * 这条口径在 SQL 上无从违反（plan §3.3 末）。
   */
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
   */
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
   */
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
   */
  int markGone(@Param("ids") Collection<Long> ids, @Param("goneAt") LocalDateTime goneAt);
}
