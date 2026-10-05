package io.yak.ops.business.approval.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.business.approval.dao.mapper.ApprovalFlowMapper;
import io.yak.ops.business.approval.dao.mapper.ApprovalInstanceMapper;
import io.yak.ops.business.approval.exception.ApprovalException;
import io.yak.ops.business.approval.application.FlowAdminService.FlowView;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.approval.dao.model.ApprovalFlowPO;
import io.yak.ops.business.approval.dao.model.ApprovalInstancePO;
import io.yak.ops.common.enums.approval.ApprovalErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 流程定义管理单测(ticket 102):编码唯一预检、归一化落库、编码不可变、删除门槛与编码释放。 */
class FlowAdminServiceTest {

  private static final String VALID_STEPS = "[{\"level\":1,\"approvers\":[\"alice\"]}]";

  private ApprovalFlowMapper flowMapper;
  private ApprovalInstanceMapper instanceMapper;
  private FlowAdminService service;

  @BeforeEach
  void setUp() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), ""), ApprovalFlowPO.class);
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), ""), ApprovalInstancePO.class);
    flowMapper = Mockito.mock(ApprovalFlowMapper.class);
    instanceMapper = mock(ApprovalInstanceMapper.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    BusinessAuditService auditService = mock(BusinessAuditService.class);
    AuditOperationHandle handle = mock(AuditOperationHandle.class);
    Mockito.lenient().when(currentProject.requireProjectId()).thenReturn(1L);
    Mockito.lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(handle);
    service = new FlowAdminService(currentProject, flowMapper, instanceMapper, auditService);
  }

  // ---------- create ----------

  @Test
  void createPersistsNormalizedLevelsAndDefaults() {
    when(flowMapper.selectCount(any())).thenReturn(0L);

    FlowView view = service.create(" MODEL_PUBLISH ", "模型发布", null,
        List.of(List.of("alice", " alice ", "bob"), List.of("carol")), "lucas");

    ArgumentCaptor<ApprovalFlowPO> captor = ArgumentCaptor.forClass(ApprovalFlowPO.class);
    verify(flowMapper).insert(captor.capture());
    ApprovalFlowPO po = captor.getValue();
    assertEquals("MODEL_PUBLISH", po.getFlowCode());
    assertEquals(1L, po.getProjectId());
    assertEquals(Boolean.TRUE, po.getEnabled());
    assertEquals(Boolean.FALSE, po.getDeleted());
    assertEquals("lucas", po.getCreatedBy());
    assertEquals(2, view.steps().size());
    assertEquals(List.of("alice", "bob"), view.steps().get(0).approvers());

    // 只有一级时也应补 level 编号
    when(flowMapper.selectCount(any())).thenReturn(0L);
    service.create("STANDARD_PUBLISH", "标准发布", null, List.of(List.of("dave")), "lucas");
    ArgumentCaptor<ApprovalFlowPO> single = ArgumentCaptor.forClass(ApprovalFlowPO.class);
    verify(flowMapper, Mockito.times(2)).insert(single.capture());
    assertTrue(single.getValue().getStepsJson().contains("\"level\":1"));
  }

  @Test
  void createRejectsDuplicateFlowCode() {
    when(flowMapper.selectCount(any())).thenReturn(1L);

    ApprovalException ex = assertThrows(ApprovalException.class,
        () -> service.create("MODEL_PUBLISH", "模型发布", null, List.of(List.of("alice")), "lucas"));

    assertEquals(ApprovalErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("流程编码已存在"));
    verify(flowMapper, never()).insert(any(ApprovalFlowPO.class));
  }

  @Test
  void createRejectsBlankCodeAndTooManyLevelsBeforeTouchingDb() {
    assertThrows(ApprovalException.class,
        () -> service.create("  ", "x", null, List.of(List.of("a")), "lucas"));
    assertThrows(ApprovalException.class, () -> service.create("F", "x", null,
        List.of(List.of("a"), List.of("b"), List.of("c")), "lucas"));
    verify(flowMapper, never()).insert(any(ApprovalFlowPO.class));
  }

  // ---------- update ----------

  @Test
  void updateRewritesOnlyMutableFields() {
    when(flowMapper.selectOne(any())).thenReturn(flow(9L, "MODEL_PUBLISH", true));
    when(flowMapper.selectById(9L)).thenReturn(flow(9L, "MODEL_PUBLISH", true));

    service.update(9L, "新名字", "描述", List.of(List.of("dave")), "lucas");

    ArgumentCaptor<ApprovalFlowPO> captor = ArgumentCaptor.forClass(ApprovalFlowPO.class);
    verify(flowMapper).updateById(captor.capture());
    ApprovalFlowPO patch = captor.getValue();
    assertNull(patch.getFlowCode());
    assertEquals("新名字", patch.getFlowName());
    assertTrue(patch.getStepsJson().contains("dave"));
    assertEquals("lucas", patch.getUpdatedBy());
  }

  @Test
  void updateBlankNameRejected() {
    when(flowMapper.selectOne(any())).thenReturn(flow(9L, "MODEL_PUBLISH", true));
    assertThrows(ApprovalException.class,
        () -> service.update(9L, "  ", null, List.of(List.of("dave")), "lucas"));
    verify(flowMapper, never()).updateById(any(ApprovalFlowPO.class));
  }

  // ---------- toggle ----------

  @Test
  void toggleFlipsEnabled() {
    when(flowMapper.selectOne(any())).thenReturn(flow(9L, "MODEL_PUBLISH", true));
    when(flowMapper.selectById(9L)).thenReturn(flow(9L, "MODEL_PUBLISH", false));

    service.toggle(9L, "lucas");

    ArgumentCaptor<ApprovalFlowPO> captor = ArgumentCaptor.forClass(ApprovalFlowPO.class);
    verify(flowMapper).updateById(captor.capture());
    assertFalse(captor.getValue().getEnabled());
  }

  // ---------- delete ----------

  @Test
  void deleteBlockedByInFlightInstances() {
    when(flowMapper.selectOne(any())).thenReturn(flow(9L, "MODEL_PUBLISH", true));
    when(instanceMapper.selectCount(any())).thenReturn(1L);

    ApprovalException ex = assertThrows(ApprovalException.class, () -> service.delete(9L, "lucas"));

    assertTrue(ex.getMessage().contains("在途"));
    verify(flowMapper, never()).updateById(any(ApprovalFlowPO.class));
  }

  @Test
  void deleteSoftDeletesAndReleasesCode() {
    when(flowMapper.selectOne(any())).thenReturn(flow(9L, "MODEL_PUBLISH", true));
    when(instanceMapper.selectCount(any())).thenReturn(0L);

    service.delete(9L, "lucas");

    ArgumentCaptor<ApprovalFlowPO> captor = ArgumentCaptor.forClass(ApprovalFlowPO.class);
    verify(flowMapper).updateById(captor.capture());
    ApprovalFlowPO patch = captor.getValue();
    assertEquals("#del#9", patch.getFlowCode());
    assertEquals(Boolean.TRUE, patch.getDeleted());
    assertEquals(Boolean.FALSE, patch.getEnabled());
  }

  // ---------- 通用 ----------

  @Test
  void missingFlowThrowsFlowNotFound() {
    when(flowMapper.selectOne(any())).thenReturn(null);

    ApprovalException ex =
        assertThrows(ApprovalException.class, () -> service.toggle(404L, "lucas"));
    assertEquals(ApprovalErrorCode.FLOW_NOT_FOUND, ex.getErrorCode());
  }

  @Test
  void listReturnsOnlyTheRequestedPage() {
    Page<ApprovalFlowPO> page = new Page<>(2, 10);
    page.setRecords(List.of(flow(9L, "MODEL_PUBLISH", true)));
    page.setTotal(11);
    when(flowMapper.selectPage(any(), any())).thenReturn(page);

    var result = service.list("MODEL", 2, 10);

    assertEquals(1, result.records().size());
    assertEquals(11, result.total());
    assertEquals(2, result.pageNo());
    assertEquals(10, result.pageSize());
    verify(flowMapper).selectPage(any(), any());
  }

  private static ApprovalFlowPO flow(Long id, String code, boolean enabled) {
    ApprovalFlowPO po = new ApprovalFlowPO();
    po.setId(id);
    po.setProjectId(1L);
    po.setFlowCode(code);
    po.setFlowName("流程 " + code);
    po.setStepsJson(VALID_STEPS);
    po.setEnabled(enabled);
    po.setDeleted(false);
    return po;
  }
}
