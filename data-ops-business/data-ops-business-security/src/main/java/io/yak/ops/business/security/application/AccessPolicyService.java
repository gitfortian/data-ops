package io.yak.ops.business.security.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.security.dao.mapper.AccessPolicyMapper;
import io.yak.ops.business.security.exception.SecurityException;
import io.yak.ops.business.security.support.audit.SecurityAudit;
import io.yak.ops.common.bean.po.security.DsecAccessPolicyPO;
import io.yak.ops.common.enums.security.SecurityErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 数据访问策略服务:主体×客体×动作×效果 的策略 CRUD 与申请审批。 */
@Component
public class AccessPolicyService {

  private static final Set<String> SUBJECTS = Set.of("USER", "ROLE");
  private static final Set<String> SCOPES =
      Set.of("DATASOURCE", "DATABASE", "TABLE", "COLUMN", "LEVEL", "ALL");
  private static final Set<String> ACTIONS = Set.of("READ", "WRITE", "EXPORT");
  private static final Set<String> EFFECTS = Set.of("ALLOW", "DENY");

  private final AccessPolicyMapper mapper;
  private final CurrentProject currentProject;
  private final BusinessAuditService auditService;

  public AccessPolicyService(
      AccessPolicyMapper mapper, CurrentProject currentProject, BusinessAuditService auditService) {
    this.mapper = mapper;
    this.currentProject = currentProject;
    this.auditService = auditService;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DsecAccessPolicyPO create(DsecAccessPolicyPO draft, String operator) {
    validate(draft);
    Long projectId = currentProject.requireProjectId();
    draft.setId(null);
    draft.setProjectId(projectId);
    draft.setStatus("PENDING");
    draft.setApplicant(operator);
    draft.setCreatedBy(operator);
    LocalDateTime now = LocalDateTime.now();
    draft.setCreateTime(now);
    draft.setUpdateTime(now);
    return SecurityAudit.tx(
        auditService,
        AuditEventType.RESOURCE_CREATED,
        SecurityAudit.request("ACCESS_POLICY_CREATE", "Create access policy", "ACCESS_POLICY", null, draft.getPolicyName()),
        () -> {
          mapper.insert(draft);
          return draft;
        });
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DsecAccessPolicyPO update(Long id, DsecAccessPolicyPO patch) {
    DsecAccessPolicyPO po = get(id);
    mergeEditable(po, patch);
    validate(po);
    SecurityAudit.tx(
        auditService,
        AuditEventType.RESOURCE_UPDATED,
        SecurityAudit.request(
            "ACCESS_POLICY_UPDATE", "Update access policy", "ACCESS_POLICY", String.valueOf(id), po.getPolicyName()),
        () -> {
          po.setUpdateTime(LocalDateTime.now());
          po.setStatus("PENDING");
          mapper.updateById(po);
          return Boolean.TRUE;
        });
    return po;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DsecAccessPolicyPO decideApproval(Long id, boolean approve, String approver, String reason) {
    DsecAccessPolicyPO po = get(id);
    if (!"PENDING".equals(po.getStatus())) {
      throw new SecurityException(SecurityErrorCode.ACCESS_NOT_PENDING, po.getStatus());
    }
    SecurityAudit.tx(
        auditService,
        AuditEventType.RESOURCE_UPDATED,
        SecurityAudit.request(
            "ACCESS_POLICY_APPROVE", "Approve access policy", "ACCESS_POLICY", String.valueOf(id), po.getPolicyName()),
        () -> {
          po.setStatus(approve ? "APPROVED" : "REJECTED");
          po.setApprover(approver);
          if (StringUtils.hasText(reason)) {
            po.setReason(reason);
          }
          po.setUpdateTime(LocalDateTime.now());
          mapper.updateById(po);
          return Boolean.TRUE;
        });
    return po;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void disable(Long id) {
    DsecAccessPolicyPO po = get(id);
    SecurityAudit.tx(
        auditService,
        AuditEventType.RESOURCE_UPDATED,
        SecurityAudit.request(
            "ACCESS_POLICY_DISABLE", "Disable access policy", "ACCESS_POLICY", String.valueOf(id), po.getPolicyName()),
        () -> {
          po.setStatus("DISABLED");
          po.setUpdateTime(LocalDateTime.now());
          mapper.updateById(po);
          return Boolean.TRUE;
        });
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    DsecAccessPolicyPO po = get(id);
    SecurityAudit.tx(
        auditService,
        AuditEventType.RESOURCE_DELETED,
        SecurityAudit.request(
            "ACCESS_POLICY_DELETE", "Delete access policy", "ACCESS_POLICY", String.valueOf(id), po.getPolicyName()),
        () -> {
          mapper.deleteById(id);
          return Boolean.TRUE;
        });
  }

  public DsecAccessPolicyPO get(Long id) {
    DsecAccessPolicyPO po = mapper.selectOne(
        new LambdaQueryWrapper<DsecAccessPolicyPO>()
            .eq(DsecAccessPolicyPO::getId, id)
            .eq(DsecAccessPolicyPO::getProjectId, currentProject.requireProjectId()));
    if (po == null) {
      throw new SecurityException(SecurityErrorCode.ACCESS_POLICY_NOT_FOUND, String.valueOf(id));
    }
    return po;
  }

  public List<DsecAccessPolicyPO> listApproved() {
    return mapper.selectList(
        new LambdaQueryWrapper<DsecAccessPolicyPO>()
            .eq(DsecAccessPolicyPO::getProjectId, currentProject.requireProjectId())
            .eq(DsecAccessPolicyPO::getStatus, "APPROVED"));
  }

  public PageData<DsecAccessPolicyPO> page(int pageNo, int pageSize, String keyword, String status) {
    Long projectId = currentProject.requireProjectId();
    LambdaQueryWrapper<DsecAccessPolicyPO> wrapper =
        new LambdaQueryWrapper<DsecAccessPolicyPO>().eq(DsecAccessPolicyPO::getProjectId, projectId);
    if (StringUtils.hasText(keyword)) {
      wrapper.like(DsecAccessPolicyPO::getPolicyName, keyword.trim());
    }
    if (StringUtils.hasText(status)) {
      wrapper.eq(DsecAccessPolicyPO::getStatus, status);
    }
    wrapper.orderByDesc(DsecAccessPolicyPO::getUpdateTime).orderByDesc(DsecAccessPolicyPO::getId);
    Page<DsecAccessPolicyPO> page = Page.of(Math.max(1, pageNo), Math.max(1, pageSize));
    var result = mapper.selectPage(page, wrapper);
    return new PageData<>(
        result.getRecords(), result.getTotal(), result.getPages(), (long) pageNo, (long) pageSize);
  }

  public long countEnabled() {
    Long c = mapper.selectCount(
        new LambdaQueryWrapper<DsecAccessPolicyPO>()
            .eq(DsecAccessPolicyPO::getProjectId, currentProject.requireProjectId())
            .eq(DsecAccessPolicyPO::getStatus, "APPROVED"));
    return c == null ? 0L : c;
  }

  /**
   * Counts approved READ policy rules whose scopes include the supplied asset coordinates.
   * This is a configuration summary only; it does not evaluate a user, role, DENY precedence, or access decision.
   */
  public long countApplicableReadPolicies(
      Long datasourceId, String dbName, String tableName, Collection<Long> levelIds) {
    Long projectId = currentProject.requireProjectId();
    LocalDateTime now = LocalDateTime.now();
    LambdaQueryWrapper<DsecAccessPolicyPO> query =
        new LambdaQueryWrapper<DsecAccessPolicyPO>()
            .eq(DsecAccessPolicyPO::getProjectId, projectId)
            .eq(DsecAccessPolicyPO::getStatus, "APPROVED")
            .eq(DsecAccessPolicyPO::getAccessType, "READ")
            .and(
                scope -> {
                  scope.eq(DsecAccessPolicyPO::getScopeType, "ALL");
                  if (datasourceId != null) {
                    scope.or(
                        rule ->
                            rule.eq(DsecAccessPolicyPO::getScopeType, "DATASOURCE")
                                .eq(DsecAccessPolicyPO::getDatasourceId, datasourceId));
                  }
                  if (datasourceId != null && StringUtils.hasText(dbName)) {
                    scope.or(
                        rule ->
                            rule.eq(DsecAccessPolicyPO::getScopeType, "DATABASE")
                                .eq(DsecAccessPolicyPO::getDatasourceId, datasourceId)
                                .eq(DsecAccessPolicyPO::getDbName, dbName));
                  }
                  if (datasourceId != null && StringUtils.hasText(dbName) && StringUtils.hasText(tableName)) {
                    scope.or(
                        rule ->
                            rule.eq(DsecAccessPolicyPO::getScopeType, "TABLE")
                                .eq(DsecAccessPolicyPO::getDatasourceId, datasourceId)
                                .eq(DsecAccessPolicyPO::getDbName, dbName)
                                .eq(DsecAccessPolicyPO::getTableName, tableName));
                  }
                  if (levelIds != null && !levelIds.isEmpty()) {
                    scope.or(
                        rule ->
                            rule.eq(DsecAccessPolicyPO::getScopeType, "LEVEL")
                                .in(DsecAccessPolicyPO::getLevelId, levelIds));
                  }
                })
            .and(
                validity ->
                    validity.isNull(DsecAccessPolicyPO::getValidFrom)
                        .or()
                        .le(DsecAccessPolicyPO::getValidFrom, now))
            .and(
                validity ->
                    validity.isNull(DsecAccessPolicyPO::getValidTo)
                        .or()
                        .ge(DsecAccessPolicyPO::getValidTo, now));
    Long count = mapper.selectCount(query);
    return count == null ? 0L : count;
  }

  private void validate(DsecAccessPolicyPO po) {
    if (!StringUtils.hasText(po.getPolicyName())) {
      throw new SecurityException(SecurityErrorCode.ACCESS_INVALID_SUBJECT, "策略名必填");
    }
    if (po.getSubjectType() == null || !SUBJECTS.contains(po.getSubjectType())) {
      throw new SecurityException(SecurityErrorCode.ACCESS_INVALID_SUBJECT, po.getSubjectType());
    }
    if (!StringUtils.hasText(po.getSubjectKey())) {
      throw new SecurityException(SecurityErrorCode.ACCESS_INVALID_SUBJECT, "主体标识必填");
    }
    if (po.getScopeType() == null || !SCOPES.contains(po.getScopeType())) {
      throw new SecurityException(SecurityErrorCode.ACCESS_INVALID_SCOPE, po.getScopeType());
    }
    switch (po.getScopeType()) {
      case "DATASOURCE" -> requireDatasource(po);
      case "DATABASE" -> {
        requireDatasource(po);
        requireText(po.getDbName(), "库名必填");
      }
      case "TABLE" -> {
        requireDatasource(po);
        requireText(po.getDbName(), "库名必填");
        requireText(po.getTableName(), "表名必填");
      }
      case "COLUMN" -> {
        requireDatasource(po);
        requireText(po.getDbName(), "库名必填");
        requireText(po.getTableName(), "表名必填");
        requireText(po.getColumnName(), "字段名必填");
      }
      case "LEVEL" -> {
        if (po.getLevelId() == null) {
          throw new SecurityException(SecurityErrorCode.ACCESS_INVALID_SCOPE, "安全等级必填");
        }
      }
      default -> { }
    }
    if (po.getAccessType() == null || !ACTIONS.contains(po.getAccessType())) {
      throw new SecurityException(SecurityErrorCode.ACCESS_INVALID_ACTION, po.getAccessType());
    }
    if (po.getEffect() == null || !EFFECTS.contains(po.getEffect())) {
      throw new SecurityException(SecurityErrorCode.ACCESS_INVALID_ACTION, po.getEffect());
    }
  }

  private void requireDatasource(DsecAccessPolicyPO po) {
    if (po.getDatasourceId() == null || po.getDatasourceId() <= 0) {
      throw new SecurityException(SecurityErrorCode.ACCESS_INVALID_SCOPE, "数据源ID必填且必须大于 0");
    }
  }

  private void requireText(String value, String message) {
    if (!StringUtils.hasText(value)) {
      throw new SecurityException(SecurityErrorCode.ACCESS_INVALID_SCOPE, message);
    }
  }

  private void mergeEditable(DsecAccessPolicyPO target, DsecAccessPolicyPO patch) {
    if (StringUtils.hasText(patch.getPolicyName())) {
      target.setPolicyName(patch.getPolicyName());
    }
    if (patch.getSubjectType() != null) {
      target.setSubjectType(patch.getSubjectType());
    }
    if (patch.getSubjectKey() != null) {
      target.setSubjectKey(patch.getSubjectKey());
    }
    if (patch.getScopeType() != null) {
      target.setScopeType(patch.getScopeType());
    }
    target.setDatasourceId(patch.getDatasourceId());
    target.setDbName(patch.getDbName());
    target.setTableName(patch.getTableName());
    target.setColumnName(patch.getColumnName());
    target.setLevelId(patch.getLevelId());
    if (patch.getAccessType() != null) {
      target.setAccessType(patch.getAccessType());
    }
    if (patch.getEffect() != null) {
      target.setEffect(patch.getEffect());
    }
    if (patch.getPriority() != null) {
      target.setPriority(patch.getPriority());
    }
    target.setValidFrom(patch.getValidFrom());
    target.setValidTo(patch.getValidTo());
  }

}
