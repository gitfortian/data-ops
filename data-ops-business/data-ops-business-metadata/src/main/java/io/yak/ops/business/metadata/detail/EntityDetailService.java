package io.yak.ops.business.metadata.detail;

import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.ops.business.metadata.api.EntityDTO;
import io.yak.ops.business.metadata.api.EntityProjection;
import io.yak.ops.business.metadata.api.EntityProvider;
import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.business.metadata.governance.MetadataGovernanceQueryService;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry;
import io.yak.ops.business.metadata.query.CatalogQueryService;
import io.yak.ops.business.metadata.dao.model.MdTypeDefPO;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * 实体详情聚合（ticket 118）：一份目录事实 + 若干块，每块独立成败。
 *
 * <p>本类<b>只做装配</b>，一条 SQL 都不写：目录行走 {@link CatalogQueryService}，治理侧表走
 * {@link MetadataGovernanceQueryService}，源域实时走 {@link EntityProvider}。块的构成全部由元模型
 * 数据决定（哪个类型有子级、有没有血缘身份、配了哪个 provider bean），代码里没有一处
 * {@code if ("table".equals(typeName))}——加一类实体不改本文件是这张页的第一约束。
 *
 * <p><b>分区容错</b>：任何一块读失败只把该块标成 {@code UNAVAILABLE} 并带上错误码与原因，
 * 响应仍 200；只有实体本身不在目录里才整体 49001。
 *
 * <p>两块<b>不在这里</b>：存储量按表快照归 lifecycle（{@code GET /api/v1/lifecycle/storage/table}，
 * 前端当独立块调，本模块不引 lifecycle 依赖以免与 modeling→metadata 成环）；统计（行数/分区）
 * 是采集时已落进 {@code attributes} 的 {@code rowCountApprox}/{@code partitioned}/{@code lastDdlTime}，
 * 配 {@code lastCollectAt} 说明新鲜度——实时再读一次系统视图等于绕过采集那一层口径。
 */
@Slf4j
@Service
public class EntityDetailService {

  /** 详情页时间线先给一屏；更多走 {@code /entities/{id}/changes}。 */
  public static final int HISTORY_PAGE_SIZE = 20;

  private final CatalogQueryService catalog;
  private final MetadataGovernanceQueryService governance;
  private final MetadataTypeRegistry typeRegistry;
  private final ApplicationContext beans;

  public EntityDetailService(
      CatalogQueryService catalog,
      MetadataGovernanceQueryService governance,
      MetadataTypeRegistry typeRegistry,
      ApplicationContext beans) {
    this.catalog = catalog;
    this.governance = governance;
    this.typeRegistry = typeRegistry;
    this.beans = beans;
  }

  public record EntityDetailView(EntityDTO entity, Map<String, SectionState> sections) {}

  public EntityDetailView detail(long id) {
    EntityDTO entity = catalog.byId(id)
        .orElseThrow(() -> new MetadataException(MetadataErrorCode.ENTITY_NOT_FOUND, "id=" + id));
    MdTypeDefPO type = typeRegistry.find(entity.typeName())
        .map(definition -> definition.type())
        .orElse(null);
    Map<String, SectionState> sections = new LinkedHashMap<>();
    putStats(sections, entity, type);
    putChildren(sections, entity, type);
    // PageData 只有 records()/total() 这样的访问器，直接进 data 会被序列化成 {}：换 PagingData 上线路才看得见行。
    sections.put("history", guard("history", () ->
        PagingData.from(governance.pageChanges(entity.id(), 1, HISTORY_PAGE_SIZE))));
    sections.put("labels", guard("labels", () -> governance.labels(entity.id())));
    putLineage(sections, entity, type);
    putSource(sections, entity, type);
    return new EntityDetailView(entity, sections);
  }

  /** 批量取实体（选择器、列表页内联）：一条 IN，缺 id 静默跳过——调用方拿到的行数就是"还在场"的行数。 */
  public List<EntityDTO> list(List<Long> ids) {
    return catalog.byIds(ids);
  }

  public PageData<MetadataGovernanceQueryService.ChangeView> changes(
      long id, int pageNo, int pageSize) {
    if (catalog.byId(id).isEmpty()) {
      throw new MetadataException(MetadataErrorCode.ENTITY_NOT_FOUND, "id=" + id);
    }
    return governance.pageChanges(id, pageNo, pageSize);
  }

  // ===========================================================================
  // 各块：构成判据都在元模型里
  // ===========================================================================

  /**
   * 统计块（行数/是否分区/最后 DDL）：三样都在采集时落进属性袋（{@code MetadataHarvestService} 的写侧），
   * 这里只做取出与新鲜度标注。
   *
   * <p>实时再读一次系统视图等于绕过采集层口径——同一张表的行数就会有两个出处、两个时刻，
   * 而对账要比的恰恰是同一把尺子量出来的值。行数天生是引擎估算，故 {@code approximate} 恒真。
   */
  private void putStats(Map<String, SectionState> sections, EntityDTO entity, MdTypeDefPO type) {
    if (type == null || !Boolean.TRUE.equals(type.getCollectible())) {
      return; // 投影类型没有物理统计可报，这块整块不出。
    }
    Map<String, Object> attributes = entity.attributes();
    Map<String, Object> stats = new LinkedHashMap<>();
    putPresent(stats, attributes, "rowCountApprox");
    putPresent(stats, attributes, "partitioned");
    putPresent(stats, attributes, "lastDdlTime");
    if (stats.isEmpty()) {
      sections.put("stats", new SectionState(
          SectionState.EMPTY, null, "最近一轮采集未取到统计（引擎不支持或读取失败按未知处理）", Map.of()));
      return;
    }
    stats.put("approximate", true);
    stats.put("lastCollectAt", entity.facts().get("lastCollectAt"));
    sections.put("stats", SectionState.of(stats));
  }

