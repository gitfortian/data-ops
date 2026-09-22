package io.yak.ops.business.modeling.approval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.approval.api.ApprovalApi;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.approval.api.ApprovalSubmitCommand;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 模型发布审批发起(ticket 104):审批中心只存展示快照,模型真相仍在 modeling。
 * 同一模型在途单唯一由审批中心 uk 保证(重复发起 → 49003)。
 */
@Service
@RequiredArgsConstructor
public class ModelPublishApprovalService {

  public static final String BIZ_TYPE = "MODEL";
  static final String PAYLOAD_MODEL_ID = "modelId";
  static final String PAYLOAD_MODEL_NAME = "modelName";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ApprovalApi approvalApi;
  private final ModelRepository modelRepository;

  public ApprovalInstanceView submit(Long modelId, String operator) {
    Model model = modelRepository.findById(modelId)
        .orElseThrow(() -> new ModelingException(
            ModelingErrorCode.NOT_FOUND, "模型不存在或已删除:" + modelId));
    String payload;
    try {
      payload = MAPPER.writeValueAsString(Map.of(
          PAYLOAD_MODEL_ID, modelId,
          PAYLOAD_MODEL_NAME, model.name() == null ? "" : model.name()));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("审批 payload 序列化失败", e);
    }
    return approvalApi.submit(new ApprovalSubmitCommand(
        ApprovalFlowCodes.MODEL_PUBLISH, BIZ_TYPE, String.valueOf(modelId),
        "模型发布申请:" + model.name(), payload, operator));
  }
}
