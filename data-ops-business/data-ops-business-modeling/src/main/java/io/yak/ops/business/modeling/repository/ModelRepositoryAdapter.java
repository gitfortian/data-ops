package io.yak.ops.business.modeling.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.dao.model.ModelingModelPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * MyBatis adapter for the modeling catalog. Every read and write is bound to
 * the project space from the trusted server-side context (PROJECT_SCOPE).
 * Live-row semantics: deleted=0 rows are the only visible/quotable models.
 */
@Repository
public class ModelRepositoryAdapter implements ModelRepository {

  /** Stored value for "no directory" (uncategorized). */
  public static final long UNCATEGORIZED_DIRECTORY_ID = 0L;

  private final ModelingModelMapper mapper;
  private final ModelTagRepository tagRepository;
  private final CurrentProject currentProject;

  @Autowired
  public ModelRepositoryAdapter(
      ModelingModelMapper mapper, ModelTagRepository tagRepository, CurrentProject currentProject) {
    this.mapper = mapper;
    this.tagRepository = tagRepository;
    this.currentProject = currentProject;
  }

  /** Compatibility constructor for focused tests; project-scoped operations will fail closed. */
  public ModelRepositoryAdapter(ModelingModelMapper mapper) {
    this(mapper, null, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public Model insert(Model model, String operator) {
    Long projectId = requiredProjectId();
    LocalDateTime now = LocalDateTime.now();
    ModelingModelPO po = new ModelingModelPO();
    po.setProjectId(projectId);
    po.setModelCode(model.code());
    po.setModelName(model.name());
    po.setDialect(model.dialect().name());
    po.setDescription(model.description());
    po.setStatus(model.status().name());
    po.setDirectoryId(toStoredDirectoryId(model.directoryId()));
    po.setLayerCode(model.layerCode());
    po.setProcessId(model.processId());
    po.setDomainId(model.domainId());
    po.setCreatedBy(operator);
    po.setUpdatedBy(operator);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    po.setDeleted(Boolean.FALSE);
    mapper.insert(po);
    return model.withPersisted(po.getId(), operator, now, now);
  }

  @Override
  public Optional<Model> findById(Long id) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<ModelingModelPO>()
                    .eq(ModelingModelPO::getId, id)
                    .eq(ModelingModelPO::getProjectId, projectId)
                    .eq(ModelingModelPO::getDeleted, Boolean.FALSE)))
        .map(po -> toDomain(po, tagRepository.tagIdsForModel(po.getId())));
  }

  @Override
  public Optional<Model> findDeletedById(Long id) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<ModelingModelPO>()
                    .eq(ModelingModelPO::getId, id)
                    .eq(ModelingModelPO::getProjectId, projectId)
                    .eq(ModelingModelPO::getDeleted, Boolean.TRUE)))
        .map(po -> toDomain(po, null));
  }

  @Override
  public boolean existsByCode(String code) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<ModelingModelPO>()
                .eq(ModelingModelPO::getProjectId, projectId)
                .eq(ModelingModelPO::getModelCode, code)
                .eq(ModelingModelPO::getDeleted, Boolean.FALSE))
        > 0L;
  }

  @Override
  public Optional<Model> findByCode(String code) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<ModelingModelPO>()
                    .eq(ModelingModelPO::getProjectId, projectId)
                    .eq(ModelingModelPO::getModelCode, code)
                    .eq(ModelingModelPO::getDeleted, Boolean.FALSE)))
        .map(po -> toDomain(po, null));
  }

  @Override
  public PageData<Model> page(
      int pageNo, int pageSize, String keyword, Long directoryId, List<Long> tagIds,
      String layerCode, Long processId, java.util.List<Long> processIds, String status,
      Long domainId) {
    Long projectId = requiredProjectId();
    List<Long> taggedModelIds = tagIds == null || tagIds.isEmpty()
        ? null
        : tagRepository.modelIdsByTagIds(tagIds);
    if (taggedModelIds != null && taggedModelIds.isEmpty()) {
      return emptyPage(pageNo, pageSize);
    }
    Page<ModelingModelPO> page = Page.of(Math.max(1, pageNo), Math.max(1, pageSize));
    LambdaQueryWrapper<ModelingModelPO> wrapper =
        queryWrapper(projectId, keyword, directoryId, taggedModelIds)
            .eq(ModelingModelPO::getDeleted, Boolean.FALSE);
    if (layerCode != null && !layerCode.isBlank()) {
      wrapper.eq(ModelingModelPO::getLayerCode, layerCode);
    }
    if (processId != null) {
      wrapper.eq(ModelingModelPO::getProcessId, processId);
    } else if (processIds != null && !processIds.isEmpty()) {
      wrapper.in(ModelingModelPO::getProcessId, processIds);
    }
    if (status != null && !status.isBlank()) {
      wrapper.eq(ModelingModelPO::getStatus, status);
    }
    if (domainId != null) {
      wrapper.eq(ModelingModelPO::getDomainId, domainId);
    }
    Page<ModelingModelPO> result = mapper.selectPage(page, wrapper);
    Map<Long, List<Long>> tagIdsByModel =
        tagRepository.tagIdsForModels(
            result.getRecords().stream().map(ModelingModelPO::getId).toList());
    List<Model> records =
        result.getRecords().stream()
            .map(po -> toDomain(po, tagIdsByModel.get(po.getId())))
            .toList();
    return new PageData<>(
        records, result.getTotal(), result.getPages(), result.getCurrent(), result.getSize());
  }

  @Override
  public PageData<Model> pageDeleted(int pageNo, int pageSize, String keyword) {
    Long projectId = requiredProjectId();
    Page<ModelingModelPO> page = Page.of(Math.max(1, pageNo), Math.max(1, pageSize));
    LambdaQueryWrapper<ModelingModelPO> wrapper =
        queryWrapper(projectId, keyword, null, null).eq(ModelingModelPO::getDeleted, Boolean.TRUE);
    Page<ModelingModelPO> result = mapper.selectPage(page, wrapper);
    List<Model> records =
        result.getRecords().stream().map(po -> toDomain(po, null)).toList();
    return new PageData<>(
        records, result.getTotal(), result.getPages(), result.getCurrent(), result.getSize());
  }

  @Override
  public boolean deleteById(Long id, String operator) {
    Long projectId = requiredProjectId();
    LocalDateTime now = LocalDateTime.now();
    // 软删除同时改写编码（原始编码留存 original_code），为同码重建腾位：
    // 存活行的 (project_id, model_code) 唯一键因此可以常驻 DB 兜底（V6）。
    return mapper.update(
            null,
            new LambdaUpdateWrapper<ModelingModelPO>()
                .eq(ModelingModelPO::getId, id)
                .eq(ModelingModelPO::getProjectId, projectId)
                .eq(ModelingModelPO::getDeleted, Boolean.FALSE)
                .set(ModelingModelPO::getDeleted, Boolean.TRUE)
                .set(ModelingModelPO::getDeletedBy, operator)
                .set(ModelingModelPO::getDeletedTime, now)
                .set(StringUtils.hasText(operator), ModelingModelPO::getUpdatedBy, operator)
                .set(ModelingModelPO::getUpdateTime, now)
                .setSql("original_code = model_code, model_code = CONCAT(model_code, '__del__', id)"))
        > 0;
  }

  @Override
  public boolean restoreById(Long id, String operator) {
    Long projectId = requiredProjectId();
    return mapper.update(
            null,
            withUpdater(
                new LambdaUpdateWrapper<ModelingModelPO>()
                    .eq(ModelingModelPO::getId, id)
                    .eq(ModelingModelPO::getProjectId, projectId)
                    .eq(ModelingModelPO::getDeleted, Boolean.TRUE)
                    .set(ModelingModelPO::getDeleted, Boolean.FALSE)
                    .set(ModelingModelPO::getDeletedBy, null)
                    .set(ModelingModelPO::getDeletedTime, null)
                    .set(ModelingModelPO::getUpdateTime, LocalDateTime.now()),
                operator)
                .setSql("model_code = original_code, original_code = NULL"))
        > 0;
  }

  @Override
  public boolean purgeById(Long id) {
    Long projectId = requiredProjectId();
    return mapper.delete(
            new LambdaQueryWrapper<ModelingModelPO>()
                .eq(ModelingModelPO::getId, id)
                .eq(ModelingModelPO::getProjectId, projectId)
                .eq(ModelingModelPO::getDeleted, Boolean.TRUE))
        > 0;
  }

  @Override
  public boolean updateDirectory(Long id, Long directoryId, String operator) {
    return mapper.update(
            null,
            liveUpdate(id, operator).set(ModelingModelPO::getDirectoryId, toStoredDirectoryId(directoryId)))
        > 0;
  }

  @Override
  public boolean updateDomain(Long id, Long domainId, String operator) {
    return mapper.update(null, liveUpdate(id, operator).set(ModelingModelPO::getDomainId, domainId))
        > 0;
  }

  @Override
  public boolean updateBasics(
      Long id, String name, String dialect, String description, String operator) {
    return mapper.update(
            null,
            liveUpdate(id, operator)
                .set(ModelingModelPO::getModelName, name)
                .set(ModelingModelPO::getDialect, dialect)
                .set(ModelingModelPO::getDescription, description))
        > 0;
  }

  @Override
  public long countByDirectory(Long directoryId) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
        new LambdaQueryWrapper<ModelingModelPO>()
            .eq(ModelingModelPO::getProjectId, projectId)
            .eq(ModelingModelPO::getDirectoryId, toStoredDirectoryId(directoryId))
            .eq(ModelingModelPO::getDeleted, Boolean.FALSE));
  }

  private PageData<Model> emptyPage(int pageNo, int pageSize) {
    return new PageData<>(
        List.of(), 0L, 0L, (long) Math.max(1, pageNo), (long) Math.max(1, pageSize));
  }

  private LambdaQueryWrapper<ModelingModelPO> queryWrapper(
      Long projectId, String keyword, Long directoryId, List<Long> taggedModelIds) {
    LambdaQueryWrapper<ModelingModelPO> wrapper =
        new LambdaQueryWrapper<ModelingModelPO>().eq(ModelingModelPO::getProjectId, projectId);
    if (directoryId != null) {
      wrapper.eq(ModelingModelPO::getDirectoryId, toStoredDirectoryId(directoryId));
    }
    if (taggedModelIds != null) {
      wrapper.in(ModelingModelPO::getId, taggedModelIds);
    }
    if (StringUtils.hasText(keyword)) {
      String trimmed = keyword.trim();
      wrapper.and(inner ->
          inner.like(ModelingModelPO::getModelName, trimmed)
              .or()
              .like(ModelingModelPO::getModelCode, trimmed));
    }
    return wrapper.orderByDesc(ModelingModelPO::getUpdateTime).orderByDesc(ModelingModelPO::getId);
  }

  @Override
  public boolean assignProcess(Long modelId, Long processId, String layerCode, String operator) {
    // 显式 set：编辑弹窗清空分层/业务过程时必须写 NULL（PO 更新会跳过 null 字段）
    return mapper.update(
            null,
            liveUpdate(modelId, operator)
                .set(ModelingModelPO::getProcessId, processId)
                .set(ModelingModelPO::getLayerCode, layerCode))
        > 0;
  }

  @Override
  public boolean assignLayer(Long modelId, String layerCode, String operator) {
    return mapper.update(null, liveUpdate(modelId, operator).set(ModelingModelPO::getLayerCode, layerCode))
        > 0;
  }

  @Override
  public List<Long> modelIdsByProcess(Long processId) {
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<ModelingModelPO>()
                .eq(ModelingModelPO::getProjectId, projectId)
                .eq(ModelingModelPO::getProcessId, processId)
                .eq(ModelingModelPO::getDeleted, Boolean.FALSE))
        .stream()
        .map(ModelingModelPO::getId)
        .toList();
  }

  @Override
  public List<Model> listBySource(Long sourceDatasourceId, String sourceTable) {
    if (sourceDatasourceId == null || !StringUtils.hasText(sourceTable)) {
      return List.of();
    }
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<ModelingModelPO>()
                .eq(ModelingModelPO::getProjectId, projectId)
                .eq(ModelingModelPO::getSourceDatasourceId, sourceDatasourceId)
                .eq(ModelingModelPO::getSourceTable, sourceTable.trim())
                .eq(ModelingModelPO::getDeleted, Boolean.FALSE))
        .stream()
        .map(po -> toDomain(po, null))
        .toList();
  }

  @Override
  public boolean assignSource(
      Long modelId, Long sourceDatasourceId, String sourceDatabase, String sourceTable,
      String operator) {
    if (sourceDatasourceId == null || !StringUtils.hasText(sourceTable)) {
      return false;
    }
    Long projectId = requiredProjectId();
    ModelingModelPO po = new ModelingModelPO();
    po.setSourceDatasourceId(sourceDatasourceId);
    po.setSourceDatabase(StringUtils.hasText(sourceDatabase) ? sourceDatabase.trim() : null);
    po.setSourceTable(sourceTable.trim());
    po.setUpdatedBy(operator);
    po.setUpdateTime(LocalDateTime.now());
    return mapper.update(
            po,
            new LambdaQueryWrapper<ModelingModelPO>()
                .eq(ModelingModelPO::getId, modelId)
                .eq(ModelingModelPO::getProjectId, projectId)
                .eq(ModelingModelPO::getDeleted, Boolean.FALSE))
        > 0;
  }

  @Override
  public boolean assignImportLineage(Long modelId, String importMode, Long sourceModelId, String operator) {
    Long projectId = requiredProjectId();
    ModelingModelPO po = new ModelingModelPO();
    po.setImportMode(importMode);
    po.setSourceModelId(sourceModelId);
    po.setUpdatedBy(operator);
    po.setUpdateTime(LocalDateTime.now());
    return mapper.update(
            po,
            new LambdaQueryWrapper<ModelingModelPO>()
                .eq(ModelingModelPO::getId, modelId)
                .eq(ModelingModelPO::getProjectId, projectId)
                .eq(ModelingModelPO::getDeleted, Boolean.FALSE))
        > 0;
  }

  @Override
  public List<Model> listByProcessLayer(Long processId, String layerCode) {
    if (processId == null || layerCode == null || layerCode.isBlank()) {
      return List.of();
    }
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<ModelingModelPO>()
                .eq(ModelingModelPO::getProjectId, projectId)
                .eq(ModelingModelPO::getProcessId, processId)
                .eq(ModelingModelPO::getLayerCode, layerCode.trim())
                .eq(ModelingModelPO::getDeleted, Boolean.FALSE)
                .orderByAsc(ModelingModelPO::getId))
        .stream()
        .map(po -> toDomain(po, null))
        .toList();
  }

  @Override
  public boolean assignAggregateMeta(
      Long modelId, String statPeriod, String appCode, String appName, String operator) {
    Long projectId = requiredProjectId();
    ModelingModelPO po = new ModelingModelPO();
    po.setStatPeriod(StringUtils.hasText(statPeriod) ? statPeriod.trim() : null);
    po.setAppCode(StringUtils.hasText(appCode) ? appCode.trim() : null);
    po.setAppName(StringUtils.hasText(appName) ? appName.trim() : null);
    po.setUpdatedBy(operator);
    po.setUpdateTime(LocalDateTime.now());
    return mapper.update(
            po,
            new LambdaQueryWrapper<ModelingModelPO>()
                .eq(ModelingModelPO::getId, modelId)
                .eq(ModelingModelPO::getProjectId, projectId)
                .eq(ModelingModelPO::getDeleted, Boolean.FALSE))
        > 0;
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private Long toStoredDirectoryId(Long directoryId) {
    return directoryId == null || directoryId <= 0L ? UNCATEGORIZED_DIRECTORY_ID : directoryId;
  }

  private static Model toDomain(ModelingModelPO po, List<Long> tagIds) {
    return new Model(
        po.getId(),
        // 软删除行的存储编码已被改写腾位，对外一律呈现原始编码。
        po.getOriginalCode() != null ? po.getOriginalCode() : po.getModelCode(),
        po.getModelName(),
        ModelDialect.fromStored(po.getDialect()).orElse(null),
        po.getDescription(),
        ModelStatus.valueOf(po.getStatus()),
        po.getCreatedBy(),
        po.getCreateTime(),
        po.getUpdateTime(),
        po.getLayerCode(),
        po.getProcessId(),
        storedToDomainDirectoryId(po.getDirectoryId()),
        tagIds,
        po.getDeletedBy(),
        po.getDeletedTime(),
        po.getSourceDatasourceId(),
        po.getSourceDatabase(),
        po.getSourceTable(),
        po.getStatPeriod(),
        po.getAppCode(),
        po.getAppName(),
        po.getImportMode(),
        po.getSourceModelId(),
        po.getPublishedVersionId(),
        po.getLatestVersionNo() == null ? 0 : po.getLatestVersionNo(),
        po.getDomainId(),
        po.getUpdatedBy());
  }

  private static Long storedToDomainDirectoryId(Long directoryId) {
    return directoryId == null || directoryId == UNCATEGORIZED_DIRECTORY_ID ? null : directoryId;
  }

  @Override
  public boolean updatePublishState(
      Long modelId, String status, Long publishedVersionId, int latestVersionNo, String operator) {
    return mapper.update(
            null,
            liveUpdate(modelId, operator)
                .set(ModelingModelPO::getStatus, status)
                .set(ModelingModelPO::getPublishedVersionId, publishedVersionId)
                .set(ModelingModelPO::getLatestVersionNo, latestVersionNo))
        > 0;
  }

  @Override
  public boolean updateStatus(Long modelId, String status, String operator) {
    return mapper.update(null, liveUpdate(modelId, operator).set(ModelingModelPO::getStatus, status))
        > 0;
  }

  /**
   * 存活模型行的统一更新入口：项目隔离条件 + 更新时间 + 最后更新人一起给。
   * operator 为空（无请求人上下文）时不动 updated_by，避免把已知的更新人写成未知。
   */
  private LambdaUpdateWrapper<ModelingModelPO> liveUpdate(Long id, String operator) {
    return withUpdater(
        new LambdaUpdateWrapper<ModelingModelPO>()
            .eq(ModelingModelPO::getId, id)
            .eq(ModelingModelPO::getProjectId, requiredProjectId())
            .eq(ModelingModelPO::getDeleted, Boolean.FALSE)
            .set(ModelingModelPO::getUpdateTime, LocalDateTime.now()),
        operator);
  }

  private static LambdaUpdateWrapper<ModelingModelPO> withUpdater(
      LambdaUpdateWrapper<ModelingModelPO> wrapper, String operator) {
    return wrapper.set(StringUtils.hasText(operator), ModelingModelPO::getUpdatedBy, operator);
  }
}
