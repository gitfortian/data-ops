package io.yak.ops.business.modeling.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.approval.api.ApprovalApi;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.approval.api.ApprovalSubmitCommand;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 模型发布审批发起单测(ticket 104):命令组装、payload 快照、模型缺失拦截。 */
class ModelPublishApprovalServiceTest {

  private ApprovalApi approvalApi;
  private ModelRepository modelRepository;
  private ModelPublishApprovalService service;

  @BeforeEach
  void setUp() {
    approvalApi = mock(ApprovalApi.class);
    modelRepository = mock(ModelRepository.class);
    service = new ModelPublishApprovalService(approvalApi, modelRepository);
  }

  @Test
  void submitBuildsCommandWithSnapshot() {
    when(modelRepository.findById(42L)).thenReturn(Optional.of(
        Model.create("dwd_order", "订单宽表", ModelDialect.MYSQL, null)));
    when(approvalApi.submit(any())).thenReturn(new ApprovalInstanceView(
        7L, ApprovalFlowCodes.MODEL_PUBLISH, "模型发布", "MODEL", "42", "t", null,
        "tom", "PENDING", 1, LocalDateTime.now(), null));

    service.submit(42L, "tom");

    ArgumentCaptor<ApprovalSubmitCommand> captor =
        ArgumentCaptor.forClass(ApprovalSubmitCommand.class);
    verify(approvalApi).submit(captor.capture());
    ApprovalSubmitCommand cmd = captor.getValue();
    assertEquals(ApprovalFlowCodes.MODEL_PUBLISH, cmd.flowCode());
    assertEquals("MODEL", cmd.bizType());
    assertEquals("42", cmd.bizId());
    assertTrue(cmd.title().contains("订单宽表"));
    assertTrue(cmd.payloadJson().contains("\"modelId\":42"));
    assertTrue(cmd.payloadJson().contains("订单宽表"));
    assertEquals("tom", cmd.applicant());
  }

  @Test
  void submitRejectsMissingModel() {
    when(modelRepository.findById(404L)).thenReturn(Optional.empty());
    assertThrows(ModelingException.class, () -> service.submit(404L, "tom"));
    verify(approvalApi, org.mockito.Mockito.never()).submit(any());
  }
}
