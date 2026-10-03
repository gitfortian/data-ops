package io.yak.ops.business.security.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.security.api.AccessDecision;
import io.yak.ops.business.security.api.ClassificationView;
import io.yak.ops.business.security.api.MaskingDirective;
import io.yak.ops.business.security.api.SecurityAccessDecisionApi;
import io.yak.ops.common.enums.security.SecurityErrorCode;
import io.yak.ops.business.security.exception.SecurityException;
import io.yak.ops.business.security.dao.mapper.AccessPolicyMapper;
import io.yak.ops.business.security.dao.model.DsecAccessPolicyPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 数据级访问裁决服务:在平台 RBAC 之上按已审批策略判定 允许/拒绝/需审批,对读操作叠加脱敏裁决,
 * 并返回待执行脱敏指令。调用方确认消费结果后才显式写入访问流水。
 */
@Component
public class AccessDecisionService implements SecurityAccessDecisionApi {

  private static final int SENSITIVE_RANK = 3;

  private static final Set<String> RESOURCE_TYPES =
      Set.of("DATASOURCE", "DATABASE", "TABLE", "COLUMN");

  private final AccessPolicyMapper policyMapper;
  private final ClassificationService classificationService;
  private final MaskingService maskingService;
  private final AccessLogService accessLogService;
  private final CurrentProject currentProject;

  public AccessDecisionService(
      AccessPolicyMapper policyMapper,
      ClassificationService classificationService,
      MaskingService maskingService,
      AccessLogService accessLogService,
      CurrentProject currentProject) {
    this.policyMapper = policyMapper;
    this.classificationService = classificationService;
    this.maskingService = maskingService;
    this.accessLogService = accessLogService;
    this.currentProject = currentProject;
  }

  @Override
  public AccessDecision decide(String actor, List<String> roles, String objectKey, String action) {
    String act = StringUtils.hasText(action) ? action.trim().toUpperCase(java.util.Locale.ROOT) : "READ";
    if (!Set.of("READ", "WRITE", "EXPORT").contains(act)) {
      throw new SecurityException(SecurityErrorCode.ACCESS_INVALID_ACTION, action);
    }
    if (!StringUtils.hasText(objectKey)) {
      throw new SecurityException(SecurityErrorCode.CLASSIFICATION_INVALID_OBJECT, "对象键不能为空");
    }
    ClassificationView view = classificationService.find(objectKey);
    boolean unconfirmed = view != null && "CANDIDATE".equals(view.status());
    ClassificationView activeView = view != null && "ACTIVE".equals(view.status()) ? view : null;
    List<DsecAccessPolicyPO> policies = policyMapper.selectList(
        new LambdaQueryWrapper<DsecAccessPolicyPO>()
            .eq(DsecAccessPolicyPO::getProjectId, currentProject.requireProjectId())
            .eq(DsecAccessPolicyPO::getStatus, "APPROVED")
            .eq(DsecAccessPolicyPO::getAccessType, act));

    DsecAccessPolicyPO matchedDeny = null;
    DsecAccessPolicyPO matchedAllow = null;
    for (DsecAccessPolicyPO policy : policies) {
      if (!subjectMatched(policy, actor, roles)
          || !scopeMatched(policy, objectKey, activeView)
          || !withinValidity(policy)) {
        continue;
      }
      if ("DENY".equals(policy.getEffect())) {
        if (matchedDeny == null || higher(policy, matchedDeny)) {
          matchedDeny = policy;
        }
      } else if (matchedAllow == null || higher(policy, matchedAllow)) {
        matchedAllow = policy;
      }
    }

    String decision;
    Long matchedId;
    boolean allowed;
    if (matchedDeny != null) {
      decision = AccessDecision.DENY;
      allowed = false;
      matchedId = matchedDeny.getId();
    } else if (unconfirmed) {
      decision = AccessDecision.NEED_APPROVAL;
      allowed = false;
      matchedId = null;
    } else if (matchedAllow != null) {
      decision = AccessDecision.ALLOW;
      allowed = true;
      matchedId = matchedAllow.getId();
    } else {
      boolean sensitive = activeView != null && activeView.sensitive(SENSITIVE_RANK);
      decision = sensitive ? AccessDecision.NEED_APPROVAL : AccessDecision.ALLOW;
      allowed = !sensitive;
      matchedId = null;
    }

    boolean masked = false;
    String algoCode = null;
    if (allowed && "READ".equals(act)) {
      MaskingDirective directive = activeView == null ? MaskingDirective.none() : maskingService.resolve(objectKey);
      masked = directive.mask();
      algoCode = directive.algoCode();
    }
    return new AccessDecision(allowed, decision, matchedId, masked, algoCode);
  }

