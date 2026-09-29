package io.yak.ops.business.metadata.harvest;

import io.yak.ops.business.metadata.dao.mapper.LineageCatalogRowMapper;
import io.yak.ops.business.metadata.dao.mapper.MdChangeMapper;
import io.yak.ops.business.metadata.dao.model.CatalogAssetRow;
import io.yak.ops.business.metadata.dao.model.CatalogAssetState;
import io.yak.ops.business.metadata.dao.model.CatalogPresenceRow;
import io.yak.ops.common.bean.po.metadata.MdChangePO;
import io.yak.ops.common.enums.metadata.MetadataEnums.ChangeType;
import io.yak.ops.common.enums.metadata.MetadataEnums.ProviderType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 两条入口<b>共用</b>的唯一 upsert（plan §3.3）。物理采集与投影对账都从这一个口子写目录行，
 * "判增量"因此只有一份实现，不会两通道各写一条 SQL 而在指纹口径上漂移。
 *
 * <p>一次调用做四件事，顺序不能换：
 * <ol>
 *   <li><b>写前预读</b>已存在行的 hash。少了这一步就分不出 NEW 与 UNCHANGED——
 *       Connector/J 默认 {@code useAffectedRows=false}，受影响行数在这里不可信。</li>
 *   <li><b>按 provider_type 分岔</b>比对：{@code HARVESTED} 只比 {@code content_hash}，
 *       {@code REGISTERED} 只比 {@code source_hash}。拿反了会让投影抖动伪装成表结构变更。</li>
 *   <li><b>分批 ≤500</b> 行一条语句（plan §0.11）。</li>
 *   <li>{@code last_collect_at} <b>单独刷</b>：每轮都要留在场痕迹，但它不是"变更"（plan §3.3 末）。</li>
 * </ol>
 *
 * <p>dry-run 只做 ①②，一条写语句都不发——这是"任务未 dry-run 通过不得启用"的实现处（plan §0.13）。
 *
 * <p>一次调用装<b>同一类实体</b>的一批行：变更流水的 {@code type_name} 因此来自命令而不是行，
 * 目录行模型上也就不必挂一个共表里不存在的列。
 *
 * <p>{@link #markGone} 是本类除 {@link #write} 外<b>唯一</b>的写入口，且只做软删（置 {@code gone_at}）。
 * 它留在这里而不是另开一个仓储，是因为共表的列归属边界只有一份才守得住：会写这张表的地方越少，
 * "谁覆写了谁的列"才越有可能被一次 grep 找全。
 */
@Slf4j
@Component
public class AssetUpsertRepository {

  /** 单条语句的最大行数。 */
  static final int WRITE_BATCH_SIZE = 500;

  private final LineageCatalogRowMapper catalogMapper;
  private final MdChangeMapper changeMapper;

  public AssetUpsertRepository(
      LineageCatalogRowMapper catalogMapper, MdChangeMapper changeMapper) {
    this.catalogMapper = catalogMapper;
    this.changeMapper = changeMapper;
  }

  /** 写入一批目录行，产出 NEW/CHANGED/UNCHANGED 计数与已存在行的复活情况。 */
  @Transactional(
      transactionManager = "yakBusinessTransactionManager",
      rollbackFor = Exception.class)
  public HarvestBatchResult write(BatchCommand command) {
    List<CatalogAssetRow> rows = command.rows();
    if (rows.isEmpty()) {
      return HarvestBatchResult.empty();
    }
    Map<String, CatalogAssetState> before = preReadStates(command, rows);
    List<String> newKeys = new ArrayList<>();
    List<String> changedKeys = new ArrayList<>();
    Set<String> revivedKeys = new LinkedHashSet<>();
    int unchanged = 0;
    for (CatalogAssetRow row : rows) {
      CatalogAssetState state = before.get(row.getAssetKey());
      if (state == null) {
        newKeys.add(row.getAssetKey());
        continue;
      }
      if (isChanged(command.providerType(), state, row)) {
        changedKeys.add(row.getAssetKey());
      } else {
        unchanged++;
      }
      if (state.getGoneAt() != null) {
        revivedKeys.add(row.getAssetKey());
      }
    }
    if (command.dryRun()) {
      return new HarvestBatchResult(
          newKeys.size(), changedKeys.size(), unchanged, revivedKeys.size(), Map.of());
    }

    for (List<CatalogAssetRow> chunk : partition(rows, WRITE_BATCH_SIZE)) {
      catalogMapper.upsertAssets(chunk);
    }
    // 变更流水要 asset_id，而新行的 id 写完才存在，所以这里再读一次"只读变更集"：
    // 幂等重跑时 newKeys/changedKeys 皆空，这一趟与后面的插入都不发生（"除 last_collect_at 外零写入"）。
    Map<String, Long> ids = new LinkedHashMap<>();
    List<String> reported = new ArrayList<>(newKeys);
    reported.addAll(changedKeys);
    for (List<String> chunk : partition(reported, WRITE_BATCH_SIZE)) {
      catalogMapper.selectStates(command.projectId(), chunk)
          .forEach(state -> ids.put(state.getAssetKey(), state.getId()));
    }
    writeChangeRows(command, newKeys, changedKeys, revivedKeys, before, ids);

    List<String> keys = rows.stream().map(CatalogAssetRow::getAssetKey).toList();
    for (List<String> chunk : partition(keys, WRITE_BATCH_SIZE)) {
      catalogMapper.touchLastCollected(command.projectId(), chunk, command.collectedAt());
    }
    return new HarvestBatchResult(
        newKeys.size(), changedKeys.size(), unchanged, revivedKeys.size(), Map.copyOf(ids));
  }

  /**
   * 按 {@code asset_key} 回读目录行 id，供上层挂子实体层级。
   *
   * <p>{@link #write} 只交回<b>本轮变更过</b>的行的 id（幂等重跑要零额外语句），而父级 id 对
   * UNCHANGED 的表同样必要，所以这里单独有一条只读入口——写路径仍然只有 {@link #write} 一个。
   *
   * <p>读不回来的键（含 {@code gone_at} 有值的软删行仍会回来）就是不存在，直接缺席，不补零值。
   */
  public Map<String, Long> idsOf(Long projectId, Collection<String> assetKeys) {
    Map<String, Long> ids = new LinkedHashMap<>();
    for (List<String> chunk : partition(new ArrayList<>(assetKeys), WRITE_BATCH_SIZE)) {
      for (CatalogAssetState state : catalogMapper.selectStates(projectId, chunk)) {
        ids.putIfAbsent(state.getAssetKey(), state.getId());
      }
    }
    return ids;
  }

  /**
   * 按 {@code asset_key} 回读已存在行的完整状态（含两把指纹与 {@code sourceUpdatedAt}）。
   *
   * <p>登记通道"保序"要先看行上的源侧时间戳再决定走 upsert 还是只刷在场（ticket 130），
   * 那是读不是写，走这里——写路径仍然只有 {@link #write} 一个。
   */
  public Map<String, CatalogAssetState> statesOf(Long projectId, Collection<String> assetKeys) {
    Map<String, CatalogAssetState> states = new LinkedHashMap<>();
    for (List<String> chunk : partition(new ArrayList<>(assetKeys), WRITE_BATCH_SIZE)) {
      for (CatalogAssetState state : catalogMapper.selectStates(projectId, chunk)) {
        states.putIfAbsent(state.getAssetKey(), state);
      }
    }
    return states;
  }

  /**
   * 只刷在场时间，不动任何内容列。
   *
   * <p>给登记通道的过期命令用：晚到的旧快照不能覆盖新内容（保序），但"这个实体此刻仍在源侧"
   * 是当下发生的事实，与快照新旧无关，必须留下。复用采集那条 {@code touchLastCollected}：
   * 两句 SQL 各自成立的话一样，没理由各写一份。
   */
  public void touchPresence(Long projectId, Collection<String> assetKeys, LocalDateTime at) {
    for (List<String> chunk : partition(new ArrayList<>(assetKeys), WRITE_BATCH_SIZE)) {
      catalogMapper.touchLastCollected(projectId, chunk, at);
    }
  }

  /**
   * 读回本数据源当前的在场集（ticket 115 的分母与候选池）。
   *
   * <p>{@code truncated} 不是"少了几行无所谓"：坍塌熔断的分母是整个在场集，读不全时比例算出来偏小，
   * <b>熔断会因为证据不足而放过一次真坍塌</b>。所以这里把触顶如实交回上层，由它判 SUSPECT。
   */
  public PresenceScan presenceScan(Long projectId, String sourceId, int limit) {
    List<CatalogPresenceRow> rows = catalogMapper.selectPresenceRows(projectId, sourceId, limit);
    List<CatalogPresenceRow> present = rows == null ? List.of() : rows;
    return new PresenceScan(present, present.size() >= limit);
  }

  /**
   * 读回登记通道在某源某类型下当前的在场行（ticket 130 unregister 的候选池）。
   *
   * <p>触顶语义与 {@link #presenceScan} 一致：行数由源侧决定，候选池读不全时 unregister 会漏软删，
   * 上层必须把"没扫完"当作结果的一部分而不是假装删干净了。
   */
  public PresenceScan registeredScan(Long projectId, Long typeId, String sourceId, int limit) {
    List<CatalogPresenceRow> rows =
        catalogMapper.selectRegisteredRows(projectId, typeId, sourceId, limit);
    List<CatalogPresenceRow> present = rows == null ? List.of() : rows;
    return new PresenceScan(present, present.size() >= limit);
  }

  /**
   * 软删一批实体，并为每个实体留一条 GONE 流水。
   *
   * <p>dry-run 一条语句都不发（与 {@link #write} 同口径）：预演要能看见"若真跑会删谁"，
   * 而代价不能是先把实体删了。
   *
   * <p>流水按<b>候选集</b>写，不按受影响行数写：行数短少只可能是并发轮次已先行确认了同一件事
   * （实体确实不在场），证据不该因此缺失；差额本身用日志留下。
   */
  @Transactional(
      transactionManager = "yakBusinessTransactionManager",
      rollbackFor = Exception.class)
  public int markGone(GoneCommand command) {
    if (command.rows().isEmpty() || command.dryRun()) {
      return 0;
    }
    List<GoneRow> rows = command.rows();
    int applied = 0;
    for (List<GoneRow> chunk : partition(rows, WRITE_BATCH_SIZE)) {
      applied +=
          catalogMapper.markGone(chunk.stream().map(row -> row.row().getId()).toList(), command.goneAt());
    }
    if (applied != rows.size()) {
      log.warn(
          "GONE 软删行数与候选数不符 project={} 期望={} 实际={}（并发轮次可能已先行处理）",
          command.projectId(),
          rows.size(),
          applied);
    }
    for (GoneRow row : rows) {
      insertGoneChange(command, row);
    }
    return applied;
  }

  private void insertGoneChange(GoneCommand command, GoneRow row) {
    MdChangePO change = new MdChangePO();
    change.setProjectId(command.projectId());
    change.setAssetId(row.row().getId());
    change.setAssetKey(row.row().getAssetKey());
    change.setTypeName(row.typeName());
    change.setProviderType(command.providerType().name());
    change.setChangeType(ChangeType.GONE.name());
    // before 是消失前的指纹：GONE 之后源侧再也读不到，这是最后一份可比对的现场。
    // 取哪一把与写入侧同一分岔（§3.3）——登记行的 content_hash 恒为 NULL，只有源指纹可读。
    change.setBeforeValue(
        command.providerType() == ProviderType.REGISTERED
            ? row.row().getSourceHash()
            : row.row().getContentHash());
    change.setCollectRunId(command.collectRunId());
    change.setChangedBy(command.operator());
    change.setChangedAt(command.goneAt());
    changeMapper.insert(change);
  }

  private Map<String, CatalogAssetState> preReadStates(
      BatchCommand command, List<CatalogAssetRow> rows) {
    List<String> keys = rows.stream().map(CatalogAssetRow::getAssetKey).toList();
    Map<String, CatalogAssetState> states = new LinkedHashMap<>();
    for (List<String> chunk : partition(keys, WRITE_BATCH_SIZE)) {
      for (CatalogAssetState state : catalogMapper.selectStates(command.projectId(), chunk)) {
        CatalogAssetState previous = states.get(state.getAssetKey());
        // 同一 asset_key 读回两行 = 行的归属被别的链路改过（plan §2.3 后果 5）。
        // 采集侧不修、也不静默：修它的是 ticket 119 的看门狗，这里只把现场留下。
        if (previous != null && !Objects.equals(previous.getId(), state.getId())) {
          log.warn(
              "目录内同一 asset_key 存在多行在场记录 project={} key={} ids=[{}, {}]",
              command.projectId(),
              state.getAssetKey(),
              previous.getId(),
              state.getId());
        }
        states.put(state.getAssetKey(), state);
      }
    }
    return states;
  }

  /** 指纹分岔（plan §3.3）：采集通道只看物理结构，登记通道只看投影内容。 */
  static boolean isChanged(
      ProviderType providerType, CatalogAssetState existing, CatalogAssetRow row) {
    return providerType == ProviderType.REGISTERED
        ? !Objects.equals(existing.getSourceHash(), row.getSourceHash())
        : !Objects.equals(existing.getContentHash(), row.getContentHash());
  }

  private void writeChangeRows(
      BatchCommand command,
      List<String> newKeys,
      List<String> changedKeys,
      Set<String> revivedKeys,
      Map<String, CatalogAssetState> before,
      Map<String, Long> ids) {
    Map<String, CatalogAssetRow> byKey = new LinkedHashMap<>();
    command.rows().forEach(row -> byKey.put(row.getAssetKey(), row));
    newKeys.forEach(key -> insertChange(command, byKey.get(key), ids.get(key), ChangeType.NEW, null, null));
    for (String key : changedKeys) {
      CatalogAssetState state = before.get(key);
      CatalogAssetRow row = byKey.get(key);
      // 曾 GONE 又回来，先记"复活"这一件事：结构差异同样落在 after/before 两列里。
      ChangeType type = revivedKeys.contains(key) ? ChangeType.REVIVED : ChangeType.CHANGED;
      insertChange(
          command, row, ids.get(key), type, fingerprint(command, state), fingerprint(command, row));
    }
  }

  private void insertChange(
      BatchCommand command,
      CatalogAssetRow row,
      Long assetId,
      ChangeType changeType,
      String beforeValue,
      String afterValue) {
    if (row == null) {
      return;
    }
    MdChangePO change = new MdChangePO();
    change.setProjectId(command.projectId());
    change.setAssetId(assetId);
    change.setAssetKey(row.getAssetKey());
    change.setTypeName(command.typeName());
    change.setProviderType(command.providerType().name());
    change.setChangeType(changeType.name());
    change.setBeforeValue(beforeValue);
    change.setAfterValue(afterValue);
    change.setSourceUpdatedAt(row.getSourceUpdatedAt());
    change.setCollectRunId(command.collectRunId());
    change.setChangedBy(command.operator());
    change.setChangedAt(command.collectedAt());
    changeMapper.insert(change);
  }

  private static String fingerprint(BatchCommand command, CatalogAssetState state) {
    return command.providerType() == ProviderType.REGISTERED
        ? state.getSourceHash()
        : state.getContentHash();
  }

  private static String fingerprint(BatchCommand command, CatalogAssetRow row) {
    return command.providerType() == ProviderType.REGISTERED ? row.getSourceHash() : row.getContentHash();
  }

  private static <T> List<List<T>> partition(List<T> values, int size) {
    List<List<T>> parts = new ArrayList<>();
    for (int index = 0; index < values.size(); index += size) {
      parts.add(values.subList(index, Math.min(values.size(), index + size)));
    }
    return parts;
  }

  /** 一次批量写入的全部外部输入；一批 = 一类实体。 */
  public record BatchCommand(
      Long projectId,
      String typeName,
      ProviderType providerType,
      Long collectRunId,
      String operator,
      LocalDateTime collectedAt,
      boolean dryRun,
      List<CatalogAssetRow> rows) {

    public BatchCommand {
      rows = rows == null ? List.of() : List.copyOf(rows);
    }
  }

  /**
   * @param assetIds 本批变更行的 id；上层挂子实体层级要用
   */
  public record HarvestBatchResult(
      int newCount,
      int changedCount,
      int unchangedCount,
      int revivedCount,
      Map<String, Long> assetIds) {

    public static HarvestBatchResult empty() {
      return new HarvestBatchResult(0, 0, 0, 0, Map.of());
    }

    public HarvestBatchResult plus(HarvestBatchResult other) {
      return new HarvestBatchResult(
          newCount + other.newCount,
          changedCount + other.changedCount,
          unchangedCount + other.unchangedCount,
          revivedCount + other.revivedCount,
          mergedIds(assetIds, other.assetIds));
    }

    private static Map<String, Long> mergedIds(Map<String, Long> left, Map<String, Long> right) {
      if (left.isEmpty()) {
        return right;
      }
      if (right.isEmpty()) {
        return left;
      }
      Map<String, Long> merged = new LinkedHashMap<>(left);
      merged.putAll(right);
      return Map.copyOf(merged);
    }
  }

  /**
   * 在场集扫描结果。
   *
   * @param truncated 读到的行数已达上限——此时分母不可信，上层据此拒绝判 GONE
   */
  public record PresenceScan(List<CatalogPresenceRow> rows, boolean truncated) {
    public PresenceScan {
      rows = rows == null ? List.of() : List.copyOf(rows);
    }
  }

  /**
   * 一条 GONE 候选。
   *
   * <p>{@code typeName} 不在共表里（{@code asset_type} 是 lineage 的图类型，不是目录的元模型类型），
   * 而变更流水必须记"哪一类实体消失了"，所以由判定方随候选一起交出。
   */
  public record GoneRow(CatalogPresenceRow row, String typeName) {}

  /** 一批软删的全部外部输入。 */
  public record GoneCommand(
      Long projectId,
      Long collectRunId,
      String operator,
      LocalDateTime goneAt,
      boolean dryRun,
      ProviderType providerType,
      List<GoneRow> rows) {

    public GoneCommand {
      rows = rows == null ? List.of() : List.copyOf(rows);
    }
  }
}
