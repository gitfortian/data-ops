package io.yak.ops.business.semantic.approval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.approval.api.ApprovalApi;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.approval.api.ApprovalSubmitCommand;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.catalog.StandardCatalogService;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 标准生效审批发起(ticket 105):批准即执行既有 changeStatus→ENABLED。
 * 同一标准在途单唯一由审批中心 uk 保证(重复发起 → 49003)。
 */
@Service
@RequiredArgsConstructor
public class StandardPublishApprovalService {

  public static final String BIZ_TYPE = "STANDARD";
  static final String PAYLOAD_STANDARD_ID = "standardId";
  static final String PAYLOAD_STANDARD_CODE = "standardCode";
  static final String PAYLOAD_STANDARD_NAME = "standardName";

  private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

  private final ApprovalApi approvalApi;
  private final StandardCatalogService catalogService;

  @org.springframework.transaction.annotation.Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public ApprovalInstanceView submit(Long standardId, String operator) {
    Standard standard = catalogService.lockDefinition(standardId);
    if (standard.kind() == io.yak.ops.business.semantic.api.StandardKind.CODE) {
      throw new SemanticException(SemanticErrorCode.INVALID_KIND, "码集不支持单标准发布审批");
    }
    if (standard.status() == StandardStatus.ENABLED) {
      // 生效审批只服务 DISABLED→ENABLED;已启用标准再提一单会在批准后重复走 changeStatus。
      throw new SemanticException(SemanticErrorCode.PUBLISH_ALREADY_ENABLED, standard.code());
    }
    String payload;
    try {
      payload = MAPPER.writeValueAsString(Map.of(
          PAYLOAD_STANDARD_ID, standardId,
          PAYLOAD_STANDARD_CODE, standard.code() == null ? "" : standard.code(),
          PAYLOAD_STANDARD_NAME, standard.name() == null ? "" : standard.name(),
          "standardVersion", standard.version(),
          "definition", standard));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("审批 payload 序列化失败", e);
    }
    return approvalApi.submit(new ApprovalSubmitCommand(
        ApprovalFlowCodes.STANDARD_PUBLISH, BIZ_TYPE, String.valueOf(standardId),
        "标准生效申请:" + standard.name(), payload, operator));
  }
}
