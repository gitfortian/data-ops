package io.yak.ops.business.modeling.catalog;

import io.yak.framework.common.PageData;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.modeling.api.ModelingModelApi;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelingDirectory;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelDirectoryRepository;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.repository.ModelTagRepository;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns the modeling catalog lifecycle (create/get/page/edit/assign/delete and
 * the recycle bin). The single home of catalog rules; persistence stays
 * project-scoped in the repositories.
 */
@Component
public class ModelCatalogService {

  private final ModelRepository repository;
  private final ModelTagRepository tagRepository;
  private final ModelDirectoryRepository directoryRepository;
  private final io.yak.ops.business.semantic.api.ProcessApi processApi;
  private final io.yak.ops.business.semantic.api.LayerConfigApi layerConfigApi;
  private final BusinessAuditService auditService;

  public ModelCatalogService(
      ModelRepository repository,
      ModelTagRepository tagRepository,
      ModelDirectoryRepository directoryRepository,
      io.yak.ops.business.semantic.api.ProcessApi processApi,
      io.yak.ops.business.semantic.api.LayerConfigApi layerConfigApi,
      BusinessAuditService auditService) {
    this.repository = repository;
    this.tagRepository = tagRepository;
    this.directoryRepository = directoryRepository;
    this.processApi = processApi;
    this.layerConfigApi = layerConfigApi;
    this.auditService = auditService;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Model create(String name, String code, String dialect, String description, String operator) {
    return create(name, code, dialect, description, operator, null, null, null, null, null, null);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Model create(String name, String code, String dialect, String description, String operator, Long directoryId) {
    return create(name, code, dialect, description, operator, directoryId, null, null, null, null, null);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Model create(String name, String code, String dialect, String description, String operator, Long directoryId, String layerCode, Long processId) {
    return create(name, code, dialect, description, operator, directoryId, layerCode, processId, null, null, null);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Model create(String name, String code, String dialect, String description, String operator, Long directoryId, String layerCode, Long processId, Long sourceDatasourceId, String sourceDatabase, String sourceTable) {
    return create(name, code, dialect, description, operator, directoryId, layerCode, processId, sourceDatasourceId, sourceDatabase, sourceTable, null);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Model create(String name, String code, String dialect, String description, String operator, Long directoryId, String layerCode, Long processId, Long sourceDatasourceId, String sourceDatabase, String sourceTable, Long domainId) {
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MODELING_MODEL_CREATE",
                "Create modeling model",
                "MODELING_MODEL",
                null,
                code,
                "APPLICATION",
                Map.of("dialect", String.valueOf(dialect))));
    try {
      ModelDialect resolvedDialect =
          ModelDialect.fromStored(dialect)
              .orElseThrow(() -> new ModelingException(ModelingErrorCode.INVALID_DIALECT, dialect));
      if (domainId != null && domainId > 0) {
        requireDomain(domainId);
      }
      // 目录跟随业务域：未显式指定目录时按业务域自动落/复用同名目录。
      Long resolvedDirectoryId =
          directoryId != null && directoryId > 0 ? directoryId : ensureDirectoryForDomain(domainId);
      if (directoryId != null && directoryId > 0) {
        directoryRepository
            .findById(directoryId)
            .orElseThrow(
                () ->
                    new ModelingException(
                        ModelingErrorCode.NOT_FOUND, "目标目录不存在：" + directoryId));
      }
      Model model =
          Model.create(
              code, name, resolvedDialect, description, resolvedDirectoryId, layerCode, processId,
              domainId);
      if (repository.existsByCode(code)) {
        throw new ModelingException(ModelingErrorCode.DUPLICATE_CODE, code);
      }
      Model inserted = repository.insert(model, operator);
      // 2026-09-17:新建模型分步，写入分层/业务过程/目录（已有列，不改动表结构）。
      if (layerCode != null && !layerCode.isBlank()) {
        repository.assignLayer(inserted.id(), layerCode, operator);
      }
      if (processId != null && processId > 0) {
        repository.assignProcess(inserted.id(), processId, layerCode, operator);
      }
      if (resolvedDirectoryId != null && resolvedDirectoryId > 0) {
        repository.updateDirectory(inserted.id(), resolvedDirectoryId, operator);
      }
      // 写入来源数据源绑定(逆向导入,用于血缘)
      if (sourceDatasourceId != null && sourceDatasourceId > 0 && sourceTable != null && !sourceTable.isBlank()) {
        repository.assignSource(inserted.id(), sourceDatasourceId, sourceDatabase, sourceTable, operator);
      }
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_CREATED,
          "Modeling model created",
          Map.of("dialect", resolvedDialect.name()),
          "Modeling model created");
      return inserted;
    } catch (RuntimeException exception) {
      audit.failure("MODELING_MODEL_CREATE_FAILED", exception);
      throw exception;
    }
  }

  /** 业务域存在性校验(semantic 松散引用;跨项目空间读失败一律视为不存在)。 */
  private void requireDomain(Long domainId) {
    boolean exists;
    try {
      exists = processApi.listDomains().stream()
          .anyMatch(domain -> domain.id().equals(domainId));
    } catch (RuntimeException ignored) {
      exists = false;
    }
    if (!exists) {
      throw new ModelingException(ModelingErrorCode.NOT_FOUND, "业务域不存在：" + domainId);
    }
  }

  /**
   * 业务域 → 目录：沿域树（含父链）复用或自动创建同名目录，目录树与业务域树一一对应。
   * 依赖 semantic 跨模块读取，读不到时返回 null（不阻塞保存，模型落在未分类）。
   */
  private Long ensureDirectoryForDomain(Long domainId) {
    if (domainId == null || domainId <= 0) {
      return null;
    }
    Map<Long, io.yak.ops.business.semantic.domain.BusinessDomain> domains;
    try {
      domains =
          processApi.listDomains().stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      io.yak.ops.business.semantic.domain.BusinessDomain::id,
                      domain -> domain,
                      (first, second) -> first));
    } catch (RuntimeException ignored) {
      return null;
    }
    return ensureDirectoryChain(domainId, domains, new java.util.HashSet<>());
  }

