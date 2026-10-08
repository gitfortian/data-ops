package io.yak.ops.business.semantic.layer;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.semantic.api.LayerStdBindingReader;
import io.yak.ops.business.semantic.api.LayerStdBindingReader.StdBindingStats;
import io.yak.ops.business.semantic.api.LayerUsageReader;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.business.semantic.api.WarehouseLayer;
import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.business.semantic.repository.SemanticLayerRepository;
import io.yak.ops.business.semantic.repository.SemanticLayerTemplateRepository;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns warehouse-layer rules: code format/uniqueness, naming-standard kind
 * matching (std_naming_id must reference a NAMING standard), required core
 * config (database/datasource, 2026-09-16), storage/partition formats,
 * preset-delete protection, model-usage delete blocking (LayerUsageReader
 * SPI), and the idempotent four-layer default initialization.
 */
@Component
public class SemanticLayerService {

  private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,32}$");

  /** 默认分区表达式:分组列=小写字母/下划线,值=字母/数字/连字符(如 dt=yyyyMMdd、hour=HH)。 */
  private static final Pattern PARTITION_PATTERN = Pattern.compile("^[a-z_]+=[A-Za-z-]+$");

  private static final Set<String> STORAGE_FORMATS = Set.of("Parquet", "ORC", "TextFile", "CSV", "JSON");

  private final SemanticLayerRepository repository;
  private final SemanticLayerTemplateRepository templateRepository;
  private final io.yak.ops.business.semantic.repository.SemanticStandardRepository standardRepository;
  private final ObjectProvider<LayerUsageReader> usageReader;
  private final ObjectProvider<LayerStdBindingReader> bindingReader;
  private final BusinessAuditService auditService;

  public SemanticLayerService(
      SemanticLayerRepository repository,
      SemanticLayerTemplateRepository templateRepository,
      io.yak.ops.business.semantic.repository.SemanticStandardRepository standardRepository,
      ObjectProvider<LayerUsageReader> usageReader,
      ObjectProvider<LayerStdBindingReader> bindingReader,
      BusinessAuditService auditService) {
    this.repository = repository;
    this.templateRepository = templateRepository;
    this.standardRepository = standardRepository;
    this.usageReader = usageReader;
    this.bindingReader = bindingReader;
    this.auditService = auditService;
  }

  public List<WarehouseLayer> list() {
    return repository.list();
  }

  /** 分层被引用模型数(2026-09-16):modeling 侧 SPI,modeling 缺席时视为 0。 */
  public Map<String, Long> modelCounts() {
    LayerUsageReader reader = usageReader.getIfAvailable();
    return reader == null ? Map.of() : reader.countModelsByLayer();
  }

  /** 定标观察期(M2-5):各层字段落标统计,modeling 缺席时为空,前端按"无数据"展示。 */
  public Map<String, StdBindingStats> bindingStats() {
    LayerStdBindingReader reader = bindingReader.getIfAvailable();
    return reader == null ? Map.of() : reader.bindingStatsByLayer();
  }

  public WarehouseLayer get(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new SemanticException(SemanticErrorCode.NOT_FOUND, String.valueOf(id)));
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public WarehouseLayer create(
      String code,
      String name,
      String databaseName,
      Long datasourceId,
      Long stdNamingId,
      String defaultPartition,
      String storageFormat,
      Integer lifecycleDays,
      String description,
      Integer sortOrder,
      Boolean stdMandatory,
      String operator) {
    validateCode(code);
    validateName(name);
    validateCoreConfig(databaseName, datasourceId);
    validateNamingRef(stdNamingId);
    validateLifecycleDays(lifecycleDays);
    validateStorageFormat(storageFormat);
    validatePartition(defaultPartition);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_LAYER_CREATE",
                "Create warehouse layer",
                "SEMANTIC_LAYER",
                null,
                code,
                "APPLICATION",
                Map.of()));
    try {
      if (repository.existsByCode(code)) {
        throw new SemanticException(SemanticErrorCode.DUPLICATE_CODE, code);
      }
      WarehouseLayer inserted =
          repository.insert(
              new WarehouseLayer(null, code, name, databaseName, datasourceId, stdNamingId,
                  defaultPartition, storageFormat, lifecycleDays, description,
                  sortOrder == null ? repository.maxSortOrder() + 1 : sortOrder,
                  "ENABLED", stdMandatory == null || stdMandatory, false, operator, null, null),
              operator);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_CREATED,
          "Warehouse layer created",
          Map.of(),
          "Warehouse layer created");
      return inserted;
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_LAYER_CREATE_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public WarehouseLayer update(
      Long id,
      String name,
      String databaseName,
      Long datasourceId,
      Long stdNamingId,
      String defaultPartition,
      String storageFormat,
      Integer lifecycleDays,
      String description,
      Integer sortOrder,
      Boolean stdMandatory) {
    WarehouseLayer existing = get(id);
    validateName(name);
    validateCoreConfig(databaseName, datasourceId);
    validateNamingRef(stdNamingId);
    validateLifecycleDays(lifecycleDays);
    validateStorageFormat(storageFormat);
    validatePartition(defaultPartition);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_LAYER_UPDATE",
                "Update warehouse layer",
                "SEMANTIC_LAYER",
                String.valueOf(id),
                existing.code(),
                "APPLICATION",
                Map.of()));
    try {
      repository.update(
          new WarehouseLayer(existing.id(), existing.code(), name, databaseName, datasourceId,
              stdNamingId, defaultPartition, storageFormat, lifecycleDays, description,
              sortOrder == null ? existing.sortOrder() : sortOrder, existing.status(),
              stdMandatory == null ? existing.stdMandatory() : stdMandatory,
              existing.preset(), existing.createdBy(), existing.createTime(),
              existing.updateTime()));
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Warehouse layer updated",
          Map.of(),
          "Warehouse layer updated");
      return get(id);
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_LAYER_UPDATE_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void changeStatus(Long id, String status, String operator) {
    WarehouseLayer existing = get(id);
    if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
      throw new SemanticException(SemanticErrorCode.INVALID_STATUS, status);
    }
    if (status.equals(existing.status())) return;
    AuditOperationHandle audit = auditService.start(
        new AuditOperationRequest("SEMANTIC_LAYER_STATUS", "Change warehouse layer status",
            "SEMANTIC_LAYER", String.valueOf(id), existing.code(), "APPLICATION",
            Map.of("from", existing.status(), "to", status, "operator", operator)));
    try {
      if (!repository.changeStatus(id, status)) {
        throw new SemanticException(SemanticErrorCode.NOT_FOUND, String.valueOf(id));
      }
      AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_UPDATED,
          "Warehouse layer status changed", Map.of("status", status), "Warehouse layer status changed");
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_LAYER_STATUS_FAILED", exception);
      throw exception;
    }
  }

  /** 删除分层(2026-09-16 收紧):默认分层(is_preset)不可删;自定义分层被模型引用时阻断。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    WarehouseLayer existing = get(id);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_LAYER_DELETE",
                "Delete warehouse layer",
                "SEMANTIC_LAYER",
                String.valueOf(id),
                existing.code(),
                "APPLICATION",
                Map.of()));
    try {
      if (existing.preset()) {
        throw new SemanticException(SemanticErrorCode.LAYER_PRESET_DELETE_BLOCKED, existing.code());
      }
      LayerUsageReader reader = usageReader.getIfAvailable();
      if (reader == null) {
        throw new SemanticException(SemanticErrorCode.DELETE_FAILED,
            "建模引用校验暂不可用，无法安全删除分层，请稍后重试");
      }
      // Dashboard usage counts only live models; deletion must also count
      // recoverable recycle-bin models, otherwise restore leaves broken refs.
      long used = reader.countPersistedLayerReferences(existing.code());
      if (used > 0) {
        throw new SemanticException(
            SemanticErrorCode.LAYER_REFERENCED, existing.code() + "（" + used + " 个模型）");
      }
      if (!repository.deleteById(id)) {
        throw new SemanticException(SemanticErrorCode.DELETE_FAILED, String.valueOf(id));
      }
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_DELETED,
          "Warehouse layer deleted",
          Map.of(),
          "Warehouse layer deleted");
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_LAYER_DELETE_FAILED", exception);
      throw exception;
    }
  }

  /** 幂等初始化四层默认配置(31 同机制:模板复制,已有编码跳过);模板携带库/命名/分区/存储/生命周期默认值。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public int initializeDefaults(String operator) {
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_LAYER_INITIALIZE",
                "Initialize default layers",
                "SEMANTIC_LAYER",
                null,
                "layer-defaults",
                "APPLICATION",
                Map.of()));
    try {
      int created = 0;
      for (var template : templateRepository.findAll()) {
        if (repository.existsByCode(template.layerCode())) {
          continue;
        }
        repository.insert(
            new WarehouseLayer(null, template.layerCode(), template.layerName(),
                template.databaseName(), null, resolveNamingId(template.stdNamingCode()),
                template.defaultPartition(), template.storageFormat(), template.lifecycleDays(),
                template.description(), template.sortOrder(), "ENABLED",
                template.stdMandatory(), true, operator, null, null),
            operator);
        created++;
      }
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_CREATED,
          "Default layers initialized",
          Map.of("created", String.valueOf(created)),
          "Default layers initialized");
      return created;
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_LAYER_INITIALIZE_FAILED", exception);
      throw exception;
    }
  }

  /** 模板中的命名标准编码解析为项目内 NAMING 标准 ID;缺失(未初始化预置标准)时留空。 */
  private Long resolveNamingId(String stdNamingCode) {
    if (!StringUtils.hasText(stdNamingCode)) {
      return null;
    }
    return standardRepository
        .findByCode(StandardKind.NAMING, stdNamingCode)
        .map(Standard::id)
        .orElse(null);
  }

  private void validateNamingRef(Long stdNamingId) {
    if (stdNamingId == null) {
      return;
    }
    Standard standard =
        standardRepository
            .findById(stdNamingId)
            .orElseThrow(
                () ->
                    new SemanticException(SemanticErrorCode.NOT_FOUND, "命名标准不存在"));
    if (standard.kind() != StandardKind.NAMING) {
      throw new SemanticException(
          SemanticErrorCode.INVALID_KIND, "std_naming_id 必须引用 NAMING 类别标准");
    }
    if (standard.status() != StandardStatus.ENABLED) {
      throw new SemanticException(
          SemanticErrorCode.LAYER_CONFIG_INVALID, "分层只能引用启用中的命名标准");
    }
  }

  private void validateLifecycleDays(Integer lifecycleDays) {
    if (lifecycleDays != null && lifecycleDays <= 0) {
      throw new SemanticException(SemanticErrorCode.LAYER_CONFIG_INVALID, "生命周期天数必须大于 0，留空表示永久");
    }
  }

  /** 核心配置必填(2026-09-16):库名 + 数据源,选填等于没配,派生建模无法定位。 */
  private void validateCoreConfig(String databaseName, Long datasourceId) {
    if (!StringUtils.hasText(databaseName)) {
      throw new SemanticException(SemanticErrorCode.LAYER_CONFIG_INVALID, "库名不能为空");
    }
    if (datasourceId == null || datasourceId <= 0) {
      throw new SemanticException(SemanticErrorCode.LAYER_CONFIG_INVALID, "数据源不能为空");
    }
  }

  private void validateStorageFormat(String storageFormat) {
    if (!StringUtils.hasText(storageFormat)) {
      return;
    }
    if (!STORAGE_FORMATS.contains(storageFormat)) {
      throw new SemanticException(
          SemanticErrorCode.LAYER_CONFIG_INVALID, "存储格式必须为 Parquet/ORC/TextFile/CSV/JSON");
    }
  }

  private void validatePartition(String defaultPartition) {
    if (!StringUtils.hasText(defaultPartition)) {
      return;
    }
    if (!PARTITION_PATTERN.matcher(defaultPartition).matches()) {
      throw new SemanticException(
          SemanticErrorCode.LAYER_CONFIG_INVALID, "默认分区表达式须形如 dt=yyyyMMdd、hour=HH");
    }
  }

  private void validateCode(String code) {
    if (!StringUtils.hasText(code) || !CODE_PATTERN.matcher(code).matches()) {
      throw new SemanticException(SemanticErrorCode.INVALID_CODE, code);
    }
  }

  private void validateName(String name) {
    if (!StringUtils.hasText(name)) {
      throw new SemanticException(SemanticErrorCode.INVALID_NAME, "分层名称不能为空");
    }
  }
}
