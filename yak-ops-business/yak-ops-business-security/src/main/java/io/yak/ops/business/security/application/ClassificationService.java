package io.yak.ops.business.security.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.security.api.ClassificationView;
import io.yak.ops.business.security.api.SecurityClassificationQueryApi;
import io.yak.ops.business.security.domain.LevelCount;
import io.yak.ops.business.security.dao.mapper.ClassificationMapper;
import io.yak.ops.business.security.dao.mapper.DataCategoryMapper;
import io.yak.ops.business.security.dao.mapper.SecurityLevelMapper;
import io.yak.ops.business.security.exception.SecurityException;
import io.yak.ops.business.security.support.audit.SecurityAudit;
import io.yak.ops.common.bean.po.security.DsecClassificationPO;
import io.yak.ops.common.bean.po.security.DsecDataCategoryPO;
import io.yak.ops.common.bean.po.security.DsecSecurityLevelPO;
import io.yak.ops.common.enums.security.SecurityErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 资产分级标签服务:把数据对象绑定到等级/分类,并作为分级查询 SPI 对下游暴露。
 */
@Component
public class ClassificationService implements SecurityClassificationQueryApi {

  private final ClassificationMapper mapper;
  private final SecurityLevelMapper levelMapper;
  private final DataCategoryMapper categoryMapper;
  private final CurrentProject currentProject;
  private final BusinessAuditService auditService;

  public ClassificationService(
      ClassificationMapper mapper,
      SecurityLevelMapper levelMapper,
      DataCategoryMapper categoryMapper,
      CurrentProject currentProject,
      BusinessAuditService auditService) {
    this.mapper = mapper;
    this.levelMapper = levelMapper;
    this.categoryMapper = categoryMapper;
    this.currentProject = currentProject;
    this.auditService = auditService;
  }

  /** 规范化对象自然键: {@code type:dsId:db.table.column},缺段以 - 占位。 */
  public static String objectKey(
      String objectType, Long datasourceId, String dbName, String tableName, String columnName) {
    return String.join(
        ":",
        safe(objectType),
        datasourceId == null ? "-" : String.valueOf(datasourceId),
        safe(dbName),
        safe(tableName),
        safe(columnName));
  }