  private Long ensureDirectoryChain(
      Long domainId,
      Map<Long, io.yak.ops.business.semantic.domain.BusinessDomain> domains,
      java.util.Set<Long> visited) {
    if (domainId == null || domainId <= 0 || !visited.add(domainId)) {
      return null;
    }
    io.yak.ops.business.semantic.domain.BusinessDomain domain = domains.get(domainId);
    if (domain == null) {
      return null;
    }
    Long parentDirectoryId = ensureDirectoryChain(domain.parentId(), domains, visited);
    String name = domain.name();
    java.util.Optional<ModelingDirectory> bound = directoryRepository.findByDomainId(domainId);
    if (bound.isPresent()) {
      ModelingDirectory directory = bound.get();
      // 域改名时跟随改名；同级已有同名目录则保持原样，不做破坏性合并。
      if (!name.equals(directory.name())
          && directoryRepository.findByParentAndName(directory.parentId(), name).isEmpty()) {
        directoryRepository.updateName(directory.id(), name);
      }
      return directory.id();
    }
    java.util.Optional<ModelingDirectory> sameName =
        directoryRepository.findByParentAndName(parentDirectoryId, name);
    if (sameName.isPresent()) {
      directoryRepository.bindDomain(sameName.get().id(), domainId);
      return sameName.get().id();
    }
    return directoryRepository.insert(parentDirectoryId, name, domainId).id();
  }

