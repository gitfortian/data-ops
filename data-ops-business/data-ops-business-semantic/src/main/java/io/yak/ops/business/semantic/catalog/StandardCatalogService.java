package io.yak.ops.business.semantic.catalog;

import io.yak.ops.business.semantic.api.StandardStatus;

import io.yak.ops.business.semantic.api.StandardKind;

import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardReferenceReader;

import io.yak.framework.common.PageData;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.semantic.api.SemanticStandardApi;
import io.yak.ops.business.semantic.dao.StandardListRow;
import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.business.semantic.repository.SemanticFieldRepository;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import io.yak.ops.business.semantic.repository.SemanticStandardVersionRepository;
import io.yak.ops.business.semantic.repository.SemanticLayerRepository;
import io.yak.ops.business.approval.api.ApprovalApi;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns the standard catalog lifecycle (create/get/page/update/status/delete).
 * The single home of catalog rules; persistence stays project-scoped in the
 * repository. Status/delete reference checks use the local field/layer records
 * and read-only consumer SPIs, keeping the semantic module independent of its consumers.
 */
@Component
public class StandardCatalogService {

  private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

  /** 行编码自动生成时,码值中的非安全字符清洗为 _(32.1)。 */
  private static final Pattern UNSAFE_CODE_CHARS = Pattern.compile("[^A-Za-z0-9_]");

  private static final int MAX_CODE_STD_LENGTH = 64;

  private static final int MAX_CODE_SUFFIX = 100;

  private final SemanticStandardRepository repository;
  private final SemanticStandardVersionRepository versionRepository;
  private final SemanticFieldRepository fieldRepository;
  private final BusinessAuditService auditService;
  private final SemanticLayerRepository layerRepository;
  private final List<StandardReferenceReader> referenceReaders;
  private final ApprovalApi approvalApi;

  @Autowired
  public StandardCatalogService(
      SemanticStandardRepository repository,
      SemanticStandardVersionRepository versionRepository,
      SemanticFieldRepository fieldRepository,
      BusinessAuditService auditService,
      SemanticLayerRepository layerRepository,
      ObjectProvider<StandardReferenceReader> referenceReaders,
      ObjectProvider<ApprovalApi> approvalApi) {
    this(repository, versionRepository, fieldRepository, auditService, layerRepository,
        referenceReaders.orderedStream().toList(), approvalApi.getIfAvailable());
  }

  /** Compatibility constructor for focused service tests without consumer modules. */
  public StandardCatalogService(
      SemanticStandardRepository repository,
      SemanticStandardVersionRepository versionRepository,
      SemanticFieldRepository fieldRepository,
      BusinessAuditService auditService) {
    this(repository, versionRepository, fieldRepository, auditService, null, List.of(), null);
  }

