package io.yak.ops.business.asset.reconcile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.yak.framework.common.PageData;
import io.yak.ops.business.asset.api.AssetCursorQuery;
import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.api.AssetPage;
import io.yak.ops.business.asset.api.AssetProvider;
import io.yak.ops.business.asset.application.AssetSettingService;
import io.yak.ops.business.asset.catalog.AssignRuleService;
import io.yak.ops.business.asset.dao.mapper.AssetChangeRecordMapper;
import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.business.asset.dao.mapper.AssetTagRelMapper;
import io.yak.ops.business.asset.exception.AssetException;
import io.yak.ops.business.asset.health.HealthRecomputeService;
import io.yak.ops.business.asset.schedule.AssetScheduleEngineBridge;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.common.bean.po.asset.AssetChangeRecordPO;
import io.yak.ops.common.bean.po.asset.AssetItemPO;
import io.yak.ops.common.bean.po.asset.AssetTagRelPO;
import io.yak.ops.common.enums.asset.AssetEnums.ChangeType;
import io.yak.ops.common.enums.asset.AssetEnums.HandleStatus;
import io.yak.ops.common.enums.asset.AssetErrorCode;
import io.yak.ops.common.enums.asset.AssetStatus;
import io.yak.ops.common.enums.asset.AssetSourceType;
import io.yak.ops.core.project.CurrentProject;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 对账引擎(ticket 95):全 provider 游标批量 upsert 台账,变更落流水。
 * 全局互斥(48012)、单 provider 失败可见不影响其余(分区容错)、MANUAL 不参与对账。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetReconcileService {

  /** SOURCE_GONE 判定窗口默认天数(yak_asset_setting 可覆盖)。 */
  public static final int DEFAULT_GONE_WINDOW_DAYS = 7;
  public static final String SETTING_GONE_WINDOW_DAYS = "source_gone_window_days";
  public static final String SETTING_LAST_PREFIX = "reconcile_last_";

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private final CurrentProject currentProject;
  private final AssetProviderRegistry registry;
  private final AssetItemMapper itemMapper;
  private final AssetChangeRecordMapper changeMapper;
  private final AssetTagRelMapper tagRelMapper;
  private final AssignRuleService assignRuleService;
  private final AssetSettingService settingService;
  private final BusinessAuditService auditService;
  private final AssetScheduleEngineBridge scheduleBridge;
  private final HealthRecomputeService healthRecompute;

  private final AtomicBoolean running = new AtomicBoolean();
  private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
    Thread thread = new Thread(runnable, "asset-reconcile");
    thread.setDaemon(true);
    return thread;
  });

  /** 单 provider 一轮对账的结果;error 非空即失败(失败可见,不吞)。 */
  public record ProviderOutcome(
      AssetSourceType sourceType, int scanned, int created, int changed, int restored,
      LocalDateTime at, String error) {}

  /** 变更流水视图(盘点"变更确认"段用)。 */
  public record ChangeView(
      Long id, Long assetId, String assetName, String changeType, Map<String, Object> diff,
      String handleStatus, String createdBy, LocalDateTime createTime) {}

  /** 手动触发:校验在请求线程内完成(48011/48012/48015),执行异步受理。 */
  public String submit(List<String> requestedSourceTypes, String operator) {
    Long projectId = currentProject.requireProjectId();
    List<AssetSourceType> types = resolveRequested(requestedSourceTypes);
    scheduleBridge.ensureProjectAlarms(projectId);
    if (!running.compareAndSet(false, true)) {
      throw new AssetException(AssetErrorCode.RECONCILE_RUNNING);
    }
    executor.submit(() -> {
      try {
        reconcileNow(projectId, types, operator, false);
      } catch (RuntimeException e) {
        log.error("Async asset reconcile failed for project {}", projectId, e);
      } finally {
        running.set(false);
      }
    });
    return "对账已受理,后台执行中";
  }

  /** 同步核心:调度与测试入口;acquireLock=true 时自行抢互斥。 */
  public List<ProviderOutcome> reconcileNow(Long projectId, List<AssetSourceType> types,
      String operator, boolean acquireLock) {
    if (acquireLock && !running.compareAndSet(false, true)) {
      throw new AssetException(AssetErrorCode.RECONCILE_RUNNING);
    }
    try {
      LocalDateTime sweepStart = LocalDateTime.now();
      List<ProviderOutcome> outcomes = new ArrayList<>();
      for (AssetSourceType type : types) {
        outcomes.add(runProvider(projectId, type, sweepStart, operator));
      }
      List<AssetSourceType> succeeded = outcomes.stream()
          .filter(o -> o.error() == null).map(ProviderOutcome::sourceType).toList();
      int gone = markGoneAssets(projectId, succeeded, sweepStart);
      settingService.put(projectId, "reconcile_last_gone_count", String.valueOf(gone));
      for (ProviderOutcome outcome : outcomes) {
        settingService.put(projectId, SETTING_LAST_PREFIX + outcome.sourceType().name(),
            writeJson(outcome));
      }
      audit(projectId, outcomes, gone);
      return outcomes;
    } finally {
      if (acquireLock) {
        running.set(false);
      }
    }
  }

  /** 调度入口:全部已注册来源,同步执行,自抢互斥;无 provider 时抛 48011。 */
  public List<ProviderOutcome> scheduledReconcile(Long projectId) {
    return reconcileNow(projectId, resolveRequested(null), "scheduler", true);
  }

  /** 各 provider 最近对账状态(含未注册的类型,前端"源接入"红点用)。 */
  public List<Map<String, Object>> status() {
    Long projectId = currentProject.requireProjectId();
    List<Map<String, Object>> views = new ArrayList<>();
    for (AssetSourceType type : AssetSourceType.values()) {
      if (type == AssetSourceType.MANUAL) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("sourceType", type.name());
      row.put("registered", registry.find(type).isPresent());
      row.put("lastRun", readJsonMap(
          settingService.get(projectId, SETTING_LAST_PREFIX + type.name())));
      views.add(row);
    }
    return views;
  }

  // ---------- 变更流水查询(确认/忽略在 ticket 96) ----------

  /** 盘点"变更确认"段:按处理状态分页,倒序。 */
  public PageData<ChangeView> pageChanges(String handleStatus, int pageNo, int pageSize) {
    Long projectId = currentProject.requireProjectId();
    Page<AssetChangeRecordPO> result = changeMapper.selectPage(new Page<>(pageNo, pageSize),
        new LambdaQueryWrapper<AssetChangeRecordPO>()
            .eq(AssetChangeRecordPO::getProjectId, projectId)
            .eq(AssetChangeRecordPO::getDeleted, false)
            .eq(StringUtils.hasText(handleStatus), AssetChangeRecordPO::getHandleStatus,
                handleStatus)
            .orderByDesc(AssetChangeRecordPO::getId));
    List<Long> assetIds = result.getRecords().stream()
        .map(AssetChangeRecordPO::getAssetId).filter(Objects::nonNull).distinct().toList();
    Map<Long, String> names = assetIds.isEmpty() ? Map.of()
        : itemMapper.selectBatchIds(assetIds).stream().collect(
            Collectors.toMap(AssetItemPO::getId, AssetItemPO::getName, (a, b) -> a));
    List<ChangeView> views = result.getRecords().stream()
        .map(po -> new ChangeView(po.getId(), po.getAssetId(),
            names.get(po.getAssetId()), po.getChangeType(), readJsonMap(po.getDiff()),
            po.getHandleStatus(), po.getCreatedBy(), po.getCreateTime()))
        .toList();
    return new PageData<>(views, result.getTotal(), result.getPages(),
        (int) result.getCurrent(), (int) result.getSize());
  }

  // ---------- 变更确认 / 忽略(ticket 96) ----------

  /** 确认变更:用 snapshot_new 覆盖台账展示字段(D1),记 CONFIRMED;非 OPEN 报 48014。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public ChangeView confirmChange(Long changeId, String operator) {
    Long projectId = currentProject.requireProjectId();
    AssetChangeRecordPO change = requireOpenChange(projectId, changeId);
    AssetItemPO item = itemMapper.selectOne(new LambdaQueryWrapper<AssetItemPO>()
        .eq(AssetItemPO::getProjectId, projectId)
        .eq(AssetItemPO::getId, change.getAssetId())
        .eq(AssetItemPO::getDeleted, false)
        .last("LIMIT 1"));
    if (item == null) {
      throw new AssetException(AssetErrorCode.ASSET_NOT_FOUND, "关联资产 id=" + change.getAssetId());
    }
    Map<String, Object> snapshot = readJsonMap(change.getSnapshotNew());
    if (!snapshot.isEmpty()) {
      applyIfPresent(item, snapshot, "name");
      applyIfPresent(item, snapshot, "description");
      applyIfPresent(item, snapshot, "layerCode");
      applyIfPresent(item, snapshot, "domainCode");
    } else if (change.getDiff() != null) {
      // 无快照的旧记录退化:按 diff 的新值侧覆盖
      Map<String, Object> diff = readJsonMap(change.getDiff());
      diff.forEach((field, value) -> {
        if (List.of("name", "description", "layerCode", "domainCode").contains(field)
            && value instanceof List<?> pair && pair.size() == 2) {
          String next = String.valueOf(pair.get(1));
          if (!next.isEmpty()) {
            applySnapshotValue(item, field, next);
          }
        }
      });
    }
    item.setUpdatedBy(operator);
    item.setUpdateTime(LocalDateTime.now());
    itemMapper.updateById(item);
    markHandled(change, HandleStatus.CONFIRMED, operator);
    auditChange("ASSET_CHANGE_CONFIRM", "Confirm asset change", changeId, item,
        AuditEventType.RESOURCE_UPDATED, "确认资产 " + item.getName() + " 的变更");
    healthRecompute.recomputeItems(projectId, List.of(item.getId()));
    return new ChangeView(change.getId(), item.getId(), item.getName(), change.getChangeType(),
        readJsonMap(change.getDiff()), change.getHandleStatus(), change.getCreatedBy(),
        change.getCreateTime());
  }

  /** 忽略变更:不回写台账,仅关闭流水;非 OPEN 报 48014。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void ignoreChange(Long changeId, String operator) {
    Long projectId = currentProject.requireProjectId();
    AssetChangeRecordPO change = requireOpenChange(projectId, changeId);
    markHandled(change, HandleStatus.IGNORED, operator);
    auditChange("ASSET_CHANGE_IGNORE", "Ignore asset change", changeId, null,
        AuditEventType.RESOURCE_UPDATED, "忽略变更记录 " + changeId);
  }

  private AssetChangeRecordPO requireOpenChange(Long projectId, Long changeId) {
    AssetChangeRecordPO change = changeMapper.selectOne(new LambdaQueryWrapper<AssetChangeRecordPO>()
        .eq(AssetChangeRecordPO::getProjectId, projectId)
        .eq(AssetChangeRecordPO::getId, changeId)
        .eq(AssetChangeRecordPO::getDeleted, false)
        .last("LIMIT 1"));
    if (change == null) {
      throw new AssetException(AssetErrorCode.ASSET_NOT_FOUND, "变更记录 id=" + changeId);
    }
    if (!HandleStatus.OPEN.name().equals(change.getHandleStatus())) {
      throw new AssetException(AssetErrorCode.CHANGE_ALREADY_HANDLED);
    }
    return change;
  }

  private void markHandled(AssetChangeRecordPO change, HandleStatus status, String operator) {
    change.setHandleStatus(status.name());
    change.setHandledBy(operator);
    change.setHandledAt(LocalDateTime.now());
    change.setUpdateTime(change.getHandledAt());
    changeMapper.updateById(change);
  }

  private static void applyIfPresent(AssetItemPO item, Map<String, Object> snapshot, String field) {
    Object value = snapshot.get(field);
    if (value != null) {
      applySnapshotValue(item, field, String.valueOf(value));
    }
  }

  private static void applySnapshotValue(AssetItemPO item, String field, String value) {
    switch (field) {
      case "name" -> item.setName(value);
      case "description" -> item.setDescription(value);
      case "layerCode" -> item.setLayerCode(value);
      case "domainCode" -> item.setDomainCode(value);
      default -> { }
    }
  }

  private void auditChange(String code, String action, Long changeId, AssetItemPO item,
      AuditEventType type, String message) {
    AuditOperationHandle handle = auditService.start(new AuditOperationRequest(
        code, action, "ASSET_CHANGE", String.valueOf(changeId),
        item == null ? String.valueOf(changeId) : item.getAssetKey(), "APPLICATION", Map.of()));
    AuditTransactions.completeOnCommit(handle, type, message,
        Map.of("changeId", String.valueOf(changeId)), null);
  }

  // ---------- provider sweep ----------

  private ProviderOutcome runProvider(Long projectId, AssetSourceType type,
      LocalDateTime sweepStart, String operator) {
    Optional<AssetProvider> provider = registry.find(type);
    if (provider.isEmpty()) {
      return new ProviderOutcome(type, 0, 0, 0, 0, sweepStart,
          "源域未接入(缺少 AssetProvider: " + type + ")");
    }
    int scanned = 0;
    int created = 0;
    int changed = 0;
    int restored = 0;
    try {
      String cursor = null;
      do {
        AssetPage page = provider.get().cursorList(
            new AssetCursorQuery(projectId, null, cursor, AssetCursorQuery.MAX_LIMIT));
        for (AssetDescriptor descriptor : page.items()) {
          UpsertResult result = upsert(projectId, type, descriptor, sweepStart, operator);
          scanned++;
          created += result.created;
          changed += result.changed;
          restored += result.restored;
        }
        cursor = page.hasMore() ? page.nextCursor() : null;
      } while (cursor != null);
      return new ProviderOutcome(type, scanned, created, changed, restored, sweepStart, null);
    } catch (RuntimeException e) {
      log.warn("Asset reconcile provider {} failed (isolated): {}", type, e.toString());
      return new ProviderOutcome(type, scanned, created, changed, restored,
          sweepStart, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
  }

  record UpsertResult(int created, int changed, int restored) {
    static final UpsertResult NEW = new UpsertResult(1, 0, 0);
    static final UpsertResult META = new UpsertResult(0, 1, 0);
    static final UpsertResult RESTORED = new UpsertResult(0, 0, 1);
    static final UpsertResult UNCHANGED = new UpsertResult(0, 0, 0);
  }

  /** upsert 三分支(design 6.1);MANUAL 键域冲突直接跳过(不覆盖手工行)。 */
  UpsertResult upsert(Long projectId, AssetSourceType type, AssetDescriptor descriptor,
      LocalDateTime sweepStart, String operator) {
    AssetItemPO existing = itemMapper.selectOne(new LambdaQueryWrapper<AssetItemPO>()
        .eq(AssetItemPO::getProjectId, projectId)
        .eq(AssetItemPO::getAssetKey, descriptor.assetKey())
        .eq(AssetItemPO::getDeleted, false)
        .last("LIMIT 1"));
    if (existing == null) {
      return createPending(projectId, type, descriptor, sweepStart, operator);
    }
    boolean hashChanged = !Objects.equals(existing.getContentHash(), descriptor.contentHash());
    existing.setReconciledAt(sweepStart);
    if (AssetStatus.IGNORED.name().equals(existing.getStatus())) {
      if (hashChanged) {
        existing.setStatus(AssetStatus.PENDING.name());
        touchFromDescriptor(existing, descriptor);
        record(existing, ChangeType.NEW, null, descriptor, operator);
        itemMapper.updateById(existing);
        return UpsertResult.META;
      }
      itemMapper.updateById(existing);
      return UpsertResult.UNCHANGED;
    }
    if (AssetStatus.SOURCE_GONE.name().equals(existing.getStatus())) {
      existing.setStatus(previousStatusOf(existing.getId()));
      touchFromDescriptor(existing, descriptor);
      record(existing, ChangeType.REAPPEARED, null, descriptor, operator);
      itemMapper.updateById(existing);
      return UpsertResult.RESTORED;
    }
    if (hashChanged) {
      Map<String, Object> diff = displayDiff(existing, descriptor);
      touchFromDescriptor(existing, descriptor);
      record(existing, ChangeType.META_CHANGED, diff, descriptor, operator);
      itemMapper.updateById(existing);
      return UpsertResult.META;
    }
    itemMapper.updateById(existing);
    return UpsertResult.UNCHANGED;
  }

  private UpsertResult createPending(Long projectId, AssetSourceType type,
      AssetDescriptor descriptor, LocalDateTime sweepStart, String operator) {
    AssetItemPO po = new AssetItemPO();
    po.setProjectId(projectId);
    po.setAssetKey(descriptor.assetKey());
    po.setSourceType(type.name());
    po.setSourceId(descriptor.sourceId());
    po.setAssetType(descriptor.assetType() == null ? null : descriptor.assetType().name());
    po.setName(descriptor.name());
    po.setDescription(descriptor.description());
    po.setLayerCode(descriptor.layerCode());
    po.setDomainCode(descriptor.domainCode());
    po.setOwner(descriptor.suggestedOwner());
    po.setStatus(AssetStatus.PENDING.name());
    po.setContentHash(descriptor.contentHash());
    po.setSourceUpdatedAt(descriptor.updatedAt());
    po.setReconciledAt(sweepStart);
    po.setViewCount30d(0);
    po.setDirectoryId(assignRuleService.suggestedDirectoryId(projectId, po));
    po.setCreatedBy(operator);
    po.setUpdatedBy(operator);
    po.setDeleted(false);
    try {
      itemMapper.insert(po);
    } catch (DuplicateKeyException e) {
      // 与并发登记撞键:本轮跳过,下轮走更新分支。
      log.debug("Skip reconcile create on key conflict: {}", descriptor.assetKey());
      return UpsertResult.UNCHANGED;
    }
    for (Long tagId : assignRuleService.suggestedTagIds(projectId, po)) {
      attachTag(projectId, po.getId(), tagId, operator);
    }
    record(po, ChangeType.NEW, null, descriptor, operator);
    return UpsertResult.NEW;
  }

  // ---------- SOURCE_GONE ----------

  int markGoneAssets(Long projectId, List<AssetSourceType> succeededTypes,
      LocalDateTime sweepStart) {
    if (succeededTypes.isEmpty()) {
      return 0;
    }
    int windowDays = Math.max(0, settingService.getInt(projectId, SETTING_GONE_WINDOW_DAYS,
        DEFAULT_GONE_WINDOW_DAYS));
    LocalDateTime cutoff = sweepStart.minusDays(windowDays);
    List<AssetItemPO> stale = itemMapper.selectList(new LambdaQueryWrapper<AssetItemPO>()
        .eq(AssetItemPO::getProjectId, projectId)
        .eq(AssetItemPO::getDeleted, false)
        .ne(AssetItemPO::getStatus, AssetStatus.SOURCE_GONE.name())
        .in(AssetItemPO::getSourceType, succeededTypes.stream().map(Enum::name).toList())
        .and(w -> w.isNull(AssetItemPO::getReconciledAt)
            .or().lt(AssetItemPO::getReconciledAt, cutoff)));
    for (AssetItemPO po : stale) {
      String previousStatus = po.getStatus();
      po.setStatus(AssetStatus.SOURCE_GONE.name());
      po.setUpdatedBy("reconcile");
      po.setUpdateTime(sweepStart);
      itemMapper.updateById(po);
      record(po, ChangeType.SOURCE_GONE,
          Map.of("goneAt", sweepStart.toString(), "previousStatus", previousStatus),
          null, "reconcile");
    }
    return stale.size();
  }

  /** SOURCE_GONE 变更流水里回读"消失前状态",REAPPEARED 恢复用。 */
  private String previousStatusOf(Long assetId) {
    AssetChangeRecordPO last = changeMapper.selectOne(new LambdaQueryWrapper<AssetChangeRecordPO>()
        .eq(AssetChangeRecordPO::getAssetId, assetId)
        .eq(AssetChangeRecordPO::getChangeType, ChangeType.SOURCE_GONE.name())
        .eq(AssetChangeRecordPO::getDeleted, false)
        .orderByDesc(AssetChangeRecordPO::getId)
        .last("LIMIT 1"));
    if (last != null && last.getDiff() != null) {
      Map<String, Object> diff = readJsonMap(last.getDiff());
      Object previous = diff.get("previousStatus");
      if (previous != null) {
        return String.valueOf(previous);
      }
    }
    return AssetStatus.PENDING.name();
  }

  // ---------- helpers ----------

  private void touchFromDescriptor(AssetItemPO po, AssetDescriptor descriptor) {
    po.setContentHash(descriptor.contentHash());
    po.setSourceUpdatedAt(descriptor.updatedAt());
    if (descriptor.assetType() != null) {
      po.setAssetType(descriptor.assetType().name());
    }
    if (descriptor.layerCode() != null) {
      po.setLayerCode(descriptor.layerCode());
    }
    if (descriptor.domainCode() != null) {
      po.setDomainCode(descriptor.domainCode());
    }
    po.setUpdatedBy("reconcile");
  }

  /** 展示字段级前后差异(名称/描述/层/域);台账不自动覆盖,确认时才更新(D1)。 */
  static Map<String, Object> displayDiff(AssetItemPO existing, AssetDescriptor descriptor) {
    Map<String, Object> diff = new LinkedHashMap<>();
    putDiff(diff, "name", existing.getName(), descriptor.name());
    putDiff(diff, "description", existing.getDescription(), descriptor.description());
    putDiff(diff, "layerCode", existing.getLayerCode(), descriptor.layerCode());
    putDiff(diff, "domainCode", existing.getDomainCode(), descriptor.domainCode());
    return diff;
  }

  private static void putDiff(Map<String, Object> diff, String field, Object oldVal,
      Object newVal) {
    if (!Objects.equals(oldVal, newVal)) {
      diff.put(field, List.of(oldVal == null ? "" : oldVal, newVal == null ? "" : newVal));
    }
  }

  private void record(AssetItemPO item, ChangeType type, Map<String, Object> diff,
      AssetDescriptor descriptor, String operator) {
    AssetChangeRecordPO po = new AssetChangeRecordPO();
    po.setProjectId(item.getProjectId());
    po.setAssetId(item.getId());
    po.setChangeType(type.name());
    po.setDiff(diff == null || diff.isEmpty() ? null : writeJson(diff));
    po.setHandleStatus(HandleStatus.OPEN.name());
    po.setSnapshotNew(descriptor == null ? null : writeJson(descriptor));
    po.setCreatedBy(operator);
    po.setDeleted(false);
    changeMapper.insert(po);
  }

  private void attachTag(Long projectId, Long assetId, Long tagId, String operator) {
    AssetTagRelPO rel = new AssetTagRelPO();
    rel.setProjectId(projectId);
    rel.setAssetId(assetId);
    rel.setTagId(tagId);
    rel.setCreatedBy(operator);
    rel.setCreateTime(LocalDateTime.now());
    try {
      tagRelMapper.insert(rel);
    } catch (DuplicateKeyException e) {
      // 规则重复命中:幂等忽略
    }
  }

  private List<AssetSourceType> resolveRequested(List<String> requested) {
    if (requested == null || requested.isEmpty()) {
      List<AssetSourceType> all = new ArrayList<>(registry.registeredTypes());
      if (all.isEmpty()) {
        throw new AssetException(AssetErrorCode.PROVIDER_UNAVAILABLE, "尚无任何已注册的 AssetProvider");
      }
      return all;
    }
    List<AssetSourceType> types = new ArrayList<>();
    for (String raw : requested) {
      AssetSourceType type;
      try {
        type = AssetSourceType.valueOf(raw.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new AssetException(AssetErrorCode.INVALID_ARGUMENT, "未知来源类型: " + raw);
      }
      if (type == AssetSourceType.MANUAL) {
        throw new AssetException(AssetErrorCode.MANUAL_NOT_RECONCILABLE);
      }
      if (registry.find(type).isEmpty()) {
        throw new AssetException(AssetErrorCode.PROVIDER_UNAVAILABLE, type.name());
      }
      types.add(type);
    }
    return types;
  }

  private void audit(Long projectId, List<ProviderOutcome> outcomes, int gone) {
    long failures = outcomes.stream().filter(o -> o.error() != null).count();
    String message = "资产对账完成:provider " + outcomes.size() + " 个,失败 " + failures
        + " 个,消失 " + gone + " 个";
    AuditOperationHandle handle = auditService.start(new AuditOperationRequest(
        "ASSET_RECONCILE", "Reconcile asset catalog", "ASSET_RECONCILE",
        String.valueOf(projectId), message, "APPLICATION",
        Map.of("outcomes", writeJson(outcomes))));
    AuditTransactions.completeOnCommit(handle, AuditEventType.RESOURCE_UPDATED,
        message, Map.of("projectId", String.valueOf(projectId)), null);
  }

  private static String writeJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("asset reconcile JSON serialization failed", e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> readJsonMap(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return MAPPER.readValue(json, Map.class);
    } catch (JsonProcessingException e) {
      return Map.of();
    }
  }

  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }
}