  public Model get(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ModelingException(ModelingErrorCode.NOT_FOUND, String.valueOf(id)));
  }

  public PageData<Model> page(
      int pageNo, int pageSize, String keyword, Long directoryId, List<Long> tagIds,
      String layerCode, Long processId, java.util.List<Long> processIds, String status,
      Long domainId) {
    return repository.page(
        pageNo, pageSize, keyword, directoryId, tagIds, layerCode, processId, processIds, status,
        domainId);
  }

  /** 业务过程 ID → 名称(工作台列表"业务过程"列,2026-09-17)。 */
  public Map<Long, String> processNames() {
    try {
      return processApi.listProcesses(null).stream()
          .collect(
              java.util.stream.Collectors.toMap(
                  io.yak.ops.business.semantic.process.BusinessProcess::id,
                  io.yak.ops.business.semantic.process.BusinessProcess::name,
                  (first, second) -> first));
    } catch (RuntimeException ignored) {
      return Map.of();
    }
  }

  /** 业务域 ID → 名称(列表/详情"业务域"展示)。 */
  public Map<Long, String> domainNames() {
    try {
      return processApi.listDomains().stream()
          .collect(
              java.util.stream.Collectors.toMap(
                  io.yak.ops.business.semantic.domain.BusinessDomain::id,
                  io.yak.ops.business.semantic.domain.BusinessDomain::name,
                  (first, second) -> first));
    } catch (RuntimeException ignored) {
      return Map.of();
    }
  }

  /** 分层编码 → 目标库名(工作台列表"目标库"列,2026-09-17)。 */
  public Map<String, String> layerDatabaseNames() {
    try {
      return layerConfigApi.listLayers().stream()
          .filter(layer -> layer.databaseName() != null)
          .collect(
              java.util.stream.Collectors.toMap(
                  io.yak.ops.business.semantic.layer.WarehouseLayer::code,
                  io.yak.ops.business.semantic.layer.WarehouseLayer::databaseName,
                  (first, second) -> first));
    } catch (RuntimeException ignored) {
      return Map.of();
    }
  }

  /**
   * Updates the model basics. Code stays the immutable stable key (DOMAIN.md);
   * everything else is editable. Null reference fields mean "leave unchanged" —
   * clearing a directory/tag binding keeps its dedicated endpoint semantics.
   */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void updateBasics(Long id, ModelingModelApi.UpdateRequest request, String operator) {
    Model existing = get(id);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MODELING_MODEL_UPDATE",
                "Update modeling model",
                "MODELING_MODEL",
                String.valueOf(id),
                existing.name(),
                "APPLICATION",
                Map.of()));
    try {
      String dialect =
          request.dialect() == null || request.dialect().isBlank()
              ? existing.dialect().name()
              : ModelDialect.fromStored(request.dialect())
                  .orElseThrow(
                      () -> new ModelingException(ModelingErrorCode.INVALID_DIALECT, request.dialect()))
                  .name();
      if (!StringUtils.hasText(request.name())) {
        throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "模型名称不能为空");
      }
      if (request.domainId() != null && request.domainId() > 0) {
        requireDomain(request.domainId());
      }
      if (request.directoryId() != null && request.directoryId() > 0) {
        directoryRepository
            .findById(request.directoryId())
            .orElseThrow(
                () ->
                    new ModelingException(
                        ModelingErrorCode.NOT_FOUND, "目标目录不存在：" + request.directoryId()));
      }
      if (!repository.updateBasics(id, request.name().trim(), dialect, normalize(request.description()), operator)) {
        throw new ModelingException(ModelingErrorCode.UPDATE_FAILED);
      }
      if (request.directoryId() != null) {
        repository.updateDirectory(id, request.directoryId(), operator);
      } else if (request.domainId() != null) {
        // 目录跟随业务域：换域则重新绑定，清除业务域则回到未分类。
        if (request.domainId() > 0) {
          Long derived = ensureDirectoryForDomain(request.domainId());
          if (derived != null) {
            repository.updateDirectory(id, derived, operator);
          }
        } else {
          repository.updateDirectory(id, null, operator);
        }
      }
      if (request.domainId() != null) {
        repository.updateDomain(id, request.domainId() > 0 ? request.domainId() : null, operator);
      }
      // <=0 = 清空归属（编辑弹窗提交完整表单；目录清空另有专用端点，语义一致）
      Long newProcessId = request.processId() == null || request.processId() <= 0 ? null : request.processId();
      String newLayerCode = request.layerCode() == null || request.layerCode().isBlank() ? null : request.layerCode();
      if (request.layerCode() != null || request.processId() != null) {
        repository.assignProcess(id, newProcessId, newLayerCode, operator);
      }
      Long sourceId =
          request.sourceDatasourceId() != null && request.sourceDatasourceId() > 0
              ? request.sourceDatasourceId()
              : existing.sourceDatasourceId();
      if (sourceId != null && StringUtils.hasText(request.sourceTable())) {
        repository.assignSource(id, sourceId, request.sourceDatabase(), request.sourceTable(), operator);
      }
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Modeling model updated",
          Map.of(),
          "模型基础信息已更新");
    } catch (RuntimeException exception) {
      audit.failure("MODELING_MODEL_UPDATE_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void assignDirectory(Long id, Long directoryId, String operator) {
    Model existing = get(id);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MODELING_MODEL_ASSIGN_DIRECTORY",
                "Assign modeling model directory",
                "MODELING_MODEL",
                String.valueOf(id),
                existing.name(),
                "APPLICATION",
                Map.of("directoryId", String.valueOf(directoryId))));
    try {
      // 归属校验：目标目录必须存在且属于当前项目空间（PROJECT_SCOPE）。
      if (directoryId != null && directoryId > 0) {
        directoryRepository
            .findById(directoryId)
            .orElseThrow(
                () ->
                    new ModelingException(
                        ModelingErrorCode.NOT_FOUND, "目标目录不存在：" + directoryId));
      }
      if (!repository.updateDirectory(id, directoryId, operator)) {
        throw new ModelingException(ModelingErrorCode.UPDATE_FAILED);
      }
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Modeling model directory assigned",
          Map.of(),
          "模型目录已更新");
    } catch (RuntimeException exception) {
      audit.failure("MODELING_MODEL_ASSIGN_DIRECTORY_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void assignTags(Long id, List<Long> tagIds) {
    Model existing = get(id);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MODELING_MODEL_ASSIGN_TAGS",
                "Assign modeling model tags",
                "MODELING_MODEL",
                String.valueOf(id),
                existing.name(),
                "APPLICATION",
                Map.of("tagCount", tagIds == null ? 0 : tagIds.size())));
    try {
      // 归属校验：每个标签必须存在且属于当前项目空间（PROJECT_SCOPE）。
      if (tagIds != null) {
        for (Long tagId : tagIds.stream().distinct().toList()) {
          tagRepository
              .findById(tagId)
              .orElseThrow(
                  () -> new ModelingException(ModelingErrorCode.NOT_FOUND, "标签不存在：" + tagId));
        }
      }
      tagRepository.replaceModelTags(id, tagIds);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Modeling model tags assigned",
          Map.of(),
          "模型标签已更新");
    } catch (RuntimeException exception) {
      audit.failure("MODELING_MODEL_ASSIGN_TAGS_FAILED", exception);
      throw exception;
    }
  }

  /** 血缘追溯:写入字段导入方式和来源模型。 */
  public boolean assignImportLineage(Long id, String importMode, Long sourceModelId, String operator) {
    return repository.assignImportLineage(id, importMode, sourceModelId, operator);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id, String operator) {
    Model existing = get(id);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MODELING_MODEL_DELETE",
                "Delete modeling model",
                "MODELING_MODEL",
                String.valueOf(id),
                existing.name(),
                "APPLICATION",
                Map.of()));
    try {
      audit.resource(String.valueOf(id), existing.name());
      if (!repository.deleteById(id, operator)) {
        throw new ModelingException(ModelingErrorCode.DELETE_FAILED);
      }
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_DELETED,
          "Modeling model moved to recycle bin",
          Map.of(),
          "模型已移入回收站");
    } catch (RuntimeException exception) {
      audit.failure("MODELING_MODEL_DELETE_FAILED", exception);
      throw exception;
    }
  }

  public PageData<Model> listDeleted(int pageNo, int pageSize, String keyword) {
    return repository.pageDeleted(pageNo, pageSize, keyword);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void restore(Long id, String operator) {
    Model recycled =
        repository
            .findDeletedById(id)
            .orElseThrow(
                () -> new ModelingException(ModelingErrorCode.NOT_FOUND, "回收站中不存在该模型：" + id));
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MODELING_MODEL_RESTORE",
                "Restore modeling model",
                "MODELING_MODEL",
                String.valueOf(id),
                recycled.name(),
                "APPLICATION",
                Map.of()));
    try {
      if (repository.existsByCode(recycled.code())) {
        throw new ModelingException(
            ModelingErrorCode.DUPLICATE_CODE, "编码已被存活模型占用，无法恢复：" + recycled.code());
      }
      if (!repository.restoreById(id, operator)) {
        throw new ModelingException(ModelingErrorCode.UPDATE_FAILED);
      }
      rehomeIfDirectoryMissing(id, recycled.directoryId(), operator);
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_UPDATED, "Modeling model restored", Map.of(), "模型已恢复");
    } catch (RuntimeException exception) {
      audit.failure("MODELING_MODEL_RESTORE_FAILED", exception);
      throw exception;
    }
  }

  /** 原目录可能已在模型入回收站期间被删除；此时回退为未分类，不阻塞恢复（DOMAIN.md）。 */
  private void rehomeIfDirectoryMissing(Long id, Long directoryId, String operator) {
    if (directoryId == null || directoryId <= 0) {
      return;
    }
    if (directoryRepository.findById(directoryId).isEmpty()) {
      repository.updateDirectory(id, null, operator);
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void purge(Long id) {
    Model recycled =
        repository
            .findDeletedById(id)
            .orElseThrow(
                () -> new ModelingException(ModelingErrorCode.NOT_FOUND, "回收站中不存在该模型：" + id));
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MODELING_MODEL_PURGE",
                "Purge modeling model",
                "MODELING_MODEL",
                String.valueOf(id),
                recycled.name(),
                "APPLICATION",
                Map.of()));
    try {
      tagRepository.replaceModelTags(id, null);
      if (!repository.purgeById(id)) {
        throw new ModelingException(ModelingErrorCode.DELETE_FAILED);
      }
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_DELETED,
          "Modeling model purged from recycle bin",
          Map.of(),
          "模型已彻底删除");
    } catch (RuntimeException exception) {
      audit.failure("MODELING_MODEL_PURGE_FAILED", exception);
      throw exception;
    }
  }

  private String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