  @Override
  public void recordAccess(
      String actor,
      String objectKey,
      String action,
      AccessDecision decision,
      boolean maskingApplied,
      String source) {
    ClassificationView view = classificationService.find(objectKey);
    accessLogService.record(
        actor,
        resourceTypeOf(objectKey),
        objectKey,
        view == null ? null : view.levelName(),
        action,
        view == null ? null : view.levelCode(),
        decision.decision(),
        maskingApplied,
        maskingApplied ? decision.algoCode() : null,
        StringUtils.hasText(source) ? source : "SECURITY");
  }

  private static boolean higher(DsecAccessPolicyPO a, DsecAccessPolicyPO b) {
    return intOf(a.getPriority()) > intOf(b.getPriority());
  }

  private static int intOf(Integer value) {
    return value == null ? 0 : value;
  }

  private static boolean subjectMatched(DsecAccessPolicyPO policy, String actor, List<String> roles) {
    if ("USER".equals(policy.getSubjectType())) {
      return actor != null && actor.equalsIgnoreCase(policy.getSubjectKey());
    }
    return roles != null && roles.stream().anyMatch(r -> r != null && r.equalsIgnoreCase(policy.getSubjectKey()));
  }

  private static boolean withinValidity(DsecAccessPolicyPO policy) {
    LocalDateTime now = LocalDateTime.now();
    if (policy.getValidFrom() != null && now.isBefore(policy.getValidFrom())) {
      return false;
    }
    return policy.getValidTo() == null || !now.isAfter(policy.getValidTo());
  }

  private static boolean scopeMatched(DsecAccessPolicyPO policy, String objectKey, ClassificationView view) {
    String dsId = segment(objectKey, 1);
    String db = segment(objectKey, 2);
    String table = segment(objectKey, 3);
    String column = segment(objectKey, 4);
    return switch (policy.getScopeType()) {
      case "ALL" -> true;
      case "DATASOURCE" -> eqStr(policy.getDatasourceId(), dsId);
      case "DATABASE" -> eqStr(policy.getDatasourceId(), dsId) && eqStr(policy.getDbName(), db);
      case "TABLE" -> eqStr(policy.getDatasourceId(), dsId)
          && eqStr(policy.getDbName(), db) && eqStr(policy.getTableName(), table);
      case "COLUMN" -> eqStr(policy.getDatasourceId(), dsId)
          && eqStr(policy.getDbName(), db) && eqStr(policy.getTableName(), table)
          && eqStr(policy.getColumnName(), column);
      case "LEVEL" -> view != null && policy.getLevelId() != null && policy.getLevelId().equals(view.levelId());
      default -> false;
    };
  }

  private static boolean eqStr(Object left, String right) {
    return left != null && right != null && !"-".equals(right) && String.valueOf(left).equalsIgnoreCase(right);
  }

  private static String segment(String objectKey, int index) {
    if (objectKey == null) {
      return null;
    }
    String[] parts = objectKey.split(":", -1);
    return index < parts.length ? parts[index] : null;
  }

  private static String resourceTypeOf(String objectKey) {
    String type = segment(objectKey, 0);
    if (type != null) {
      String upper = type.trim().toUpperCase();
      if (RESOURCE_TYPES.contains(upper)) {
        return upper;
      }
    }
    return "COLUMN";
  }
}
