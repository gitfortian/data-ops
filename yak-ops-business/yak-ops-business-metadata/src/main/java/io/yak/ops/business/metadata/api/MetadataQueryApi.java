package io.yak.ops.business.metadata.api;

import io.yak.framework.common.PagingData;
import java.util.List;
import java.util.Optional;

/**
 * 目录查询 API（ticket 118，plan §5.2）。消费方是 modeling 符合性对账（工单 120）、quality 选择器（121）、
 * asset TABLE provider 与各源域的详情聚合，全部读<b>同一张</b> {@code yak_metadata_asset}。
 *
 * <p>后两个便捷方法<b>只是</b> {@code type_id}/{@code parent_asset_id} 过滤的语法糖，不是第二套模型：
 * 实现里禁止出现独立表、独立 DTO 或第二套行映射（plan §5.2 的括注，由分层守卫与单测守住）。
 *
 * <p>项目归属一律取服务端可信上下文（{@code CurrentProject}），入参里没有 {@code projectId}（§0.9）。
 */
public interface MetadataQueryApi {

  /** 跨类型统一入口：与 HTTP 的 {@code /search} 同一条计划装配路径，只是不出 facet。 */
  PagingData<EntityDTO> search(EntityQuery query);

  /** 单个实体（详情聚合的目录侧事实）。gone 行与别的项目都读不到。 */
  Optional<EntityDTO> getEntity(long id);

  /** 批量按 id 取（选择器、列表页内联）：<b>一条 IN</b>，不是 N 次单取。 */
  List<EntityDTO> listEntities(List<Long> ids);

  /** 子级实体（表 → 列）。{@code typeName} 给定时收窄到该类型，否则取声明了父子关系的子类型。 */
  List<EntityDTO> listChildren(long parentId, String typeName);

  /** 物理表的列清单——{@code listChildren} 的 {@code type_name=tableColumn} 特例。 */
  List<EntityDTO> listPhysicalColumns(String datasourceId, String database, String table);

  /** 按资产键定位物理表（键由采集侧 {@code PhysicalTableAssetKey} 生成，本 API 不替调用方拼键）。 */
  Optional<EntityDTO> findPhysicalTable(String assetKey);
}
