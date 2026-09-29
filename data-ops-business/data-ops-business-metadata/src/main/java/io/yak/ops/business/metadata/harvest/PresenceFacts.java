package io.yak.ops.business.metadata.harvest;

import io.yak.ops.business.metadata.harvest.MetadataHarvestService.ScopeOutcome;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一轮采集结束后判 GONE 所需的全部外部事实（ticket 115）。
 *
 * <p>为什么把这些收成一个 record 而不是让 {@link MetadataPresenceService} 自己去查：
 * "本轮见过谁"只存在于采集现场，事后从库里读 {@code last_collect_at} 反推等于用写过的结果去验证写
 * 本身——一轮把 A 表写成 B 表键时，反推会跟着一起错。
 *
 * <p>{@code scopes} 是本 record 的核心：<b>只有它里面出现过、且这一轮读成功的作用域，才允许判 GONE</b>。
 * 任务把 {@code databaseName}/{@code schemaName}/{@code tablePattern} 收窄时，落选的那些实体根本没进
 * 本轮的在场声明，把它们算成"源侧已消失"就是配置改动引发的批量误删（plan §3.4 空 seen 集同一条理由）。
 *
 * @param seenKeys 本轮写过的全部 asset_key（含 UNCHANGED）；空集 = "什么都没发现"
 * @param scopes 每个 (database, schema) 的在场性结论
 * @param previousRoundStartedAt 上一<b>有效</b>轮的开始时刻，即"缺席满两轮"的边界；null = 没有可比轮次
 * @param goneAt 本轮的时刻，写进 {@code gone_at}
 */
public record PresenceFacts(
    Long projectId,
    String sourceId,
    Long collectRunId,
    String operator,
    boolean dryRun,
    String databaseName,
    String schemaName,
    String tablePattern,
    boolean collectColumns,
    Set<String> seenKeys,
    List<ScopeOutcome> scopes,
    LocalDateTime previousRoundStartedAt,
    LocalDateTime goneAt) {

  /** schema 用空串哨兵（MySQL/Doris 没有 schema 这一级），与采集侧 {@code NO_SCHEMA} 同一口径。 */
  public static String scopeKey(String database, String schema) {
    return normalize(database) + "|" + normalize(schema);
  }

  private static String normalize(String raw) {
    return raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
  }

  /** 本轮一条实体都没登记：与"连接器崩了、什么都没发现"无从区分，绝不允许解释成"全没了"（plan §3.4）。 */
  public boolean nothingSeen() {
    return seenKeys == null || seenKeys.isEmpty();
  }

  /** 本轮读到了表清单的作用域——表级 GONE 只在这里判。 */
  public Set<String> readableScopeKeys() {
    return scopeKeys(false);
  }

  /** 连列都读全了的作用域——列级 GONE 只在这里判。 */
  public Set<String> columnCompleteScopeKeys() {
    return scopeKeys(true);
  }

  /** 本轮是否枚举了整个数据源的库清单；点名了单个库就没有"库消失"这个结论可下。 */
  public boolean databasesEnumerated() {
    return databaseName == null || databaseName.isBlank();
  }

  private Set<String> scopeKeys(boolean columnsCompleteOnly) {
    Set<String> keys = new LinkedHashSet<>();
    for (ScopeOutcome scope : scopes == null ? List.<ScopeOutcome>of() : scopes) {
      if (columnsCompleteOnly ? !scope.columnsComplete() : !scope.tablesReadable()) {
        continue;
      }
      keys.add(scopeKey(scope.databaseName(), scope.schemaName()));
    }
    return keys;
  }
}
