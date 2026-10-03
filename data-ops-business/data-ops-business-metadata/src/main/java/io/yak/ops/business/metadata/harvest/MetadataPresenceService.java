package io.yak.ops.business.metadata.harvest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.metadata.dao.mapper.MdCollectRunMapper;
import io.yak.ops.business.metadata.dao.model.CatalogPresenceRow;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.GoneCommand;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.GoneRow;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.PresenceScan;
import io.yak.ops.business.metadata.dao.model.MdCollectRunPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.ProviderType;
import io.yak.ops.common.enums.metadata.MetadataEnums.RunStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * GONE 判定与质量熔断（ticket 115，plan §3.4）。<b>采集侧唯一会造成不可逆损失的环节</b>，
 * 所以本类的每一条规则都是"证据不足就什么都不做"的形状。
 *
 * <h2>四道闸，缺一都可能清库</h2>
 * <ol>
 *   <li><b>空 seen 集 → 零 GONE</b>。OM 的原话（蒸馏 §3.4）：连接器崩了与"什么都没发现"无从区分，
 *       把空集解释成"作用域内全部过期"会静默删掉整个 service/database。
 *   <li><b>只有本轮完整声明过的作用域才有缺席资格</b>：表清单读失败的作用域（{@code failed=true}）、
 *       列读不全的表（{@code partialTables>0}）、被 {@code tablePattern} 排除的表、
 *       {@code databaseName}/{@code schemaName} 点名范围之外的行，一律<b>不进候选</b>。
 *   <li><b>连续两轮缺席</b>：判据 {@code last_collect_at < 上一有效轮开始时刻}。FAILED 轮次<b>不算有效轮次</b>
 *       ——它什么都没采到，拿它的时刻当边界会把"没人看见"降级成"确实不在"。
 *   <li><b>坍塌比例熔断</b>：单轮缺席表数 / 本轮在场表数 &gt; 阈值（默认 30%，{@code
 *       yak.metadata.harvest.gone-collapse-ratio}）→ 整轮 {@code SUSPECT}、<b>不落任何 GONE</b>、
 *       不撤销血缘。理由：本平台数据源少、单库表数波动大，一次连接超时就能让"整库消失"看起来成立；
 *       OM 靠 scope 粒度天然分散了这个风险，我们必须显式拦。
 * </ol>
 *
 * <p><b>祖先覆盖跳过</b>（OM 六道安全的最后一条）：一张表被判 GONE 时它的列不再单独判——列必然跟着表一起
 * 不在，逐个记 12.6 倍条流水只会把一次真实变更淹在噪音里，表行才是那条有效证据。
 *
 * <p><b>GONE 是软删</b>：只置 {@code gone_at}，不物理删、不动 {@code entity_status}、不删标签。
 * 撤销血缘只在确认 GONE 之后发生（{@link CatalogGraphRevocation}），撤销能力未装配时如实记日志。
 *
 * <p>本类不碰任何写事务边界：读扫描 + 纯判定在这里，软删与流水在
 * {@link AssetUpsertRepository#markGone}，一个方法一次往返，熔断时那条写路径根本不会被调用。
 */
@Slf4j
@Component
public class MetadataPresenceService {

  /** 一次扫描的行数上限；触顶即分母不可信 → SUSPECT。列数约为表数 12.6 倍，故按整库量级留余量。 */
  static final int SCAN_LIMIT = 50_000;

  /**
   * 熔断的最小缺席表数：比例单独用在小库上会永久卡死。
   *
   * <p>两表的库删掉一张就是 50%，十张里删三张是 30%——同一件事在小库上永远越阈值，于是它的 GONE
   * 一条也落不了地，目录里留着再也不会出现的表直到永远。真实坍塌（连接超时、整库不可见）从来不会只
   * 少一张表，所以下限定在两张：单张缺席交给"连续两轮"那道闸去保，比例只负责拦批量。
   */
  private static final int MIN_COLLAPSED_TABLES = 2;

  private static final String ASSET_DATABASE = "DATABASE";
  private static final String ASSET_TABLE = "TABLE";
  private static final String ASSET_COLUMN = "COLUMN";

  private final AssetUpsertRepository upsertRepository;
  private final MdCollectRunMapper runMapper;
  private final ObjectProvider<CatalogGraphRevocation> revocations;
  private final double collapseRatio;

  public MetadataPresenceService(
      AssetUpsertRepository upsertRepository,
      MdCollectRunMapper runMapper,
      ObjectProvider<CatalogGraphRevocation> revocations,
      @Value("${yak.metadata.harvest.gone-collapse-ratio:0.3}") double collapseRatio) {
    this.upsertRepository = upsertRepository;
    this.runMapper = runMapper;
    this.revocations = revocations;
    this.collapseRatio = collapseRatio;
  }

  /**
   * 上一<b>有效</b>轮的开始时刻，即"缺席满两轮"的边界；没有可比轮次时返回 null（本轮不判 GONE）。
   *
   * <p>只认 SUCCESS 与 SUSPECT：dry-run 不改现场、FAILED 什么都没采到，两者都不能充当"那时它还该被看见"
   * 的证据。SUSPECT 之所以算有效——它确实跑完了目录枚举、只是拒绝落 GONE。
   */
  public LocalDateTime previousRoundStartedAt(Long projectId, Long jobId, Long currentRunId) {
    MdCollectRunPO previous =
        runMapper.selectOne(
            new LambdaQueryWrapper<MdCollectRunPO>()
                .eq(MdCollectRunPO::getProjectId, projectId)
                .eq(MdCollectRunPO::getJobId, jobId)
                .ne(MdCollectRunPO::getId, currentRunId)
                .eq(MdCollectRunPO::getDryRun, false)
                .in(MdCollectRunPO::getStatus, RunStatus.SUCCESS.name(), RunStatus.SUSPECT.name())
                .orderByDesc(MdCollectRunPO::getStartedAt)
                .last("LIMIT 1"));
    return previous == null ? null : previous.getStartedAt();
  }

  /** 判一轮的缺席并落软删。四道闸全过才可能写库，任一闸拦下都是"零写入 + 一条理由"。 */
  public PresenceOutcome evaluateAndApply(PresenceFacts facts) {
    if (facts.previousRoundStartedAt() == null) {
      // 可能是任务首轮，也可能是前几轮全 FAILED。两种都不构成删除证据。
      return PresenceOutcome.refused("无有效上一轮可比对，本轮不判 GONE");
    }
    if (facts.nothingSeen()) {
      log.warn(
          "本轮未见任何实体，按 plan §3.4 拒绝把空集解释成全量消失 project={} dataSource={}",
          facts.projectId(),
          facts.sourceId());
      return PresenceOutcome.refused("本轮未见任何实体，空集不得解释为全量消失");
    }
    PresenceScan scan = upsertRepository.presenceScan(facts.projectId(), facts.sourceId(), SCAN_LIMIT);
    if (scan.truncated()) {
      // 分母读不全时比例只会偏小，熔断会因"证据不足"放过一次真坍塌——这正是要拦的那类。
      return PresenceOutcome.suspect(
          "在场集扫描触顶 " + SCAN_LIMIT + " 行，分母不可信", scan.rows().size());
    }
    Judgment judgment = judge(facts, scan.rows());
    if (judgment.collapsed(collapseRatio)) {
      String reason =
          String.format(
              Locale.ROOT,
              "单轮缺席表数 %d/%d 超过坍塌阈值 %.0f%%，本轮不落任何 GONE",
              judgment.absentTables,
              judgment.presentTables,
              collapseRatio * 100);
      log.error("采集熔断 project={} dataSource={}：{}", facts.projectId(), facts.sourceId(), reason);
      return PresenceOutcome.suspect(reason, scan.rows().size(), judgment);
    }
    if (judgment.gone.isEmpty()) {
      return PresenceOutcome.clean(scan.rows().size(), judgment);
    }
    if (facts.dryRun()) {
      log.info(
          "dry-run 预演：本轮将软删 {} 个实体（表 {} / 列 {} / 库 {}）",
          judgment.gone.size(),
          judgment.absentTables,
          judgment.absentColumns,
          judgment.absentDatabases);
      return PresenceOutcome.decided(judgment, scan.rows().size(), true);
    }
    int applied =
        upsertRepository.markGone(
            new GoneCommand(
                facts.projectId(),
                facts.collectRunId(),
                facts.operator(),
                facts.goneAt(),
                false,
                ProviderType.HARVESTED,
                judgment.gone));
    if (applied < judgment.gone.size()) {
      log.warn(
          "GONE 候选 {} 条、实际软删 {} 行 project={}（差额按已被并发轮次处理，不重试）",
          judgment.gone.size(),
          applied,
          facts.projectId());
    }
    revoke(facts, judgment.gone);
    return PresenceOutcome.decided(judgment, scan.rows().size(), false, applied);
  }

  /**
   * 纯判定：把在场集分成"消失"与"本轮无缺席资格"两堆。
   *
   * <p>三遍而不是单遍扫描，因为列的判定要用表的结果（祖先覆盖跳过），而库级判定要在表之前独立收窄。
   */
  private Judgment judge(PresenceFacts facts, List<CatalogPresenceRow> rows) {
    Set<String> readableScopes = facts.readableScopeKeys();
    Set<String> columnScopes = facts.columnCompleteScopeKeys();
    Judgment judgment = new Judgment();
    for (CatalogPresenceRow row : rows) {
      if (ASSET_DATABASE.equals(row.getAssetType())
          && facts.databasesEnumerated()
          && !HarvestSystemDatabases.excluded(
              MetadataHarvestService.normalizeIdentifier(row.getDatabaseName()))
          && absent(row, facts)) {
        judgment.goneDatabase(row);
      }
    }
    for (CatalogPresenceRow row : rows) {
      if (!ASSET_TABLE.equals(row.getAssetType())) {
        continue;
      }
      if (!claimable(row, readableScopes, facts)) {
        continue;
      }
      judgment.presentTables++;
      if (absent(row, facts)) {
        judgment.goneTable(row);
      }
    }
    if (!facts.collectColumns()) {
      // 本轮压根没读列，就没有"列消失"这件事可说——空手声明是 ticket 114 那条 PARTIAL 纪律的另一面。
      return judgment;
    }
    for (CatalogPresenceRow row : rows) {
      if (!ASSET_COLUMN.equals(row.getAssetType())) {
        continue;
      }
      if (!claimable(row, columnScopes, facts)
          || judgment.goneTableTriples.contains(tripleOf(row))) {
        continue;
      }
      if (absent(row, facts)) {
        judgment.goneColumn(row);
      }
    }
    return judgment;
  }

  /** 这行本轮有无"缺席资格"：所在作用域要被完整采过，且未被表名过滤器排除。 */
  private static boolean claimable(
      CatalogPresenceRow row, Set<String> scopes, PresenceFacts facts) {
    return scopes.contains(PresenceFacts.scopeKey(row.getDatabaseName(), row.getSchemaName()))
        && MetadataHarvestService.matchesPattern(row.getTableName(), facts.tablePattern());
  }

  /**
   * 缺席满两轮。
   *
   * <p>{@code last_collect_at} 每轮必刷（{@code touchLastCollected}），所以"早于上一轮开始"就是
   * "上一轮和本轮都没刷到过它"——不需要新列、不需要状态机，也不受重启影响。
   *
   * <p>时间戳为空的行<b>不判</b>：没有任何在场证据的行不该消失。
   */
  private static boolean absent(CatalogPresenceRow row, PresenceFacts facts) {
    return row.getLastCollectAt() != null
        && row.getLastCollectAt().isBefore(facts.previousRoundStartedAt());
  }

  private static String tripleOf(CatalogPresenceRow row) {
    return PresenceFacts.scopeKey(row.getDatabaseName(), row.getSchemaName())
        + "|"
        + MetadataHarvestService.normalizeIdentifier(row.getTableName());
  }

  /** 撤销血缘节点。能力未装配（ticket 113 未落地）时如实留日志，不静默、也不谎报已撤销。 */
  private void revoke(PresenceFacts facts, List<GoneRow> gone) {
    CatalogGraphRevocation revocation = revocations.getIfAvailable();
    List<String> keys = gone.stream().map(row -> row.row().getAssetKey()).toList();
    if (revocation == null) {
      log.warn(
          "血缘撤销能力未装配（ticket 113 未落地）：{} 个实体已软删但仍留在血缘图上 project={}",
          keys.size(),
          facts.projectId());
      return;
    }
    try {
      revocation.revoke(keys);
    } catch (RuntimeException failure) {
      // 撤销失败不回滚软删：实体确实不在源侧，图上的残留是可见性问题，误删才是数据问题。
      log.error("血缘撤销失败，软删结果保留 project={} keys={}", facts.projectId(), keys.size(), failure);
    }
  }

  /** 一轮的判定现场；熔断只看表那一份。 */
  private static final class Judgment {

    private final List<GoneRow> gone = new ArrayList<>();
    private final Set<String> goneTableTriples = new LinkedHashSet<>();
    private int presentTables;
    private int absentTables;
    private int absentColumns;
    private int absentDatabases;

    void goneTable(CatalogPresenceRow row) {
      gone.add(new GoneRow(row, MetadataHarvestService.TYPE_TABLE));
      goneTableTriples.add(tripleOf(row));
      absentTables++;
    }

    void goneColumn(CatalogPresenceRow row) {
      gone.add(new GoneRow(row, MetadataHarvestService.TYPE_COLUMN));
      absentColumns++;
    }

    void goneDatabase(CatalogPresenceRow row) {
      gone.add(new GoneRow(row, MetadataHarvestService.TYPE_DATABASE));
      absentDatabases++;
    }

    int goneCount() {
      return gone.size();
    }

    List<String> goneKeys() {
      return gone.stream().map(row -> row.row().getAssetKey()).toList();
    }

    boolean collapsed(double ratio) {
      return absentTables >= MIN_COLLAPSED_TABLES
          && presentTables > 0
          && (double) absentTables / presentTables > ratio;
    }
  }

  /**
   * 一轮的在场性结论，由 {@code MetadataHarvestService} 落进 {@code yak_md_collect_run}。
   *
   * @param suspect 熔断路径：本轮不落 GONE，但整轮标 {@code SUSPECT} 让运维看见
   * @param reason "为什么没删"或"为什么可疑"；{@code null} = 判定正常执行
   * @param goneCount <b>实际</b>软删行数（dry-run 恒 0，候选数看下面三个分类计数）
   */
  public record PresenceOutcome(
      boolean suspect,
      String reason,
      int goneCount,
      int goneTables,
      int goneColumns,
      int goneDatabases,
      int scanned,
      int presentTables,
      List<String> goneKeys) {

    static PresenceOutcome refused(String reason) {
      return new PresenceOutcome(false, reason, 0, 0, 0, 0, 0, 0, List.of());
    }

    static PresenceOutcome suspect(String reason, int scanned) {
      return new PresenceOutcome(true, reason, 0, 0, 0, 0, scanned, 0, List.of());
    }

    static PresenceOutcome suspect(String reason, int scanned, Judgment judgment) {
      return new PresenceOutcome(
          true, reason, 0, judgment.absentTables, judgment.absentColumns, judgment.absentDatabases,
          scanned, judgment.presentTables, List.of());
    }

    static PresenceOutcome clean(int scanned, Judgment judgment) {
      return new PresenceOutcome(
          false, null, 0, 0, 0, 0, scanned, judgment.presentTables, List.of());
    }

    static PresenceOutcome decided(Judgment judgment, int scanned, boolean dryRun) {
      return decided(judgment, scanned, dryRun, 0);
    }

    static PresenceOutcome decided(Judgment judgment, int scanned, boolean dryRun, int applied) {
      return new PresenceOutcome(
          false,
          dryRun ? "dry-run：仅预演，未落软删" : null,
          applied,
          judgment.absentTables,
          judgment.absentColumns,
          judgment.absentDatabases,
          scanned,
          judgment.presentTables,
          judgment.goneKeys());
    }

    /** {@code RunStatus}：熔断 → SUSPECT，其余沿用调用方的成败。 */
    public RunStatus statusOn(RunStatus otherwise) {
      return suspect ? RunStatus.SUSPECT : otherwise;
    }
  }
}
