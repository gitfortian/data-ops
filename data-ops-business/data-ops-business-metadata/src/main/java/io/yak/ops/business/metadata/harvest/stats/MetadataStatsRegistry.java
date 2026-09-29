package io.yak.ops.business.metadata.harvest.stats;

import io.yak.ops.business.metadata.harvest.stats.MetadataStatsProvider.TableStats;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 按 {@link MetadataStatsProvider#supports(String)} 路由的统计注册表（照 {@code AssetProviderRegistry}
 * 的多 bean 收集形态：加一种方言 = 加一个 bean，本类与采集主流程都不用改）。
 *
 * <p>与 asset 那个注册表有一处刻意的不同：这里<b>不在启动期查重就失败</b>。统计是可缺项，
 * 两个实现同时声称支持一种方言时，取先注册者并在日志里点名——为一条"锦上添花"的数据而让应用启不来，
 * 是拿整个平台换元数据的一个筛选项。
 *
 * <p>本类是采集侧统计的<b>唯一</b>兜底处：任何实现抛异常都在这里咽下去换成空 Map，
 * 因为统计失败必须不影响实体是否登记（plan §3.2 只把统计当属性补全）。
 */
@Slf4j
@Component
public class MetadataStatsRegistry {

  private final List<MetadataStatsProvider> providers;

  public MetadataStatsRegistry(ObjectProvider<MetadataStatsProvider> discovered) {
    this.providers = discovered.stream().toList();
  }

  /** 无匹配实现或读取失败时返回空 Map（= 全部未知），绝不返回 null。 */
  public Map<String, TableStats> load(
      String databaseType, long dataSourceId, String database, String schema) {
    for (MetadataStatsProvider provider : providers) {
      if (!provider.supports(databaseType)) {
        continue;
      }
      try {
        Map<String, TableStats> stats = provider.load(dataSourceId, database, schema);
        return stats == null ? Map.of() : stats;
      } catch (RuntimeException e) {
        log.warn(
            "统计读取失败，本轮按未知处理（不影响实体登记） provider={} dataSource={} scope={}.{}",
            provider.getClass().getSimpleName(),
            dataSourceId,
            database,
            schema,
            e);
        return Map.of();
      }
    }
    return Map.of();
  }

  /** 已装配的方言实现数；看门狗与"统计层是否真的在工作"的断言用。 */
  public int registeredCount() {
    return providers.size();
  }
}