  private StandardCatalogService(
      SemanticStandardRepository repository,
      SemanticStandardVersionRepository versionRepository,
      SemanticFieldRepository fieldRepository,
      BusinessAuditService auditService,
      SemanticLayerRepository layerRepository,
      List<StandardReferenceReader> referenceReaders,
      ApprovalApi approvalApi) {
    this.repository = repository;
    this.versionRepository = versionRepository;
    this.fieldRepository = fieldRepository;
    this.auditService = auditService;
    this.layerRepository = layerRepository;
    this.referenceReaders = referenceReaders;
    this.approvalApi = approvalApi;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Standard create(SemanticStandardApi.CreateRequest request, String operator) {
    StandardKind kind = parseKind(request.kind());
    validateCode(request.code());
    validateName(request.name());
    Standard.KindFields fields = toFields(request);
    kind.validateRequired(fields);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_STANDARD_CREATE",
                "Create semantic standard",
                "SEMANTIC_STANDARD",
                null,
                request.code(),
                "APPLICATION",
                Map.of("kind", request.kind())));
    try {
      if (repository.existsByCode(kind, request.code())) {
        throw new SemanticException(SemanticErrorCode.DUPLICATE_CODE, request.code());
      }
      Standard inserted =
          repository.insert(
              new Standard(
                  null,
                  kind,
                  request.code(),
                  request.name(),
                  kind != StandardKind.CODE && publishFlowEnabled()
                      ? StandardStatus.DISABLED : StandardStatus.ENABLED,
                  1,
                  request.sortOrder() == null ? 0 : request.sortOrder(),
                  false,
                  request.description(),
                  fields,
                  operator,
                  null,
                  null),
              operator);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_CREATED,
          "Semantic standard created",
          Map.of("kind", request.kind()),
          "Semantic standard created");
      return inserted;
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_STANDARD_CREATE_FAILED", exception);
      throw exception;
    }
  }

  public Standard get(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new SemanticException(SemanticErrorCode.NOT_FOUND, String.valueOf(id)));
  }

  public Standard lockDefinition(Long id) {
    return repository.findByIdForUpdate(id)
        .orElseThrow(() -> new SemanticException(SemanticErrorCode.NOT_FOUND, String.valueOf(id)));
  }

  private void assertNoPendingApproval(Long id) {
    if (approvalApi == null) return;
    var approval = approvalApi.find(ApprovalFlowCodes.STANDARD_PUBLISH, "STANDARD", String.valueOf(id));
    if (approval != null && "PENDING".equals(approval.status())) {
      throw new SemanticException(SemanticErrorCode.INVALID_STATUS, "标准正在审批，请撤销或等待审批结束后修改");
    }
  }

  /** 统一分页(32.1):五类原始行 + CODE 码集组行(SQL GROUP BY),全部视图/码值分类页均聚合。 */
  public PageData<StandardListRow> page(
      int pageNo, int pageSize, String kind, String keyword, String status) {
    return repository.pageListRows(
        pageNo,
        pageSize,
        kind == null || kind.isBlank() ? null : parseKind(kind),
        keyword,
        status == null || status.isBlank() ? null : parseStatus(status));
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Standard update(Long id, SemanticStandardApi.UpdateRequest request, String operator) {
    Standard existing = lockDefinition(id);
    assertNoPendingApproval(id);
    if (existing.kind() == StandardKind.CODE) {
      throw new SemanticException(SemanticErrorCode.INVALID_STATUS,
          "码值标准必须通过码集整体编辑");
    }
    if (request.version() == null || !request.version().equals(existing.version())) {
      throw new SemanticException(SemanticErrorCode.VERSION_CONFLICT,
          "当前版本 " + existing.version() + "，请求版本 " + request.version());
    }
    validateName(request.name());
    Standard.KindFields fields = toFields(request);
    existing.kind().validateRequired(fields);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_STANDARD_UPDATE",
                "Update semantic standard",
                "SEMANTIC_STANDARD",
                String.valueOf(id),
                existing.code(),
                "APPLICATION",
                Map.of()));
    try {
      // 版本快照:先落"修改前"的完整状态(version = 当前版本),再应用更新(32)。
      versionRepository.recordSnapshot(existing, operator);
      Standard updated =
          new Standard(
              existing.id(),
              existing.kind(),
              existing.code(),
              request.name(),
              existing.status(),
              existing.version() + 1,
              request.sortOrder() == null ? existing.sortOrder() : request.sortOrder(),
              existing.preset(),
              request.description(),
              fields,
              existing.createdBy(),
              existing.createTime(),
              existing.updateTime());
      Standard saved = repository.update(updated, request.version(), operator);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Semantic standard updated",
          Map.of("version", String.valueOf(saved.version())),
          "Semantic standard updated");
      return saved;
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_STANDARD_UPDATE_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Standard changeStatus(Long id, String status, String operator) {
    return changeStatus(id, status, operator, false);
  }

  /** Approval terminal callback; regular status changes cannot bypass an enabled flow. */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Standard enableAfterApproval(Long id, String operator) {
    return changeStatus(id, StandardStatus.ENABLED.name(), operator, true);
  }

  private Standard changeStatus(Long id, String status, String operator, boolean approved) {
    Standard existing = lockDefinition(id);
    if (!approved) assertNoPendingApproval(id);
    if (existing.kind() == StandardKind.CODE) {
      throw new SemanticException(SemanticErrorCode.INVALID_STATUS,
          "码值标准必须通过码集整体启停");
    }
    StandardStatus target = parseStatus(status);
    if (target == StandardStatus.DISABLED && countReferences(existing) > 0) {
      throw new SemanticException(SemanticErrorCode.STANDARD_REFERENCED,
          "该标准仍被字段库、分层、建模或指标引用");
    }
    if (target == StandardStatus.ENABLED && existing.kind() != StandardKind.CODE
        && !approved && publishFlowEnabled()) {
      throw new SemanticException(SemanticErrorCode.INVALID_STATUS,
          "当前项目已启用 STANDARD_PUBLISH 流程，请通过发布审批启用标准");
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_STANDARD_STATUS",
                "Change semantic standard status",
                "SEMANTIC_STANDARD",
                String.valueOf(id),
                existing.code(),
                "APPLICATION",
                Map.of("status", target.name())));
    try {
      if (target != existing.status() && !repository.updateStatus(id, target, operator)) {
        throw new SemanticException(SemanticErrorCode.NOT_FOUND, String.valueOf(id));
      }
      Standard updated = existing.withStatus(target, null);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Semantic standard status changed",
          Map.of("status", target.name()),
          "Semantic standard status changed");
      return updated;
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_STANDARD_STATUS_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id, String operator) {
    Standard existing = lockDefinition(id);
    assertNoPendingApproval(id);
    if (existing.kind() == StandardKind.CODE) {
      throw new SemanticException(SemanticErrorCode.INVALID_STATUS,
          "码值标准必须通过码集整体删除");
    }
    if (existing.preset()) {
      throw new SemanticException(SemanticErrorCode.PRESET_DELETE_BLOCKED, existing.code());
    }
    if (countReferences(existing) > 0) {
      throw new SemanticException(SemanticErrorCode.STANDARD_REFERENCED,
          "该标准仍被字段库、分层、建模或指标引用");
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_STANDARD_DELETE",
                "Delete semantic standard",
                "SEMANTIC_STANDARD",
                String.valueOf(id),
                existing.code(),
                "APPLICATION",
                Map.of()));
    try {
      if (!repository.deleteById(id)) {
        throw new SemanticException(SemanticErrorCode.DELETE_FAILED, String.valueOf(id));
      }
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_DELETED,
          "Semantic standard deleted",
          Map.of(),
          "Semantic standard deleted");
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_STANDARD_DELETE_FAILED", exception);
      throw exception;
    }
  }

  /** 31 初始化判定:项目内是否已有任意标准行。 */
  public long countByProject() {
    return repository.countByProject();
  }

  /** 32 版本回溯:某标准的全部历史版本(修改前状态),最新在前。 */
  public java.util.List<StandardVersion> listVersions(Long id) {
    get(id);
    return versionRepository.listByStandard(id);
  }

  /** Create-only path used by the CREATE-authorized endpoint. */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public List<Standard> createCodeSet(
      SemanticStandardApi.CodeSetSaveRequest request, String operator) {
    if (StringUtils.hasText(request.originCodeSetCode())) {
      throw new SemanticException(SemanticErrorCode.INVALID_CODE,
          "新建码集不能携带存量迁移组键");
    }
    if (repository.existsByCodeSetCode(request.codeSetCode())
        || !repository.listByCodeSetCode(request.codeSetCode()).isEmpty()) {
      throw new SemanticException(SemanticErrorCode.CODE_SET_DUPLICATE, request.codeSetCode());
    }
    return saveCodeSet(request, operator);
  }

  /** Update-only path used by the UPDATE-authorized endpoint. */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public List<Standard> updateCodeSet(
      SemanticStandardApi.CodeSetSaveRequest request, String operator) {
    if (!StringUtils.hasText(request.originCodeSetCode())
        && !repository.existsByCodeSetCode(request.codeSetCode())) {
      throw new SemanticException(SemanticErrorCode.CODE_SET_NOT_FOUND, request.codeSetCode());
    }
    return saveCodeSet(request, operator);
  }

  // ── 码集方法(32.1:码值类聚合交互) ──

  /**
   * 批量保存码集:一次写入同一码集下的全部码值行,事务原子。
   * 行编码 = {码集编码}_{码值},码值非安全字符清洗为 _,冲突自动加数字后缀;
   * 修改行先落版本快照(32 不变式);originCodeSetCode 仅存量空码集行补全编码时传入。
   */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public List<Standard> saveCodeSet(SemanticStandardApi.CodeSetSaveRequest request, String operator) {
    String codeSetCode = request.codeSetCode();
    validateCode(codeSetCode);
    validateName(request.name());
    List<SemanticStandardApi.CodeValueItem> items = normalizeCodeValues(request);
    String originKey =
        StringUtils.hasText(request.originCodeSetCode())
            ? request.originCodeSetCode().trim()
            : codeSetCode;
    boolean adopting = StringUtils.hasText(request.originCodeSetCode());
    if (adopting && originKey.equals(codeSetCode)) {
      throw new SemanticException(SemanticErrorCode.INVALID_CODE,
          "原组键仅用于采纳存量未编码的码值行");
    }
    if (adopting && (repository.existsByCodeSetCode(codeSetCode)
        || !repository.listByCodeSetCode(codeSetCode).isEmpty())) {
      throw new SemanticException(SemanticErrorCode.CODE_SET_DUPLICATE, codeSetCode);
    }

    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_CODE_SET_SAVE",
                "Save semantic code set",
                "SEMANTIC_STANDARD",
                null,
                codeSetCode,
                "APPLICATION",
                Map.of("valueCount", String.valueOf(items.size()))));
    try {
      List<Standard> existingRows = repository.lockCodeSet(originKey);
      if (!existingRows.isEmpty() && !CodeSetRevision.of(existingRows).equals(request.revision())) {
        throw new SemanticException(SemanticErrorCode.VERSION_CONFLICT,
            "码集已被修改，请保留输入并重新加载后合并");
      }
      if (adopting && existingRows.isEmpty()) {
        throw new SemanticException(SemanticErrorCode.CODE_SET_NOT_FOUND, originKey);
      }
      if (adopting && existingRows.stream().anyMatch(
          row -> StringUtils.hasText(row.fields().codeSetCode()))) {
        throw new SemanticException(SemanticErrorCode.INVALID_CODE,
            "仅允许迁移 code_set_code 为空的存量码值行");
      }
      boolean creating = existingRows.isEmpty();
      if (creating && repository.existsByCodeSetCode(codeSetCode)) {
        throw new SemanticException(SemanticErrorCode.CODE_SET_DUPLICATE, codeSetCode);
      }
      Map<String, Standard> existingByValue =
          existingRows.stream()
              .collect(
                  Collectors.toMap(
                      row -> row.fields().codeValue(), Function.identity(), (first, second) -> first));
      StandardStatus groupStatus = existingRows.stream()
          .anyMatch(row -> row.status() == StandardStatus.DISABLED)
          ? StandardStatus.DISABLED : StandardStatus.ENABLED;
      Set<String> submittedValues =
          items.stream().map(SemanticStandardApi.CodeValueItem::codeValue).collect(Collectors.toSet());
      boolean removesValues = existingRows.stream()
          .anyMatch(row -> !submittedValues.contains(row.fields().codeValue()));
      if (removesValues && countCodeSetReferences(codeSetCode) > 0) {
        throw new SemanticException(SemanticErrorCode.STANDARD_REFERENCED,
            "该码集已有字段或模型引用，不能移除其中的码值");
      }
      // 提交中不存在的既有码值 = 删除(与单行删除一致,不落快照)
      for (Standard existing : existingRows) {
        if (!submittedValues.contains(existing.fields().codeValue())) {
          repository.deleteById(existing.id());
        }
      }
      List<Standard> result = new ArrayList<>();
      for (SemanticStandardApi.CodeValueItem item : items) {
        Standard existing = existingByValue.get(item.codeValue());
        if (existing != null) {
          // 版本快照:先落"修改前"的完整状态,再应用更新(32)
          versionRepository.recordSnapshot(existing, operator);
          result.add(repository.update(
              codeSetRow(existing, codeSetCode, request, item, groupStatus), operator));
        } else {
          result.add(
              repository.insert(
                  new Standard(
                      null,
                      StandardKind.CODE,
                      generateUniqueRowCode(codeSetCode, item.codeValue()),
                      request.name(),
                      groupStatus,
                      1,
                      item.sortOrder() == null ? 0 : item.sortOrder(),
                      false,
                      request.description(),
                      codeSetFields(codeSetCode, item),
                      operator,
                      null,
                      null),
                  operator));
        }
      }
      AuditTransactions.completeOnCommit(
          audit,
          creating ? AuditEventType.RESOURCE_CREATED : AuditEventType.RESOURCE_UPDATED,
          creating ? "Semantic code set created" : "Semantic code set updated",
          Map.of("valueCount", String.valueOf(result.size())),
          creating ? "Semantic code set created" : "Semantic code set updated");
      return result;
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_CODE_SET_SAVE_FAILED", exception);
      throw exception;
    }
  }

  /** 码集聚合分页:与统一分页同源 SQL(kind=CODE 只出组行),一行 = 一个码集。 */
  public PageData<StandardListRow> pageCodeSet(
      int pageNo, int pageSize, String keyword, String status) {
    return repository.pageListRows(
        pageNo,
        pageSize,
        StandardKind.CODE,
        keyword,
        status == null || status.isBlank() ? null : parseStatus(status));
  }

  /** 字段码值引用下拉(35):启用码集选项(value = code_set_code)。 */
  public List<StandardListRow> listCodeSetOptions() {
    return repository.listEnabledCodeSetOptions();
  }

  /**
   * 字段引用下拉(35):按类别返回启用标准,编辑弹窗打开时按需加载,不做列表页预载。
   * CODE 不在此列(引用的是码集编码,走专用码集选项端点)。
   */
  public Map<StandardKind, List<Standard>> listStandardOptions(String kinds) {
    Map<StandardKind, List<Standard>> grouped = new LinkedHashMap<>();
    for (String raw : kinds.split(",")) {
      StandardKind kind = StandardKind.fromStored(raw.trim()).orElse(null);
      if (kind == null || kind == StandardKind.CODE) {
        continue;
      }
      grouped.put(kind, repository.listEnabledByKind(kind));
    }
    return grouped;
  }

  /** 码集级版本(32.1):合并组内各行的修改前快照,按时间倒序。 */
  public List<StandardVersion> listCodeSetVersions(String codeSetCode) {
    List<Standard> rows = repository.listByCodeSetCode(codeSetCode);
    if (rows.isEmpty()) {
      throw new SemanticException(SemanticErrorCode.CODE_SET_NOT_FOUND, codeSetCode);
    }
    Map<String, StandardVersion> snapshots = new LinkedHashMap<>();
    versionRepository.listByCodeSet(codeSetCode).forEach(version ->
        snapshots.put(version.standardId() + ":" + version.version(), version));
    // Live rows also cover legacy groups whose old snapshots have no code_set_code.
    rows.stream()
        .flatMap(row -> versionRepository.listByStandard(row.id()).stream())
        .forEach(version -> snapshots.putIfAbsent(version.standardId() + ":" + version.version(), version));
    return snapshots.values().stream()
        .sorted(
            java.util.Comparator.comparing(
                StandardVersion::createTime, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
        .toList();
  }

  /** 按组键(码集编码;存量空码集行为 std_code)查询详情,含全部码值行。 */
  public List<Standard> getCodeSet(String codeSetCode) {
    List<Standard> rows = repository.listByCodeSetCode(codeSetCode);
    if (rows.isEmpty()) {
      throw new SemanticException(SemanticErrorCode.CODE_SET_NOT_FOUND, codeSetCode);
    }
    return rows;
  }

  /** 码集编码是否已存在(严格按 code_set_code 匹配,不含存量空码集行)。 */
  public boolean existsCodeSet(String codeSetCode) {
    return repository.existsByCodeSetCode(codeSetCode);
  }

  /** 整组启停码集(不支持单独停用某一码值行)。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public int changeCodeSetStatus(String codeSetCode, String status, String operator) {
    List<Standard> rows = repository.listByCodeSetCode(codeSetCode);
    if (rows.isEmpty()) {
      throw new SemanticException(SemanticErrorCode.CODE_SET_NOT_FOUND, codeSetCode);
    }
    StandardStatus target = parseStatus(status);
    if (target == StandardStatus.DISABLED && countCodeSetReferences(codeSetCode) > 0) {
      throw new SemanticException(SemanticErrorCode.STANDARD_REFERENCED, codeSetCode);
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_CODE_SET_STATUS",
                "Change semantic code set status",
                "SEMANTIC_STANDARD",
                null,
                codeSetCode,
                "APPLICATION",
                Map.of("status", target.name())));
    try {
      int count = repository.updateStatusByCodeSetCode(codeSetCode, target, operator);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Semantic code set status changed",
          Map.of("status", target.name()),
          "Semantic code set status changed");
      return count;
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_CODE_SET_STATUS_FAILED", exception);
      throw exception;
    }
  }

  /** 整组删除码集:任一码值所属码集已被标准字段引用(35)时阻断。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public int deleteCodeSet(String codeSetCode, String operator) {
    List<Standard> rows = repository.listByCodeSetCode(codeSetCode);
    if (rows.isEmpty()) {
      throw new SemanticException(SemanticErrorCode.CODE_SET_NOT_FOUND, codeSetCode);
    }
    if (rows.stream().anyMatch(Standard::preset)) {
      throw new SemanticException(SemanticErrorCode.PRESET_DELETE_BLOCKED, codeSetCode);
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_CODE_SET_DELETE",
                "Delete semantic code set",
                "SEMANTIC_STANDARD",
                null,
                codeSetCode,
                "APPLICATION",
                Map.of()));
    try {
      if (countCodeSetReferences(codeSetCode) > 0) {
        throw new SemanticException(SemanticErrorCode.STANDARD_REFERENCED, codeSetCode);
      }
      int count = repository.deleteByCodeSetCode(codeSetCode);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_DELETED,
          "Semantic code set deleted",
          Map.of("valueCount", String.valueOf(count)),
          "Semantic code set deleted");
      return count;
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_CODE_SET_DELETE_FAILED", exception);
      throw exception;
    }
  }

  /** 码值 trim + 空白标签归空 + 组内去重(服务端兜底,前端已有实时校验)。 */
  private static List<SemanticStandardApi.CodeValueItem> normalizeCodeValues(
      SemanticStandardApi.CodeSetSaveRequest request) {
    List<SemanticStandardApi.CodeValueItem> items = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (SemanticStandardApi.CodeValueItem item : request.values()) {
      String value = item.codeValue().trim();
      if (!seen.add(value)) {
        throw new SemanticException(SemanticErrorCode.CODE_SET_VALUE_DUPLICATE, request.codeSetCode());
      }
      String label =
          StringUtils.hasText(item.codeLabel()) ? item.codeLabel().trim() : null;
      items.add(new SemanticStandardApi.CodeValueItem(value, label, item.sortOrder()));
    }
    return items;
  }

  /** 行编码自动生成:{码集编码}_{码值清洗},冲突追加 _2..;总长不超过 64。 */
  private String generateUniqueRowCode(String codeSetCode, String codeValue) {
    String sanitized = UNSAFE_CODE_CHARS.matcher(codeValue).replaceAll("_");
    String base = codeSetCode + "_" + sanitized;
    String candidate = truncateCode(base, "");
    int suffix = 1;
    while (repository.existsByCode(StandardKind.CODE, candidate)) {
      suffix++;
      if (suffix > MAX_CODE_SUFFIX) {
        throw new SemanticException(SemanticErrorCode.DUPLICATE_CODE, base);
      }
      candidate = truncateCode(base, "_" + suffix);
    }
    return candidate;
  }

  private static String truncateCode(String base, String tail) {
    int keep = Math.min(base.length(), MAX_CODE_STD_LENGTH - tail.length());
    return base.substring(0, keep) + tail;
  }

  private static Standard.KindFields codeSetFields(
      String codeSetCode, SemanticStandardApi.CodeValueItem item) {
    return new Standard.KindFields(null, null, null, null, null, null, null,
        codeSetCode, item.codeValue(), item.codeLabel(),
        null, null, null, null, null, null, null);
  }

  private static Standard codeSetRow(
      Standard existing,
      String codeSetCode,
      SemanticStandardApi.CodeSetSaveRequest request,
      SemanticStandardApi.CodeValueItem item,
      StandardStatus status) {
    return new Standard(
        existing.id(),
        StandardKind.CODE,
        existing.code(),
        request.name(),
        status,
        existing.version() + 1,
        item.sortOrder() == null ? existing.sortOrder() : item.sortOrder(),
        existing.preset(),
        request.description(),
        codeSetFields(codeSetCode, item),
        existing.createdBy(),
        existing.createTime(),
        existing.updateTime());
  }

  public boolean publishFlowEnabled() {
    return approvalApi != null && approvalApi.isFlowEnabled(ApprovalFlowCodes.STANDARD_PUBLISH);
  }

  private long countCodeSetReferences(String codeSetCode) {
    long count = fieldRepository.countByCodeSet(codeSetCode);
    for (StandardReferenceReader reader : referenceReaders) {
      count += reader.countReferences(StandardKind.CODE, null, codeSetCode);
    }
    return count;
  }

  private long countReferences(Standard standard) {
    if (standard.kind() == StandardKind.CODE) {
      return countCodeSetReferences(standard.fields().codeSetCode());
    }
    long count = fieldRepository.countByStandard(standard.kind(), standard.id());
    if (standard.kind() == StandardKind.NAMING && layerRepository != null) {
      count += layerRepository.countByNamingStandard(standard.id());
    }
    for (StandardReferenceReader reader : referenceReaders) {
      count += reader.countReferences(standard.kind(), standard.id(), null);
    }
    return count;
  }

  private static Standard.KindFields toFields(SemanticStandardApi.CreateRequest request) {
    return new Standard.KindFields(
        request.scope(),
        request.layer(),
        request.ruleExpr(),
        request.example(),
        request.typeCode(),
        request.stdType(),
        request.sourceMapping(),
        request.codeSetCode(),
        request.codeValue(),
        request.codeLabel(),
        request.unitCode(),
        request.unitType(),
        request.caliberCode(),
        request.calRule(),
        request.businessDesc(),
        request.levelCode(),
        request.maskRule());
  }

  private static Standard.KindFields toFields(SemanticStandardApi.UpdateRequest request) {
    return new Standard.KindFields(
        request.scope(),
        request.layer(),
        request.ruleExpr(),
        request.example(),
        request.typeCode(),
        request.stdType(),
        request.sourceMapping(),
        request.codeSetCode(),
        request.codeValue(),
        request.codeLabel(),
        request.unitCode(),
        request.unitType(),
        request.caliberCode(),
        request.calRule(),
        request.businessDesc(),
        request.levelCode(),
        request.maskRule());
  }

  private static StandardKind parseKind(String kind) {
    return StandardKind.fromStored(kind)
        .orElseThrow(() -> new SemanticException(SemanticErrorCode.INVALID_KIND, kind));
  }

  private static StandardStatus parseStatus(String status) {
    return StandardStatus.fromStored(status)
        .orElseThrow(() -> new SemanticException(SemanticErrorCode.INVALID_STATUS, status));
  }

  private static void validateCode(String code) {
    if (!StringUtils.hasText(code) || !CODE_PATTERN.matcher(code).matches()) {
      throw new SemanticException(SemanticErrorCode.INVALID_CODE, code);
    }
  }

  private static void validateName(String name) {
    if (!StringUtils.hasText(name)) {
      throw new SemanticException(SemanticErrorCode.INVALID_NAME, "标准名称不能为空");
    }
  }
}
