package io.yak.ops.business.security.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.security.dao.mapper.ClassificationMapper;
import io.yak.ops.business.security.dao.mapper.SecurityLevelMapper;
import io.yak.ops.business.security.exception.SecurityException;
import io.yak.ops.business.semantic.api.StandardQueryApi;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.common.bean.po.security.DsecSecurityLevelPO;
import io.yak.ops.common.enums.security.SecurityErrorCode;
import io.yak.ops.core.project.CurrentProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 安全等级服务单测:编码校验、唯一性、持久化草稿态、被引用删除阻断、不存在、SECURITY 标准引用校验。 */
class SecurityLevelServiceTest {

  private SecurityLevelMapper mapper;
  private ClassificationMapper classificationMapper;
  private CurrentProject currentProject;
  private StandardQueryApi standardQueryApi;
  private SecurityLevelService service;

  @BeforeEach
  void setUp() {
    mapper = Mockito.mock(SecurityLevelMapper.class);
    classificationMapper = Mockito.mock(ClassificationMapper.class);
    currentProject = Mockito.mock(CurrentProject.class);
    BusinessAuditService auditService = Mockito.mock(BusinessAuditService.class);
    AuditOperationHandle handle = Mockito.mock(AuditOperationHandle.class);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(handle);
    lenient().when(currentProject.requireProjectId()).thenReturn(1L);
    standardQueryApi = Mockito.mock(StandardQueryApi.class);
    service =
        new SecurityLevelService(mapper, classificationMapper, currentProject, auditService, standardQueryApi);
  }

  @Test
  void createRejectsInvalidCode() {
    SecurityException ex =
        assertThrows(SecurityException.class,
            () -> service.create("bad code!", "机密", 5, null, null, "tester"));
    assertEquals(SecurityErrorCode.LEVEL_INVALID_CODE, ex.getErrorCode());
    verify(mapper, never()).insert(any(DsecSecurityLevelPO.class));
  }

  @Test
  void createRejectsRankBelowOne() {
    SecurityException ex =
        assertThrows(SecurityException.class,
            () -> service.create("L1", "机密", 0, null, null, "tester"));
    assertEquals(SecurityErrorCode.LEVEL_INVALID_RANK, ex.getErrorCode());
  }

  @Test
  void createRejectsDuplicateCode() {
    when(mapper.selectCount(any())).thenReturn(1L);
    SecurityException ex =
        assertThrows(SecurityException.class,
            () -> service.create("L1", "机密", 1, null, null, "tester"));
    assertEquals(SecurityErrorCode.LEVEL_DUPLICATE_CODE, ex.getErrorCode());
    verify(mapper, never()).insert(any(DsecSecurityLevelPO.class));
  }

  @Test
  void createPersistsAsDraft() {
    when(mapper.selectCount(any())).thenReturn(0L);
    DsecSecurityLevelPO created = service.create("L1", "机密", 1, null, "最高密", "tester");
    assertEquals("L1", created.getLevelCode());
    assertEquals("DRAFT", created.getStatus());
    assertEquals(1L, created.getProjectId());
    verify(mapper).insert(any(DsecSecurityLevelPO.class));
  }

  @Test
  void getThrowsWhenMissing() {
    when(mapper.selectOne(any())).thenReturn(null);
    SecurityException ex = assertThrows(SecurityException.class, () -> service.get(99L));
    assertEquals(SecurityErrorCode.LEVEL_NOT_FOUND, ex.getErrorCode());
  }

  @Test
  void deleteBlockedWhenReferenced() {
    when(mapper.selectOne(any())).thenReturn(level(3L, "L3"));
    when(classificationMapper.selectCount(any())).thenReturn(2L);
    SecurityException ex = assertThrows(SecurityException.class, () -> service.delete(3L));
    assertEquals(SecurityErrorCode.LEVEL_REFERENCED, ex.getErrorCode());
    verify(mapper, never()).delete(any());
  }

  @Test
  void deleteSucceedsWhenNotReferenced() {
    when(mapper.selectOne(any())).thenReturn(level(3L, "L3"));
    when(classificationMapper.selectCount(any())).thenReturn(0L);
    service.delete(3L);
    verify(mapper).delete(any());
  }

  @Test
  void createRejectsMissingStdSecurityRef() {
    when(mapper.selectCount(any())).thenReturn(0L);
    when(standardQueryApi.get(7L)).thenReturn(null);
    SecurityException ex =
        assertThrows(SecurityException.class,
            () -> service.create("L1", "机密", 1, 7L, null, "tester"));
    assertEquals(SecurityErrorCode.LEVEL_INVALID_STD_SECURITY, ex.getErrorCode());
    verify(mapper, never()).insert(any(DsecSecurityLevelPO.class));
  }

  @Test
  void createRejectsWrongKindOrDisabledStdSecurityRef() {
    when(mapper.selectCount(any())).thenReturn(0L);
    when(standardQueryApi.get(7L)).thenReturn(standard(7L, StandardKind.TYPE, StandardStatus.ENABLED));
    assertThrows(SecurityException.class,
        () -> service.create("L1", "机密", 1, 7L, null, "tester"));
    when(standardQueryApi.get(8L)).thenReturn(standard(8L, StandardKind.SECURITY, StandardStatus.DISABLED));
    SecurityException ex =
        assertThrows(SecurityException.class,
            () -> service.create("L1", "机密", 1, 8L, null, "tester"));
    assertEquals(SecurityErrorCode.LEVEL_INVALID_STD_SECURITY, ex.getErrorCode());
    verify(mapper, never()).insert(any(DsecSecurityLevelPO.class));
  }

  @Test
  void createPersistsEnabledStdSecurityRef() {
    when(mapper.selectCount(any())).thenReturn(0L);
    when(standardQueryApi.get(7L)).thenReturn(standard(7L, StandardKind.SECURITY, StandardStatus.ENABLED));
    DsecSecurityLevelPO created = service.create("L3", "机密", 3, 7L, null, "tester");
    assertEquals(7L, created.getStdSecurityId());
    verify(mapper).insert(any(DsecSecurityLevelPO.class));
  }

  private static Standard standard(Long id, StandardKind kind, StandardStatus status) {
    return new Standard(
        id, kind, "SEC_" + id, "标准" + id, status, 1, 0, false, null,
        new Standard.KindFields(null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, "L3", null),
        "tester", null, null);
  }

  private static DsecSecurityLevelPO level(Long id, String code) {
    DsecSecurityLevelPO po = new DsecSecurityLevelPO();
    po.setId(id);
    po.setProjectId(1L);
    po.setLevelCode(code);
    po.setLevelName("级别");
    po.setRankNo(3);
    po.setStatus("ACTIVE");
    return po;
  }
}
