package io.yak.ops.business.metadata.harvest.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metadata.harvest.stats.MetadataStatsProvider.TableStats;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 统计注册表的路由与兜底（ticket 114）。
 *
 * <p>本类要证明的是<b>启动期与运行期都不会因为统计而炸</b>：两个实现抢同一方言时取先注册者而不是
 * 抛异常——为一条锦上添花的数据让应用起不来，是拿整个平台换元数据的一个筛选项。
 */
class MetadataStatsRegistryTest {

  private final MetadataStatsProvider mysql = supporting("MYSQL");
  private final MetadataStatsProvider doris = supporting("DORIS");

  @SuppressWarnings("unchecked")
  private MetadataStatsRegistry registry(MetadataStatsProvider... providers) {
    ObjectProvider<MetadataStatsProvider> discovered = mock(ObjectProvider.class);
    when(discovered.stream()).thenAnswer(call -> Stream.of(providers));
    return new MetadataStatsRegistry(discovered);
  }

  @Test
  void theFirstProviderClaimingTheDialectIsTheOneThatGetsCalled() {
    MetadataStatsProvider rival = supporting("MYSQL");
    when(rival.load(7L, "shop", "")).thenReturn(Map.of("orders", new TableStats(10L, null, null)));
    when(mysql.load(7L, "shop", "")).thenReturn(Map.of("orders", new TableStats(999L, null, null)));

    Map<String, TableStats> stats = registry(rival, mysql).load("MYSQL", 7L, "shop", "");

    assertThat(stats.get("orders").rowCountApprox()).isEqualTo(10L);
    // 先注册者胜出：重复声明方言不是错误，但也不能让两轮采集随机换实现、给出两个行数。
    verify(mysql, never()).load(anyLong(), any(), any());
  }

  @Test
  void anUnroutedDialectSimplyHasNoStats() {
    assertThat(registry(mysql).load("DORIS", 7L, "shop", "")).isEmpty();
    assertThat(registry(mysql).load(null, 7L, "shop", "")).isEmpty();
    verify(mysql, never()).load(
        anyLong(),
        any(),
        any());
  }

  @Test
  void aProviderThatThrowsCannotFailTheRound() {
    when(mysql.load(7L, "shop", "")).thenThrow(new IllegalStateException("系统视图不可读"));

    assertThat(registry(mysql).load("MYSQL", 7L, "shop", "")).isEmpty();
  }

  @Test
  void aProviderThatReturnsNullIsReadAsNoKnowledgeNotAsAnEmptyRound() {
    when(mysql.load(7L, "shop", "")).thenReturn(null);

    assertThat(registry(mysql).load("MYSQL", 7L, "shop", "")).isEmpty();
  }

  @Test
  void anEmptyRegistryIsTheNormalStateWhenNoPluginIsInstalled() {
    MetadataStatsRegistry empty = registry();

    assertThatCode(() -> empty.load("MYSQL", 7L, "shop", "")).doesNotThrowAnyException();
    assertThat(empty.load("MYSQL", 7L, "shop", "")).isEmpty();
    assertThat(empty.registeredCount()).isZero();
    assertThat(registry(mysql, doris).registeredCount()).isEqualTo(2);
  }

  private static MetadataStatsProvider supporting(String dialect) {
    MetadataStatsProvider provider = mock(MetadataStatsProvider.class);
    when(provider.supports(dialect)).thenReturn(true);
    return provider;
  }
}
