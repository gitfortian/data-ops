package io.yak.ops.business.modeling.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.approval.api.ApprovalDecision;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalFlowHandler;
import io.yak.ops.business.modeling.version.ModelVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MODEL_PUBLISH 终态回调(onApproved = 业务生效点,与审批同事务 D5):
 * 批准即按发起人执行既有版本发布,链路零改动。抛错 → 49009 整体回滚,审批人重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelPublishApprovalHandler implements ApprovalFlowHandler {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ModelVersionService versionService;

  @Override
  public String flowCode() {
    return ApprovalFlowCodes.MODEL_PUBLISH;
  }

  @Override
  public void onApproved(ApprovalDecision decision) {
    long modelId = readModelId(decision.payloadJson());
    String fingerprint = readStructureFingerprint(decision.payloadJson());
    ModelVersionService.PublishResult result =
        versionService.publishApproved(modelId, fingerprint, decision.applicant());
    log.info("模型发布审批通过即发布: modelId={}, versionNo={}, created={}, applicant={}",
        modelId, result.version().versionNo(), result.created(), decision.applicant());
  }

  private static String readStructureFingerprint(String payloadJson) {
    try {
      String value = MAPPER.readTree(payloadJson)
          .path(ModelPublishApprovalService.PAYLOAD_STRUCTURE_FINGERPRINT).asText(null);
      if (value == null || !value.matches("[a-f0-9]{64}")) {
        throw new IllegalStateException("payload 缺少有效 structureFingerprint");
      }
      return value;
    } catch (Exception e) {
      throw new IllegalStateException("模型送审版本解析失败:" + e.getMessage(), e);
    }
  }

  private static long readModelId(String payloadJson) {
    try {
      JsonNode node = MAPPER.readTree(payloadJson)
          .path(ModelPublishApprovalService.PAYLOAD_MODEL_ID);
      if (!node.canConvertToLong()) {
        throw new IllegalStateException("payload 缺少 modelId: " + payloadJson);
      }
      return node.asLong();
    } catch (Exception e) {
      throw new IllegalStateException("审批 payload 解析失败:" + e.getMessage(), e);
    }
  }
}