  private static String safe(String v) {
    return StringUtils.hasText(v) ? v.trim() : "-";
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DsecClassificationPO upsert(
      String objectType,
      Long datasourceId,
      String dbName,
      String tableName,
      String columnName,
      Long levelId,
      Long categoryId,
      String source,
      Integer confidence,
      Long discoveryRuleId,
      String status,
      String objectName,
      String operator) {
    if (!StringUtils.hasText(objectType) || levelId == null) {
      throw new SecurityException(SecurityErrorCode.CLASSIFICATION_INVALID_OBJECT, "对象类型与等级必填");
    }
    Long projectId = currentProject.requireProjectId();
    DsecSecurityLevelPO level = levelMapper.selectOne(scopedLevel(projectId, levelId));
    if (level == null) {
      throw new SecurityException(SecurityErrorCode.CLASSIFICATION_INVALID_LEVEL, String.valueOf(levelId));
    }
    if (categoryId != null && categoryMapper.selectOne(scopedCategory(projectId, categoryId)) == null) {
      throw new SecurityException(SecurityErrorCode.CLASSIFICATION_INVALID_CATEGORY, String.valueOf(categoryId));
    }
    String key = objectKey(objectType, datasourceId, dbName, tableName, columnName);
    String resolvedStatus = StringUtils.hasText(status) ? status : "ACTIVE";
    LocalDateTime now = LocalDateTime.now();
    DsecClassificationPO existing = findByKey(key);
    return SecurityAudit.tx(
        auditService,
        existing == null ? AuditEventType.RESOURCE_CREATED : AuditEventType.RESOURCE_UPDATED,
        SecurityAudit.request(
            "CLASSIFICATION_UPSERT", "Upsert classification", "CLASSIFICATION", key, objectName),
        () -> {
          if (existing == null) {
            DsecClassificationPO po = new DsecClassificationPO();
            po.setProjectId(projectId);
            po.setObjectType(objectType);
            po.setObjectKey(key);
            po.setDatasourceId(datasourceId);
            po.setDbName(safe(dbName));
            po.setTableName(safe(tableName));
            po.setColumnName(safe(columnName));
            po.setObjectName(objectName);
            po.setSource(StringUtils.hasText(source) ? source : "MANUAL");
            po.setConfidence(confidence == null ? 100 : confidence);
            po.setDiscoveryRuleId(discoveryRuleId);
            po.setCreateTime(now);
            applyLabels(po, levelId, categoryId, resolvedStatus, operator, now);
            mapper.insert(po);
            return po;
          }
          applyLabels(existing, levelId, categoryId, resolvedStatus, null, now);
          if (StringUtils.hasText(source)) {
            existing.setSource(source);
          }
          if (confidence != null) {
            existing.setConfidence(confidence);
          }
          existing.setDiscoveryRuleId(discoveryRuleId);
          mapper.updateById(existing);
          return existing;
        });
  }

  private void applyLabels(
      DsecClassificationPO po, Long levelId, Long categoryId, String status, String operator, LocalDateTime now) {
    po.setLevelId(levelId);
    po.setCategoryId(categoryId);
    po.setStatus(status);
    po.setUpdateTime(now);
    if (operator != null) {
      po.setCreatedBy(operator);
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DsecClassificationPO changeStatus(Long id, String status) {
    DsecClassificationPO po = get(id);
    if (!List.of("CANDIDATE", "ACTIVE", "REJECTED").contains(status)) {
      throw new SecurityException(SecurityErrorCode.CLASSIFICATION_INVALID_STATUS, status);
    }
    SecurityAudit.tx(
        auditService,
        AuditEventType.RESOURCE_UPDATED,
        SecurityAudit.request("CLASSIFICATION_STATUS", "Change classification status", "CLASSIFICATION",
            String.valueOf(id), po.getObjectKey()),
        () -> {
          po.setStatus(status);
          po.setUpdateTime(LocalDateTime.now());
          mapper.updateById(po);
          return Boolean.TRUE;
        });
    return po;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    DsecClassificationPO po = get(id);
    SecurityAudit.tx(
        auditService,
        AuditEventType.RESOURCE_DELETED,
        SecurityAudit.request("CLASSIFICATION_DELETE", "Delete classification", "CLASSIFICATION",
            String.valueOf(id), po.getObjectKey()),
        () -> {
          mapper.deleteById(id);
          return Boolean.TRUE;
        });
  }

  public DsecClassificationPO get(Long id) {
    DsecClassificationPO po = mapper.selectOne(
        new LambdaQueryWrapper<DsecClassificationPO>()
            .eq(DsecClassificationPO::getId, id)
            .eq(DsecClassificationPO::getProjectId, currentProject.requireProjectId()));
    if (po == null) {
      throw new SecurityException(SecurityErrorCode.CLASSIFICATION_NOT_FOUND, String.valueOf(id));
    }
    return po;
  }

  public PageData<DsecClassificationPO> page(
      int pageNo, int pageSize, String keyword, Long levelId, Long categoryId, String status) {
    Long projectId = currentProject.requireProjectId();
    LambdaQueryWrapper<DsecClassificationPO> wrapper =
        new LambdaQueryWrapper<DsecClassificationPO>().eq(DsecClassificationPO::getProjectId, projectId);
    if (StringUtils.hasText(keyword)) {
      wrapper.and(c -> c.like(DsecClassificationPO::getObjectKey, keyword.trim())
          .or().like(DsecClassificationPO::getObjectName, keyword.trim()));
    }
    if (levelId != null) {
      wrapper.eq(DsecClassificationPO::getLevelId, levelId);
    }
    if (categoryId != null) {
      wrapper.eq(DsecClassificationPO::getCategoryId, categoryId);
    }
    if (StringUtils.hasText(status)) {
      wrapper.eq(DsecClassificationPO::getStatus, status);
    }
    wrapper.orderByDesc(DsecClassificationPO::getUpdateTime).orderByDesc(DsecClassificationPO::getId);
    Page<DsecClassificationPO> page = Page.of(Math.max(1, pageNo), Math.max(1, pageSize));
    var result = mapper.selectPage(page, wrapper);
    return new PageData<>(
        result.getRecords(), result.getTotal(), result.getPages(), (long) pageNo, (long) pageSize);
  }

  // ==== SecurityClassificationQueryApi ====

  @Override
  public ClassificationView find(String objectKey) {
    DsecClassificationPO po = findByKey(objectKey);
    return po == null ? null : toView(po);
  }

  @Override
  public Map<String, ClassificationView> findMany(Collection<String> objectKeys) {
    Map<String, ClassificationView> map = new LinkedHashMap<>();
    if (objectKeys == null || objectKeys.isEmpty()) {
      return map;
    }
    Long projectId = currentProject.requireProjectId();
    List<DsecClassificationPO> rows = mapper.selectList(
        new LambdaQueryWrapper<DsecClassificationPO>()
            .eq(DsecClassificationPO::getProjectId, projectId)
            .in(DsecClassificationPO::getObjectKey, objectKeys));
    for (DsecClassificationPO row : rows) {
      map.put(row.getObjectKey(), toView(row));
    }
    return map;
  }

  @Override
  public List<ClassificationView> findActiveByTable(
      String datasourceId, String dbName, String tableName) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectList(
            new LambdaQueryWrapper<DsecClassificationPO>()
                .eq(DsecClassificationPO::getProjectId, projectId)
                .eq(DsecClassificationPO::getDbName, safe(dbName))
                .eq(DsecClassificationPO::getDatasourceId, Long.valueOf(datasourceId))
                .eq(DsecClassificationPO::getTableName, safe(tableName))
                .eq(DsecClassificationPO::getStatus, "ACTIVE"))
        .stream()
        .map(this::toView)
        .toList();
  }

  @Override
  public List<ClassificationView> findByTable(String dbName, String tableName) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectList(
            new LambdaQueryWrapper<DsecClassificationPO>()
                .eq(DsecClassificationPO::getProjectId, projectId)
                .eq(DsecClassificationPO::getDbName, safe(dbName))
                .eq(DsecClassificationPO::getTableName, safe(tableName))
                .eq(DsecClassificationPO::getStatus, "ACTIVE"))
        .stream()
        .map(this::toView)
        .toList();
  }

  public DsecClassificationPO findByKey(String objectKey) {
    return mapper.selectOne(
        new LambdaQueryWrapper<DsecClassificationPO>()
            .eq(DsecClassificationPO::getProjectId, currentProject.requireProjectId())
            .eq(DsecClassificationPO::getObjectKey, objectKey));
  }

  private ClassificationView toView(DsecClassificationPO po) {
    DsecSecurityLevelPO level = po.getLevelId() == null
        ? null : levelMapper.selectOne(scopedLevel(po.getProjectId(), po.getLevelId()));
    DsecDataCategoryPO category = po.getCategoryId() == null
        ? null : categoryMapper.selectOne(scopedCategory(po.getProjectId(), po.getCategoryId()));
    return new ClassificationView(
        po.getObjectKey(),
        po.getLevelId(),
        level == null ? null : level.getLevelCode(),
        level == null ? null : level.getLevelName(),
        level == null ? null : level.getRankNo(),
        po.getCategoryId(),
        category == null ? null : category.getCategoryCode(),
        category == null ? null : category.getCategoryName(),
        po.getStatus());
  }

  private LambdaQueryWrapper<DsecSecurityLevelPO> scopedLevel(Long projectId, Long levelId) {
    return new LambdaQueryWrapper<DsecSecurityLevelPO>()
        .eq(DsecSecurityLevelPO::getId, levelId)
        .eq(DsecSecurityLevelPO::getProjectId, projectId);
  }

  private LambdaQueryWrapper<DsecDataCategoryPO> scopedCategory(Long projectId, Long categoryId) {
    return new LambdaQueryWrapper<DsecDataCategoryPO>()
        .eq(DsecDataCategoryPO::getId, categoryId)
        .eq(DsecDataCategoryPO::getProjectId, projectId);
  }

  public long countTotal() {
    return mapper.selectCount(
        new LambdaQueryWrapper<DsecClassificationPO>()
            .eq(DsecClassificationPO::getProjectId, currentProject.requireProjectId()));
  }

  public long countByStatus(String status) {
    return mapper.selectCount(
        new LambdaQueryWrapper<DsecClassificationPO>()
            .eq(DsecClassificationPO::getProjectId, currentProject.requireProjectId())
            .eq(DsecClassificationPO::getStatus, status));
  }

  public List<LevelCount> levelDistribution() {
    Long projectId = currentProject.requireProjectId();
    List<DsecSecurityLevelPO> levels =
        levelMapper.selectList(
            new LambdaQueryWrapper<DsecSecurityLevelPO>()
                .eq(DsecSecurityLevelPO::getProjectId, projectId)
                .orderByAsc(DsecSecurityLevelPO::getRankNo));
    List<LevelCount> result = new java.util.ArrayList<>();
    for (DsecSecurityLevelPO level : levels) {
      long count =
          mapper.selectCount(
              new LambdaQueryWrapper<DsecClassificationPO>()
                  .eq(DsecClassificationPO::getProjectId, projectId)
                  .eq(DsecClassificationPO::getLevelId, level.getId()));
      result.add(new LevelCount(level.getLevelCode(), level.getLevelName(), level.getRankNo(), count));
    }
    return result;
  }
}
