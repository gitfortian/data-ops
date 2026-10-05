package io.yak.ops.business.security.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.security.dao.mapper.AccessPolicyMapper;
import io.yak.ops.business.security.dao.model.DsecAccessPolicyPO;
import io.yak.ops.business.security.exception.SecurityException;
import io.yak.ops.common.enums.security.SecurityErrorCode;
import io.yak.ops.core.project.CurrentProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AccessPolicyApprovalSnapshotTest {

  private AccessPolicyMapper mapper;
  private AuditOperationHandle auditHandle;
  private AccessPolicyService service;

  @BeforeEach
  void setUp() {
    mapper = mock(AccessPolicyMapper.class);
    CurrentProject project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenReturn(1L);
    BusinessAuditService audit = mock(BusinessAuditService.class);
    auditHandle = mock(AuditOperationHandle.class);
    when(audit.start(any())).thenReturn(auditHandle);
    service = new AccessPolicyService(mapper, project, audit);
  }

  @Test
  void changedPolicyCannotBeApprovedFromAnOldSubmission() {
    DsecAccessPolicyPO current = policy("ALL");
    when(mapper.selectOne(any())).thenReturn(current);
    AccessPolicyApprovalSnapshot submitted = AccessPolicyApprovalSnapshot.from(policy("TABLE"));

    SecurityException error = assertThrows(SecurityException.class,
        () -> service.decideApproval(submitted, true, "reviewer", "approve"));

    assertEquals(SecurityErrorCode.ACCESS_APPROVAL_SNAPSHOT_STALE, error.getErrorCode());
    verify(mapper, never()).updateById(any(DsecAccessPolicyPO.class));
  }

  @Test
  void unchangedPolicyCanProceedToTheExistingApprovalTransition() {
    DsecAccessPolicyPO current = policy("TABLE");
    when(mapper.selectOne(any())).thenReturn(current);

    DsecAccessPolicyPO decided = service.decideApproval(
        AccessPolicyApprovalSnapshot.from(policy("TABLE")), true, "reviewer", "approve");

    assertEquals("APPROVED", decided.getStatus());
    verify(mapper).updateById(current);
  }

  private static DsecAccessPolicyPO policy(String scope) {
    DsecAccessPolicyPO policy = new DsecAccessPolicyPO();
    policy.setId(8L);
    policy.setProjectId(1L);
    policy.setPolicyName("Read orders");
    policy.setSubjectType("USER");
    policy.setSubjectKey("analyst");
    policy.setScopeType(scope);
    policy.setDbName("warehouse");
    policy.setTableName("orders");
    policy.setAccessType("READ");
    policy.setEffect("ALLOW");
    policy.setStatus("PENDING");
    return policy;
  }
}
