package io.yak.ops.business.semantic.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.approval.api.ApprovalDecision;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalFlowHandler;
import io.yak.ops.business.semantic.catalog.StandardCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** STANDARD_PUBLISH 终态回调:批准后按发起人执行既有 changeStatus→ENABLED(同事务 D5)。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StandardPublishApprovalHandler implements ApprovalFlowHandler {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final StandardCatalogService catalogService;

  @Override
  public String flowCode() {
    return ApprovalFlowCodes.STANDARD_PUBLISH;
  }

  @Override
  public void onApproved(ApprovalDecision decision) {
    long standardId = readStandardId(decision.payloadJson());
    try {
      JsonNode payload = MAPPER.readTree(decision.payloadJson());
      var current = catalogService.lockDefinition(standardId);
      if (!payload.path("standardVersion").isInt()
          || payload.path("standardVersion").asInt() != current.version()) {
        throw new io.yak.ops.business.semantic.exception.SemanticException(
            io.yak.ops.common.enums.semantic.SemanticErrorCode.VERSION_CONFLICT,
            "送审版本已失效，请撤销后重新提交");
      }
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalStateException("审批定义快照解析失败", exception);
    }
    catalogService.enableAfterApproval(standardId, decision.applicant());
    log.info("标准生效审批通过即启用: standardId={}, applicant={}", standardId,
        decision.applicant());
  }

  private static long readStandardId(String payloadJson) {
    try {
      JsonNode node = MAPPER.readTree(payloadJson)
          .path(StandardPublishApprovalService.PAYLOAD_STANDARD_ID);
      if (!node.canConvertToLong()) {
        throw new IllegalStateException("payload 缺少 standardId: " + payloadJson);
      }
      return node.asLong();
    } catch (Exception e) {
      throw new IllegalStateException("审批 payload 解析失败:" + e.getMessage(), e);
    }
  }
}
