package io.yak.ops.business.modeling.logical;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingLogicalModelMapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingLogicalEntityMapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingLogicalAttributeMapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingEntityRelationMapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingLogicalModelVersionMapper;
import io.yak.ops.business.modeling.dao.model.LogicalModelPO;
import io.yak.ops.business.modeling.dao.model.LogicalEntityPO;
import io.yak.ops.business.modeling.dao.model.LogicalAttributePO;
import io.yak.ops.business.modeling.dao.model.LogicalRelationPO;
import io.yak.ops.business.modeling.dao.model.LogicalModelVersionPO;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.semantic.api.BusinessProcess;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Business-facing logical-model DRAFT authoring over the existing Modeling tables.
 * Source processes/standard fields remain owned by Semantic; no SQL/DDL is emitted.
 *
 * <p>The root logical model is Project-scoped; every child operation first validates
 * its root and its entity relationship. Legacy rows without project_id fail closed.
 */
@Service
@RequiredArgsConstructor
public class LogicalDraftService {
  private static final Pattern CODE = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,127}$");
  private final CurrentProject currentProject;
  private final ProcessApi processes;
  private final ModelingLogicalModelMapper models;
  private final ModelingLogicalEntityMapper entities;
  private final ModelingLogicalAttributeMapper attributes;
  private final ModelingEntityRelationMapper relations;
  private final ModelingLogicalModelVersionMapper versions;
  private final ObjectMapper json;

  public record NewDraft(Long processId, String code, String name, String description) {}
  public record NewEntity(String code, String name, String businessName, String description) {}
  public record NewAttribute(String code, String name, Long stdFieldId, String logicalType,
                             String description, Boolean primaryFlag, Boolean nullable) {}
  public record NewRelation(Long sourceEntityId, Long targetEntityId, String relationType,
                            String cardinality, String description) {}
  public record EntityView(LogicalEntityPO entity, List<LogicalAttributePO> attributes) {}
  public record DraftView(LogicalModelPO model, BusinessProcess process,
                          List<EntityView> entities, List<LogicalRelationPO> relations) {}
  public record VersionView(Long id, Integer versionNo, String status, String createdBy,
                            LocalDateTime createTime) {}

  private static ModelingException invalid(String message) {
    return new ModelingException(ModelingErrorCode.INVALID_COLUMN, message);
  }

  private static String requiredCode(String value) {
    if (value == null || !CODE.matcher(value.trim()).matches()) {
      throw invalid("编码只能使用字母开头的英文、数字、下划线（最多128字符）");
    }
    return value.trim();
  }

  private static String requiredName(String value) {
    if (!StringUtils.hasText(value) || value.trim().length() > 256) {
      throw invalid("名称不能为空，且不得超过256字符");
    }
    return value.trim();
  }

  private static String optional(String value, int limit, String name) {
    if (value != null && value.length() > limit) throw invalid(name + "长度超限");
    return value == null || value.isBlank() ? null : value.trim();
  }

  private BusinessProcess requireProcess(Long id) {
    if (id == null) throw invalid("请选择已授权的业务过程");
    return processes.listProcesses(null).stream().filter(p -> Objects.equals(p.id(), id))
        .findFirst().orElseThrow(() -> invalid("业务过程不存在或当前项目无访问权限"));
  }

  private LogicalModelPO requireModel(Long id, boolean lock) {
    Long projectId = currentProject.requireProjectId();
    LambdaQueryWrapper<LogicalModelPO> q = new LambdaQueryWrapper<LogicalModelPO>()
        .eq(LogicalModelPO::getId, id).eq(LogicalModelPO::getProjectId, projectId);
    if (lock) q.last("FOR UPDATE");
    LogicalModelPO model = models.selectOne(q);
    if (model == null) throw new ModelingException(ModelingErrorCode.NOT_FOUND,
        "逻辑模型不存在或不属于当前项目");
    return model;
  }

  private LogicalEntityPO requireEntity(Long modelId, Long entityId) {
    LogicalEntityPO entity = entities.selectById(entityId);
    if (entity == null || !Objects.equals(entity.getLogicalModelId(), modelId)) {
      throw invalid("逻辑实体不存在或不属于当前模型");
    }
    return entity;
  }

  public List<LogicalModelPO> list() {
    return models.selectList(new LambdaQueryWrapper<LogicalModelPO>()
        .eq(LogicalModelPO::getProjectId, currentProject.requireProjectId())
        .orderByDesc(LogicalModelPO::getUpdateTime).last("LIMIT 200"));
  }

  public DraftView get(Long id) {
    LogicalModelPO model = requireModel(id, false);
    BusinessProcess process = requireProcess(model.getProcessId());
    List<LogicalEntityPO> entityList = entities.selectList(new LambdaQueryWrapper<LogicalEntityPO>()
        .eq(LogicalEntityPO::getLogicalModelId, model.getId())
        .orderByAsc(LogicalEntityPO::getId));
    List<EntityView> result = entityList.stream().map(entity -> new EntityView(entity,
        attributes.selectList(new LambdaQueryWrapper<LogicalAttributePO>()
            .eq(LogicalAttributePO::getEntityId, entity.getId())
            .orderByAsc(LogicalAttributePO::getSort, LogicalAttributePO::getId)))).toList();
    Set<Long> entityIds = new HashSet<>();
    for (LogicalEntityPO entity : entityList) entityIds.add(entity.getId());
    List<LogicalRelationPO> mapped = entityIds.isEmpty() ? List.of()
        : relations.selectList(new LambdaQueryWrapper<LogicalRelationPO>()
          .in(LogicalRelationPO::getSourceEntityId, entityIds)
          .in(LogicalRelationPO::getTargetEntityId, entityIds)
          .orderByAsc(LogicalRelationPO::getId));
    return new DraftView(model, process, result, mapped);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DraftView create(NewDraft input, String actor) {
    BusinessProcess process = requireProcess(input.processId());
    String code = requiredCode(input.code());
    if (models.selectCount(new LambdaQueryWrapper<LogicalModelPO>()
        .eq(LogicalModelPO::getProjectId, currentProject.requireProjectId())
        .eq(LogicalModelPO::getCode, code)) > 0) {
      throw invalid("当前项目已存在相同逻辑模型编码");
    }
    LocalDateTime now = LocalDateTime.now();
    LogicalModelPO root = new LogicalModelPO();
    root.setProjectId(currentProject.requireProjectId());
    root.setProcessId(process.id());
    root.setDomainId(process.domainId());
    root.setCode(code);
    root.setName(requiredName(input.name()));
    root.setDescription(optional(input.description(), 1024, "模型描述"));
    root.setOwner(actor);
    root.setStatus("DRAFT");
    root.setCreateTime(now);
    root.setUpdateTime(now);
    models.insert(root);
    // A user-approved operation creates just the container: never infer
    // order↔detail relations, uniqueness, grain, or auto-publish semantics.
    return get(root.getId());
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DraftView addEntity(Long modelId, NewEntity input, String actor) {
    LogicalModelPO root = requireModel(modelId, true);
    String code = requiredCode(input.code());
    if (entities.selectCount(new LambdaQueryWrapper<LogicalEntityPO>()
        .eq(LogicalEntityPO::getLogicalModelId, modelId)
        .eq(LogicalEntityPO::getCode, code)) > 0) throw invalid("实体编码在逻辑模型内重复");
    LogicalEntityPO entity = new LogicalEntityPO();
    entity.setLogicalModelId(modelId);
    entity.setCode(code);
    entity.setName(requiredName(input.name()));
    entity.setBusinessName(optional(input.businessName(), 256, "业务名称"));
    entity.setDescription(optional(input.description(), 1024, "实体描述"));
    entity.setOwner(actor);
    entity.setStatus("DRAFT");
    entity.setCreateTime(LocalDateTime.now());
    entity.setUpdateTime(LocalDateTime.now());
    entities.insert(entity);
    touch(root);
    return get(modelId);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DraftView updateEntity(Long modelId, Long entityId, NewEntity input) {
    LogicalModelPO root = requireModel(modelId, true);
    LogicalEntityPO entity = requireEntity(modelId, entityId);
    if (!Objects.equals(entity.getCode(), requiredCode(input.code()))) {
      throw invalid("逻辑实体编码不可更改");
    }
    entity.setName(requiredName(input.name()));
    entity.setBusinessName(optional(input.businessName(), 256, "业务名称"));
    entity.setDescription(optional(input.description(), 1024, "实体描述"));
    entity.setUpdateTime(LocalDateTime.now());
    entities.updateById(entity);
    touch(root);
    return get(modelId);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DraftView addAttribute(Long modelId, Long entityId, NewAttribute input) {
    LogicalModelPO root = requireModel(modelId, true);
    requireEntity(modelId, entityId);
    String code = requiredCode(input.code());
    if (attributes.selectCount(new LambdaQueryWrapper<LogicalAttributePO>()
        .eq(LogicalAttributePO::getEntityId, entityId)
        .eq(LogicalAttributePO::getCode, code)) > 0) throw invalid("实体内属性编码重复");
    StandardField field = null;
    if (input.stdFieldId() != null) {
      field = processes.getField(input.stdFieldId());
      if (field == null || !field.isEnabled()) throw invalid("标准字段不可用或已停用");
    }
    LogicalAttributePO attribute = new LogicalAttributePO();
    attribute.setEntityId(entityId);
    attribute.setCode(code);
    attribute.setName(requiredName(input.name()));
    attribute.setStdFieldId(field == null ? null : field.id());
    attribute.setLogicalType(optional(input.logicalType(), 128, "逻辑类型"));
    attribute.setDescription(optional(input.description(), 1024, "属性描述"));
    attribute.setPrimaryFlag(Boolean.TRUE.equals(input.primaryFlag()));
    attribute.setNullable(!Boolean.FALSE.equals(input.nullable()));
    attribute.setSort(0);
    attributes.insert(attribute);
    touch(root);
    return get(modelId);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DraftView updateAttribute(Long modelId, Long entityId, Long attributeId, NewAttribute input) {
    LogicalModelPO root = requireModel(modelId, true);
    requireEntity(modelId, entityId);
    LogicalAttributePO attribute = attributes.selectById(attributeId);
    if (attribute == null || !Objects.equals(attribute.getEntityId(), entityId)) {
      throw invalid("逻辑属性不属于当前模型实体");
    }
    if (!Objects.equals(attribute.getCode(), requiredCode(input.code()))) {
      throw invalid("逻辑属性编码不可更改");
    }
    if (input.stdFieldId() != null) {
      StandardField field = processes.getField(input.stdFieldId());
      if (field == null || !field.isEnabled()) throw invalid("标准字段不可用或已停用");
    }
    attribute.setName(requiredName(input.name()));
    attribute.setStdFieldId(input.stdFieldId());
    attribute.setLogicalType(optional(input.logicalType(), 128, "逻辑类型"));
    attribute.setDescription(optional(input.description(), 1024, "属性描述"));
    attribute.setPrimaryFlag(Boolean.TRUE.equals(input.primaryFlag()));
    attribute.setNullable(!Boolean.FALSE.equals(input.nullable()));
    attributes.updateById(attribute);
    touch(root);
    return get(modelId);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DraftView addRelation(Long modelId, NewRelation input) {
    LogicalModelPO root = requireModel(modelId, true);
    requireEntity(modelId, input.sourceEntityId());
    requireEntity(modelId, input.targetEntityId());
    if (Objects.equals(input.sourceEntityId(), input.targetEntityId())) {
      throw invalid("自关联必须由后续专业建模合同明确，不在首次业务草稿自动建立");
    }
    if (!Set.of("ONE_TO_ONE", "ONE_TO_MANY", "MANY_TO_ONE", "MANY_TO_MANY", "UNKNOWN")
        .contains(input.cardinality() == null ? "" : input.cardinality())) {
      throw invalid("请选择已确认的关系基数，或显式标记 UNKNOWN");
    }
    LogicalRelationPO relation = new LogicalRelationPO();
    relation.setSourceEntityId(input.sourceEntityId());
    relation.setTargetEntityId(input.targetEntityId());
    relation.setRelationType("ASSOCIATION");
    relation.setCardinality(input.cardinality());
    relation.setDescription(optional(input.description(), 1024, "关系描述"));
    relations.insert(relation);
    touch(root);
    return get(modelId);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DraftView updateRelation(Long modelId, Long relationId, NewRelation input) {
    LogicalModelPO root = requireModel(modelId, true);
    LogicalRelationPO relation = relations.selectById(relationId);
    if (relation == null) throw invalid("逻辑关系不存在");
    requireEntity(modelId, relation.getSourceEntityId());
    requireEntity(modelId, relation.getTargetEntityId());
    if (!Objects.equals(relation.getSourceEntityId(), input.sourceEntityId())
        || !Objects.equals(relation.getTargetEntityId(), input.targetEntityId())) {
      throw invalid("已有关系不可重绑实体");
    }
    if (!Set.of("ONE_TO_ONE", "ONE_TO_MANY", "MANY_TO_ONE", "MANY_TO_MANY", "UNKNOWN")
        .contains(input.cardinality() == null ? "" : input.cardinality())) {
      throw invalid("请选择关系基数或 UNKNOWN");
    }
    relation.setCardinality(input.cardinality());
    relation.setDescription(optional(input.description(), 1024, "关系描述"));
    relations.updateById(relation);
    touch(root);
    return get(modelId);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public VersionView freezeDraft(Long modelId, String actor) {
    LogicalModelPO root = requireModel(modelId, true);
    DraftView current = get(modelId);
    if (current.entities().isEmpty() || current.entities().stream().allMatch(e -> e.attributes().isEmpty())) {
      throw invalid("逻辑模型至少需要一个实体和一项属性，才能保存独立快照");
    }
    Integer previous = versions.selectList(new LambdaQueryWrapper<LogicalModelVersionPO>()
        .eq(LogicalModelVersionPO::getModelId, modelId)
        .orderByDesc(LogicalModelVersionPO::getVersionNo).last("LIMIT 1"))
        .stream().map(LogicalModelVersionPO::getVersionNo).findFirst().orElse(0);
    LogicalModelVersionPO version = new LogicalModelVersionPO();
    version.setModelId(root.getId());
    version.setVersionNo(previous + 1);
    version.setStatus("DRAFT"); // Independent saved design snapshot, NOT published/deployed.
    try {
      version.setSnapshot(json.writeValueAsString(current));
    } catch (JsonProcessingException ex) {
      throw invalid("无法冻结逻辑模型快照");
    }
    version.setCreatedBy(actor);
    version.setCreateTime(LocalDateTime.now());
    version.setUpdateTime(LocalDateTime.now());
    versions.insert(version);
    return toVersion(version);
  }

  public List<VersionView> versions(Long modelId) {
    requireModel(modelId, false);
    return versions.selectList(new LambdaQueryWrapper<LogicalModelVersionPO>()
        .eq(LogicalModelVersionPO::getModelId, modelId)
        .orderByDesc(LogicalModelVersionPO::getVersionNo))
        .stream().map(this::toVersion).toList();
  }

  public String versionSnapshot(Long modelId, int number) {
    requireModel(modelId, false);
    LogicalModelVersionPO po = versions.selectOne(new LambdaQueryWrapper<LogicalModelVersionPO>()
        .eq(LogicalModelVersionPO::getModelId, modelId)
        .eq(LogicalModelVersionPO::getVersionNo, number));
    if (po == null) throw new ModelingException(ModelingErrorCode.NOT_FOUND, "未找到独立逻辑设计版本");
    return po.getSnapshot();
  }

  private VersionView toVersion(LogicalModelVersionPO po) {
    return new VersionView(po.getId(), po.getVersionNo(), po.getStatus(),
        po.getCreatedBy(), po.getCreateTime());
  }

  private void touch(LogicalModelPO root) {
    root.setUpdateTime(LocalDateTime.now());
    models.updateById(root);
  }
}