  private static void putPresent(
      Map<String, Object> target, Map<String, Object> source, String key) {
    Object value = source.get(key);
    if (value != null) {
      target.put(key, value);
    }
  }

  /**
   * 子级列：只有元模型声明了父子对（哪个类型的 {@code parent_types} 含本类型）才出这块。
   * 有子级类型但一行没有 → {@code EMPTY}（"还没采到"由采集侧的运行历史解释）。
   */
  private void putChildren(Map<String, SectionState> sections, EntityDTO entity, MdTypeDefPO type) {
    if (type == null) {
      return;
    }
    Optional<String> childType = catalog.childTypeOf(entity.typeName());
    if (childType.isEmpty()) {
      return;
    }
    sections.put("children", guard("children",
        () -> catalog.children(entity.id(), childType.get())));
  }

  /** 血缘只给入口：本模块不调 lineage，也不为它加 Maven 边（共表 id 与 lineage assetId 同源是既有事实）。 */
  private void putLineage(Map<String, SectionState> sections, EntityDTO entity, MdTypeDefPO type) {
    if (type == null) {
      return;
    }
    if (type.getLineageAssetType() == null || type.getLineageAssetType().isBlank()) {
      // 类型还没在 lineage 里取得身份（plan §2.3 后果 1 等 ticket 134），是"还不能"，不是"没有血缘"。
      sections.put("lineage", SectionState.unavailable(
          MetadataErrorCode.DETAIL_SECTION_UNAVAILABLE.getCode(),
          "类型 " + entity.typeName() + " 尚未登记 lineage_asset_type，血缘图里没有它"));
      return;
    }
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("assetKey", entity.assetKey());
    entry.put("path", "/data-analysis/lineage?assetKey="
        + URLEncoder.encode(entity.assetKey(), StandardCharsets.UTF_8));
    sections.put("lineage", SectionState.of(entry));
  }

  /**
   * 投影实体的源域实时块。寻址用 {@code type_def.provider_bean}，不是类型常量；
   * 取到 bean 后还要核对它的 {@code typeName()}——名字对上了类型接错了，会把模型清单贴到指标详情上。
   */
  private void putSource(Map<String, SectionState> sections, EntityDTO entity, MdTypeDefPO type) {
    if (!entity.projected()) {
      return; // 物理实体没有"源域"可问，采集现场已经在目录里。
    }
    String beanName = type == null ? null : type.getProviderBean();
    if (beanName == null || beanName.isBlank()) {
      sections.put("source", SectionState.unavailable(
          MetadataErrorCode.PROVIDER_UNAVAILABLE.getCode(),
          "类型 " + entity.typeName() + " 未配置 provider_bean，实时读侧无出处"));
      return;
    }
    sections.put("source", guardSource(beanName, entity));
  }

  private SectionState guardSource(String beanName, EntityDTO entity) {
    try {
      EntityProjection projection = resolveProvider(beanName, entity)
          .refresh(entity.sourceId())
          .orElse(null);
      return projection == null
          ? new SectionState(
              SectionState.EMPTY,
              null,
              "源域已无此实体：目录里的投影等对账通道（ticket 135）撤销",
              Map.of())
          : SectionState.of(projection);
    } catch (RuntimeException e) {
      return unavailableFrom(e, "source");
    }
  }

  /** 寻址按 {@code type_def.provider_bean}，再核对 bean 自己声明的类型；对不上就是接线错了，宁可降级不可错贴。 */
  private EntityProvider resolveProvider(String beanName, EntityDTO entity) {
    if (!beans.containsBean(beanName)) {
      // 类型登记了 bean 名但本进程没装那个源域（模块未启用/未部署在一起）。
      throw new MetadataException(MetadataErrorCode.PROVIDER_UNAVAILABLE,
          "provider bean " + beanName + " 未在当前进程装配");
    }
    EntityProvider provider = beans.getBean(beanName, EntityProvider.class);
    if (!entity.typeName().equals(provider.typeName())) {
      throw new MetadataException(MetadataErrorCode.PROVIDER_UNAVAILABLE,
          "provider bean " + beanName + " 声明的类型是 " + provider.typeName()
              + "，与本实体类型 " + entity.typeName() + " 不符");
    }
    return provider;
  }

  private SectionState guard(String block, Supplier<Object> body) {
    try {
      return SectionState.of(body.get());
    } catch (RuntimeException e) {
      return unavailableFrom(e, block);
    }
  }

  private SectionState unavailableFrom(RuntimeException e, String block) {
    if (e instanceof MetadataException metadataException) {
      // 带码的失败要说得出是哪类不可用（provider 没装 ≠ 源域 500），兜成 49025 会指错排查方向。
      log.warn("详情分区 {} 读取失败（仅降级该块）: {}", block, e.getMessage());
      return SectionState.unavailable(
          metadataException.getErrorCode().getCode(), metadataException.getUserMessage());
    }
    log.warn("详情分区 {} 读取失败（仅降级该块）", block, e);
    return SectionState.unavailable(
        MetadataErrorCode.DETAIL_SECTION_UNAVAILABLE.getCode(),
        block + " 读取失败: " + e.getClass().getSimpleName()
            + (e.getMessage() == null ? "" : " " + e.getMessage()));
  }
}
