package io.yak.ops.business.security.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.framework.common.ErrorCode;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.security.dao.mapper.ClassificationMapper;
import io.yak.ops.business.security.dao.mapper.DataCategoryMapper;
import io.yak.ops.business.security.exception.SecurityException;
import io.yak.ops.business.security.support.audit.SecurityAudit;
import io.yak.ops.business.security.dao.model.DsecClassificationPO;
import io.yak.ops.business.security.dao.model.DsecDataCategoryPO;
import io.yak.ops.common.enums.security.SecurityErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 数据分类(分类树)服务:编码唯一、父分类校验、删除阻断(子分类/被引用)。 */
@Component
public class DataCategoryService {

  private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_\\u4e00-\\u9fa5-]{1,64}$");

  private final DataCategoryMapper mapper;
  private final ClassificationMapper classificationMapper;
  private final CurrentProject currentProject;
  private final BusinessAuditService auditService;

  public DataCategoryService(
      DataCategoryMapper mapper,
      ClassificationMapper classificationMapper,
      CurrentProject currentProject,
      BusinessAuditService auditService) {
    this.mapper = mapper;
    this.classificationMapper = classificationMapper;
    this.currentProject = currentProject;
    this.auditService = auditService;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DsecDataCategoryPO create(
      String code, String name, String parentCode, Integer sortOrder, String description, String operator) {
    if (!StringUtils.hasText(code) || !CODE.matcher(code).matches()) {
      throw new SecurityException(SecurityErrorCode.CATEGORY_INVALID_CODE, code);
    }
    if (!StringUtils.hasText(name)) {
      throw new SecurityException(SecurityErrorCode.CATEGORY_INVALID_CODE, "名称不能为空");
    }
    Long projectId = currentProject.requireProjectId();
    if (existsByCode(code)) {
      throw new SecurityException(SecurityErrorCode.CATEGORY_DUPLICATE_CODE, code);
    }
    if (StringUtils.hasText(parentCode) && !existsByCode(parentCode)) {
      throw new SecurityException(SecurityErrorCode.CATEGORY_PARENT_NOT_FOUND, parentCode);
    }
    return SecurityAudit.tx(
        auditService,
        AuditEventType.RESOURCE_CREATED,
        SecurityAudit.request("DATA_CATEGORY_CREATE", "Create data category", "DATA_CATEGORY", null, code),
        () -> {
          LocalDateTime now = LocalDateTime.now();
          DsecDataCategoryPO po = new DsecDataCategoryPO();
          po.setProjectId(projectId);
          po.setCategoryCode(code);
          po.setCategoryName(name);
          po.setParentCode(StringUtils.hasText(parentCode) ? parentCode : null);
          po.setSortOrder(sortOrder == null ? 0 : sortOrder);
          po.setDescription(description);
          po.setStatus("ACTIVE");
          po.setCreatedBy(operator);
          po.setCreateTime(now);
          po.setUpdateTime(now);
          mapper.insert(po);
          return po;
        });
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DsecDataCategoryPO update(Long id, String name, Integer sortOrder, String description) {
    DsecDataCategoryPO po = get(id);
    if (!StringUtils.hasText(name)) {
      throw new SecurityException(SecurityErrorCode.CATEGORY_INVALID_CODE, "名称不能为空");
    }
    return SecurityAudit.tx(
        auditService,
        AuditEventType.RESOURCE_UPDATED,
        SecurityAudit.request(
            "DATA_CATEGORY_UPDATE", "Update data category", "DATA_CATEGORY", String.valueOf(id), po.getCategoryCode()),
        () -> {
          DsecDataCategoryPO patch = new DsecDataCategoryPO();
          patch.setCategoryName(name);
          patch.setSortOrder(sortOrder);
          patch.setDescription(description);
          patch.setUpdateTime(LocalDateTime.now());
          mapper.update(patch, scoped(id));
          return get(id);
        });
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    DsecDataCategoryPO po = get(id);
    Long children =
        mapper.selectCount(
            new LambdaQueryWrapper<DsecDataCategoryPO>()
                .eq(DsecDataCategoryPO::getProjectId, currentProject.requireProjectId())
                .eq(DsecDataCategoryPO::getParentCode, po.getCategoryCode()));
    if (children != null && children > 0) {
      throw new SecurityException(SecurityErrorCode.CATEGORY_HAS_CHILDREN, po.getCategoryCode());
    }
    Long used =
        classificationMapper.selectCount(
            new LambdaQueryWrapper<DsecClassificationPO>()
                .eq(DsecClassificationPO::getProjectId, currentProject.requireProjectId())
                .eq(DsecClassificationPO::getCategoryId, id));
    if (used != null && used > 0) {
      throw new SecurityException(SecurityErrorCode.CATEGORY_REFERENCED, po.getCategoryCode());
    }
    SecurityAudit.tx(
        auditService,
        AuditEventType.RESOURCE_DELETED,
        SecurityAudit.request(
            "DATA_CATEGORY_DELETE", "Delete data category", "DATA_CATEGORY", String.valueOf(id), po.getCategoryCode()),
        () -> {
          mapper.delete(scoped(id));
          return Boolean.TRUE;
        });
  }

  public DsecDataCategoryPO get(Long id) {
    DsecDataCategoryPO po = mapper.selectOne(scoped(id));
    if (po == null) {
      throw new SecurityException(SecurityErrorCode.CATEGORY_NOT_FOUND, String.valueOf(id));
    }
    return po;
  }

  public DsecDataCategoryPO findByCode(String code) {
    return mapper.selectOne(
        new LambdaQueryWrapper<DsecDataCategoryPO>()
            .eq(DsecDataCategoryPO::getProjectId, currentProject.requireProjectId())
            .eq(DsecDataCategoryPO::getCategoryCode, code));
  }

  public List<DsecDataCategoryPO> listAll() {
    return mapper.selectList(
        new LambdaQueryWrapper<DsecDataCategoryPO>()
            .eq(DsecDataCategoryPO::getProjectId, currentProject.requireProjectId())
            .orderByAsc(DsecDataCategoryPO::getSortOrder)
            .orderByAsc(DsecDataCategoryPO::getId));
  }

  public PageData<DsecDataCategoryPO> page(int pageNo, int pageSize, String keyword) {
    Long projectId = currentProject.requireProjectId();
    LambdaQueryWrapper<DsecDataCategoryPO> wrapper =
        new LambdaQueryWrapper<DsecDataCategoryPO>().eq(DsecDataCategoryPO::getProjectId, projectId);
    if (StringUtils.hasText(keyword)) {
      String kw = keyword.trim();
      wrapper.and(
          c -> c.like(DsecDataCategoryPO::getCategoryCode, kw).or().like(DsecDataCategoryPO::getCategoryName, kw));
    }
    wrapper.orderByAsc(DsecDataCategoryPO::getSortOrder).orderByAsc(DsecDataCategoryPO::getId);
    Page<DsecDataCategoryPO> page = Page.of(Math.max(1, pageNo), Math.max(1, pageSize));
    var result = mapper.selectPage(page, wrapper);
    return new PageData<>(
        result.getRecords(), result.getTotal(), result.getPages(), (long) pageNo, (long) pageSize);
  }

  private boolean existsByCode(String code) {
    return findByCode(code) != null;
  }

  private LambdaQueryWrapper<DsecDataCategoryPO> scoped(Long id) {
    return new LambdaQueryWrapper<DsecDataCategoryPO>()
        .eq(DsecDataCategoryPO::getId, id)
        .eq(DsecDataCategoryPO::getProjectId, currentProject.requireProjectId());
  }
}
