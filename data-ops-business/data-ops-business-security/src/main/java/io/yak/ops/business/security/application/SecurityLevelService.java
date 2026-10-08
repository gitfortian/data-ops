package io.yak.ops.business.security.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.security.dao.mapper.ClassificationMapper;
import io.yak.ops.business.security.dao.mapper.SecurityLevelMapper;
import io.yak.ops.business.security.exception.SecurityException;
import io.yak.ops.business.security.support.audit.SecurityAudit;
import io.yak.ops.business.semantic.api.StandardQueryApi;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.business.security.dao.model.DsecClassificationPO;
import io.yak.ops.business.security.dao.model.DsecSecurityLevelPO;
import io.yak.ops.common.enums.security.SecurityErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 安全等级(分级)字典服务:编码唯一、序位、状态流转、被引用阻断;等级字典真源在本模块,关联的语义 SECURITY 标准仅为字段级模板(ticket 01 裁决)。 */
@Component
public class SecurityLevelService {

  private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_]{1,32}$");
  private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "DISABLED");

  private final SecurityLevelMapper mapper;
  private final ClassificationMapper classificationMapper;
  private final CurrentProject currentProject;
  private final BusinessAuditService auditService;
  private final StandardQueryApi standardQueryApi;

  public SecurityLevelService(
      SecurityLevelMapper mapper,
      ClassificationMapper classificationMapper,
      CurrentProject currentProject,
      BusinessAuditService auditService,
      StandardQueryApi standardQueryApi) {
    this.mapper = mapper;
    this.classificationMapper = classificationMapper;
    this.currentProject = currentProject;
    this.auditService = auditService;
    this.standardQueryApi = standardQueryApi;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DsecSecurityLevelPO create(
      String code, String name, Integer rankNo, Long stdSecurityId, String description, String operator) {
    if (!StringUtils.hasText(code) || !CODE.matcher(code).matches()) {
      throw new SecurityException(SecurityErrorCode.LEVEL_INVALID_CODE, code);
    }
    if (!StringUtils.hasText(name)) {
      throw new SecurityException(SecurityErrorCode.LEVEL_INVALID_NAME);
    }
    if (rankNo == null || rankNo < 1) {
      throw new SecurityException(SecurityErrorCode.LEVEL_INVALID_RANK, String.valueOf(rankNo));
    }
    validateStdSecurity(stdSecurityId);
    Long projectId = currentProject.requireProjectId();
    if (existsByCode(code)) {
      throw new SecurityException(SecurityErrorCode.LEVEL_DUPLICATE_CODE, code);
    }
    return SecurityAudit.tx(
        auditService,
        io.yak.ops.business.audit.AuditEventType.RESOURCE_CREATED,
        SecurityAudit.request("SECURITY_LEVEL_CREATE", "Create security level", "SECURITY_LEVEL", null, code),
        () -> {
          LocalDateTime now = LocalDateTime.now();
          DsecSecurityLevelPO po = new DsecSecurityLevelPO();
          po.setProjectId(projectId);
          po.setLevelCode(code);
          po.setLevelName(name);
          po.setRankNo(rankNo);
          po.setStdSecurityId(stdSecurityId);
          po.setDescription(description);
          po.setStatus("DRAFT");
          po.setCreatedBy(operator);
          po.setCreateTime(now);
          po.setUpdateTime(now);
          mapper.insert(po);
          return po;
        });
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DsecSecurityLevelPO update(
      Long id, String name, Integer rankNo, Long stdSecurityId, String description) {
    DsecSecurityLevelPO po = get(id);
    if (!StringUtils.hasText(name)) {
      throw new SecurityException(SecurityErrorCode.LEVEL_INVALID_NAME);
    }
    if (rankNo != null && rankNo < 1) {
      throw new SecurityException(SecurityErrorCode.LEVEL_INVALID_RANK, String.valueOf(rankNo));
    }
    validateStdSecurity(stdSecurityId);
    return SecurityAudit.tx(
        auditService,
        io.yak.ops.business.audit.AuditEventType.RESOURCE_UPDATED,
        SecurityAudit.request(
            "SECURITY_LEVEL_UPDATE", "Update security level", "SECURITY_LEVEL", String.valueOf(id), po.getLevelCode()),
        () -> {
          // 显式 set:允许把关联标准/描述清空(实体 patch 的 null 会被 MP 忽略)
          mapper.update(
              null,
              new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<DsecSecurityLevelPO>()
                  .eq(DsecSecurityLevelPO::getId, id)
                  .eq(DsecSecurityLevelPO::getProjectId, currentProject.requireProjectId())
                  .set(DsecSecurityLevelPO::getLevelName, name)
                  .set(rankNo != null, DsecSecurityLevelPO::getRankNo, rankNo)
                  .set(DsecSecurityLevelPO::getStdSecurityId, stdSecurityId)
                  .set(DsecSecurityLevelPO::getDescription, description)
                  .set(DsecSecurityLevelPO::getUpdateTime, LocalDateTime.now()));
          return get(id);
        });
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void changeStatus(Long id, String status) {
    DsecSecurityLevelPO po = get(id);
    if (status == null || !STATUSES.contains(status)) {
      throw new SecurityException(SecurityErrorCode.LEVEL_NOT_FOUND, status);
    }
    if ("DRAFT".equals(status) && !"DRAFT".equals(po.getStatus())) {
      throw new SecurityException(SecurityErrorCode.INVALID_STATUS, "已生效/停用的等级不可回退为草稿");
    }
    SecurityAudit.tx(
        auditService,
        io.yak.ops.business.audit.AuditEventType.RESOURCE_UPDATED,
        SecurityAudit.request(
            "SECURITY_LEVEL_STATUS", "Change security level status", "SECURITY_LEVEL", String.valueOf(id), po.getLevelCode()),
        () -> {
          DsecSecurityLevelPO patch = new DsecSecurityLevelPO();
          patch.setStatus(status);
          patch.setUpdateTime(LocalDateTime.now());
          mapper.update(patch, scoped(id));
          return Boolean.TRUE;
        });
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    DsecSecurityLevelPO po = get(id);
    Long used =
        classificationMapper.selectCount(
            new LambdaQueryWrapper<DsecClassificationPO>()
                .eq(DsecClassificationPO::getProjectId, currentProject.requireProjectId())
                .eq(DsecClassificationPO::getLevelId, id));
    if (used != null && used > 0) {
      throw new SecurityException(SecurityErrorCode.LEVEL_REFERENCED, po.getLevelCode());
    }
    SecurityAudit.tx(
        auditService,
        io.yak.ops.business.audit.AuditEventType.RESOURCE_DELETED,
        SecurityAudit.request(
            "SECURITY_LEVEL_DELETE", "Delete security level", "SECURITY_LEVEL", String.valueOf(id), po.getLevelCode()),
        () -> {
          mapper.delete(scoped(id));
          return Boolean.TRUE;
        });
  }

  public DsecSecurityLevelPO get(Long id) {
    DsecSecurityLevelPO po = mapper.selectOne(scoped(id));
    if (po == null) {
      throw new SecurityException(SecurityErrorCode.LEVEL_NOT_FOUND, String.valueOf(id));
    }
    return po;
  }

  public List<DsecSecurityLevelPO> findAllActive() {
    return mapper.selectList(
        projectScope()
            .eq(DsecSecurityLevelPO::getStatus, "ACTIVE")
            .orderByAsc(DsecSecurityLevelPO::getRankNo));
  }

  public PageData<DsecSecurityLevelPO> page(int pageNo, int pageSize, String keyword, String status) {
    Long projectId = currentProject.requireProjectId();
    LambdaQueryWrapper<DsecSecurityLevelPO> wrapper =
        projectScope();
    if (StringUtils.hasText(keyword)) {
      String kw = keyword.trim();
      wrapper.and(c -> c.like(DsecSecurityLevelPO::getLevelCode, kw).or().like(DsecSecurityLevelPO::getLevelName, kw));
    }
    if (StringUtils.hasText(status)) {
      wrapper.eq(DsecSecurityLevelPO::getStatus, status);
    }
    wrapper.orderByAsc(DsecSecurityLevelPO::getRankNo).orderByDesc(DsecSecurityLevelPO::getId);
    Page<DsecSecurityLevelPO> page = Page.of(Math.max(1, pageNo), Math.max(1, pageSize));
    var result = mapper.selectPage(page, wrapper);
    return new PageData<>(
        result.getRecords(), result.getTotal(), result.getPages(), (long) pageNo, (long) pageSize);
  }

  public long countAll() {
    return mapper.selectCount(
        projectScope());
  }

  /** 引用校验仿 MdmAttributeService.validateKindRef:可空;非空必须是存在且启用的 SECURITY 标准。 */
  private void validateStdSecurity(Long stdSecurityId) {
    if (stdSecurityId == null) {
      return;
    }
    Standard standard = standardQueryApi.get(stdSecurityId);
    if (standard == null
        || standard.kind() != StandardKind.SECURITY
        || standard.status() != StandardStatus.ENABLED) {
      throw new SecurityException(SecurityErrorCode.LEVEL_INVALID_STD_SECURITY, String.valueOf(stdSecurityId));
    }
  }

  private boolean existsByCode(String code) {
    Long c =
        mapper.selectCount(
            projectScope()
                .eq(DsecSecurityLevelPO::getLevelCode, code));
    return c != null && c > 0;
  }

  /** Project scope is required for every security level read and code lookup. */
  private LambdaQueryWrapper<DsecSecurityLevelPO> projectScope() {
    return new LambdaQueryWrapper<DsecSecurityLevelPO>()
        .eq(DsecSecurityLevelPO::getProjectId, currentProject.requireProjectId());
  }

  private LambdaQueryWrapper<DsecSecurityLevelPO> scoped(Long id) {
    return new LambdaQueryWrapper<DsecSecurityLevelPO>()
        .eq(DsecSecurityLevelPO::getId, id)
        .eq(DsecSecurityLevelPO::getProjectId, currentProject.requireProjectId());
  }
}
