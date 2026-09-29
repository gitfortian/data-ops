package io.yak.ops.business.security.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.security.api.AccessDecision;
import io.yak.ops.business.security.api.ClassificationView;
import io.yak.ops.business.security.api.MaskingDirective;
import io.yak.ops.business.security.dao.mapper.AccessPolicyMapper;
import io.yak.ops.common.bean.po.security.DsecAccessPolicyPO;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 访问裁决核心闭环单测:DENY 优先、ALLOW+脱敏、敏感默认需审批、非敏感默认放行。 */
class AccessDecisionServiceTest {

  private static final String KEY = "COLUMN:7:db1:t1:phone";

  private AccessPolicyMapper policyMapper;
  private ClassificationService classificationService;
  private MaskingService maskingService;
  private AccessLogService accessLogService;
  private AccessDecisionService service;

  @BeforeEach
  void setUp() {
    policyMapper = Mockito.mock(AccessPolicyMapper.class);
    classificationService = Mockito.mock(ClassificationService.class);
    maskingService = Mockito.mock(MaskingService.class);
    accessLogService = Mockito.mock(AccessLogService.class);
    CurrentProject currentProject = Mockito.mock(CurrentProject.class);
    lenient().when(currentProject.requireProjectId()).thenReturn(1L);
    lenient().when(policyMapper.selectList(any())).thenReturn(List.of());
    service = new AccessDecisionService(
        policyMapper, classificationService, maskingService, accessLogService, currentProject);
  }

  @Test
  void explicitDenyWinsAndBlocksAccess() {
    when(policyMapper.selectList(any()))
        .thenReturn(List.of(policy("DENY", 10)));
    AccessDecision d = service.decide("alice", List.of(), KEY, "READ");
    assertEquals(AccessDecision.DENY, d.decision());
    assertFalse(d.allowed());
    assertEquals(10L, d.matchedPolicyId());
  }

  @Test
  void allowReadAppliesMaskingDirective() {
    when(policyMapper.selectList(any())).thenReturn(List.of(policy("ALLOW", 5)));
    when(classificationService.find(KEY)).thenReturn(null);
    when(maskingService.resolve(KEY))
        .thenReturn(new MaskingDirective(true, "MASK_PARTIAL", "{\"keepLeft\":1}"));
    AccessDecision d = service.decide("alice", List.of(), KEY, "READ");
    assertEquals(AccessDecision.ALLOW, d.decision());
    assertTrue(d.allowed());
    assertTrue(d.masked());
    assertEquals("MASK_PARTIAL", d.algoCode());
    verify(accessLogService)
        .record(eq("alice"), any(), eq(KEY), any(), eq("READ"), any(),
            eq(AccessDecision.ALLOW), eq(true), eq("MASK_PARTIAL"), any());
  }

  @Test
  void noPolicySensitiveFallsToNeedApproval() {
    when(policyMapper.selectList(any())).thenReturn(List.of());
    when(classificationService.find(KEY))
        .thenReturn(new ClassificationView(KEY, 4L, "L4", "核心", 4, null, null, null));
    AccessDecision d = service.decide("alice", List.of(), KEY, "READ");
    assertEquals(AccessDecision.NEED_APPROVAL, d.decision());
    assertFalse(d.allowed());
    verify(maskingService, never()).resolve(any());
  }

  @Test
  void noPolicyNonSensitiveDefaultsAllow() {
    when(policyMapper.selectList(any())).thenReturn(List.of());
    when(classificationService.find(KEY)).thenReturn(null);
    when(maskingService.resolve(KEY)).thenReturn(MaskingDirective.none());
    AccessDecision d = service.decide("alice", List.of(), KEY, "READ");
    assertEquals(AccessDecision.ALLOW, d.decision());
    assertTrue(d.allowed());
    assertFalse(d.masked());
  }

  @Test
  void subjectMismatchIsIgnoredByPolicyScope() {
    when(policyMapper.selectList(any())).thenReturn(List.of(policy("DENY", 10)));
    when(maskingService.resolve(KEY)).thenReturn(MaskingDirective.none());
    // actor differs from policy subjectKey -> policy not matched -> default allow (view null)
    AccessDecision d = service.decide("bob", List.of(), KEY, "READ");
    assertEquals(AccessDecision.ALLOW, d.decision());
    assertTrue(d.allowed());
  }

  private static DsecAccessPolicyPO policy(String effect, int priority) {
    DsecAccessPolicyPO po = new DsecAccessPolicyPO();
    po.setId((long) priority);
    po.setProjectId(1L);
    po.setStatus("APPROVED");
    po.setAccessType("READ");
    po.setEffect(effect);
    po.setPriority(priority);
    po.setSubjectType("USER");
    po.setSubjectKey("alice");
    po.setScopeType("ALL");
    return po;
  }
}
