package io.yak.ops.business.metric.asset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.asset.api.AssetContentHash;
import io.yak.ops.business.asset.api.AssetCursorQuery;
import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.api.AssetPage;
import io.yak.ops.business.asset.api.AssetProvider;
import io.yak.ops.business.metric.config.ConditionalOnMetricPersistence;
import io.yak.ops.business.metric.dao.mapper.MetricMapper;
import io.yak.ops.business.metric.lineage.MetricLineageRegistrationService;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.BusinessDomain;
import io.yak.ops.common.bean.po.metric.MetricPO;
import io.yak.ops.common.enums.asset.AssetEnums.AssetType;
import io.yak.ops.common.enums.asset.AssetSourceType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * METRIC 资产供给(asset ticket 94):只读本域指标表,asset_key 复用血缘登记键生成器
 * {@link MetricLineageRegistrationService#metricAssetKey(Long)}。
 * 域编码经 semantic SPI 尽力解析;解析失败不影响指纹(用 domainId 入指纹),避免依赖抖动误报变更。
 */
@Component
@ConditionalOnMetricPersistence
@RequiredArgsConstructor
@Slf4j
public class MetricAssetProvider implements AssetProvider {

  private final MetricMapper mapper;
  private final ObjectProvider<ProcessApi> processApi;

  @Override
  public AssetSourceType sourceType() {
    return AssetSourceType.METRIC;
  }

  @Override
  public AssetPage cursorList(AssetCursorQuery query) {
    Long afterId = parseIdOrNull(query.cursor());
    List<MetricPO> rows = mapper.selectList(
        new LambdaQueryWrapper<MetricPO>()
            .eq(MetricPO::getProjectId, query.projectId())
            .gt(query.updatedAfter() != null, MetricPO::getUpdateTime, query.updatedAfter())
            .gt(afterId != null, MetricPO::getId, afterId)
            .orderByAsc(MetricPO::getId)
            .last("LIMIT " + query.limit()));
    if (rows.isEmpty()) {
      return AssetPage.empty();
    }
    Map<Long, String> domainCodes = domainCodes();
    List<AssetDescriptor> items =
        rows.stream().map(po -> toDescriptor(po, domainCodes)).toList();
    String nextCursor = rows.size() == query.limit()
        ? String.valueOf(rows.get(rows.size() - 1).getId())
        : null;
    return new AssetPage(items, nextCursor);
  }

  @Override
  public Optional<AssetDescriptor> refresh(String sourceId) {
    Long id = parseIdOrNull(sourceId);
    if (id == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(mapper.selectById(id))
        .map(po -> toDescriptor(po, domainCodes()));
  }

  AssetDescriptor toDescriptor(MetricPO po, Map<Long, String> domainCodes) {
    Map<String, String> extra = new LinkedHashMap<>();
    put(extra, "metricCode", po.getMetricCode());
    put(extra, "metricType", po.getMetricType());
    put(extra, "statPeriod", po.getStatPeriod());
    put(extra, "status", po.getStatus());
    put(extra, "version", po.getVersion());
    return new AssetDescriptor(
        MetricLineageRegistrationService.metricAssetKey(po.getId()),
        String.valueOf(po.getId()),
        po.getMetricName(),
        po.getBusinessDesc(),
        AssetType.METRIC,
        null,
        po.getDomainId() == null ? null : domainCodes.get(po.getDomainId()),
        po.getOwner(),
        po.getUpdateTime(),
        AssetContentHash.of(
            po.getMetricName(), po.getBusinessDesc(), po.getMetricType(), po.getStatus(),
            String.valueOf(po.getDomainId())),
        extra);
  }

  /** 业务域 id → code;semantic 不可用时回空表(域编码留空,不阻塞对账)。 */
  private Map<Long, String> domainCodes() {
    try {
      ProcessApi api = processApi.getIfAvailable();
      if (api == null) {
        return Map.of();
      }
      return api.listDomains().stream()
          .filter(d -> d.code() != null)
          .collect(Collectors.toMap(BusinessDomain::id, BusinessDomain::code, (a, b) -> a));
    } catch (RuntimeException e) {
      log.warn("Resolve domain codes failed (degraded to empty): {}", e.getMessage());
      return Map.of();
    }
  }

  private static void put(Map<String, String> map, String key, Object value) {
    if (value != null) {
      map.put(key, String.valueOf(value));
    }
  }

  private static Long parseIdOrNull(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
