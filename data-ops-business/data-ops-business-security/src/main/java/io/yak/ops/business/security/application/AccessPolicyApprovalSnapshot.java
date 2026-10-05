package io.yak.ops.business.security.application;

import io.yak.ops.business.security.dao.model.DsecAccessPolicyPO;
import java.time.LocalDateTime;
import java.util.Objects;

/** Immutable source-owned policy facts reviewed by an ACCESS_GRANT approver. */
public record AccessPolicyApprovalSnapshot(
    Long policyId,
    String policyName,
    String subjectType,
    String subjectKey,
    String scopeType,
    Long datasourceId,
    String dbName,
    String tableName,
    String columnName,
    Long levelId,
    String accessType,
    String effect,
    Integer priority,
    String validFrom,
    String validTo) {

  public static AccessPolicyApprovalSnapshot from(DsecAccessPolicyPO policy) {
    return new AccessPolicyApprovalSnapshot(
        policy.getId(), policy.getPolicyName(), policy.getSubjectType(), policy.getSubjectKey(),
        policy.getScopeType(), policy.getDatasourceId(), policy.getDbName(), policy.getTableName(),
        policy.getColumnName(), policy.getLevelId(), policy.getAccessType(), policy.getEffect(),
        policy.getPriority(), format(policy.getValidFrom()), format(policy.getValidTo()));
  }

  public boolean matches(DsecAccessPolicyPO policy) {
    return Objects.equals(this, from(policy));
  }

  private static String format(LocalDateTime value) {
    return value == null ? null : value.toString();
  }
}
