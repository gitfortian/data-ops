package io.yak.ops.business.modeling.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.approval.api.ApprovalDecision;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalFlowHandler;
import io.yak.ops.business.modeling.version.ModelVersionService;
import io.yak.ops.business.modeling.mapping.DwdMappingPublicationService;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MODEL_PUBLISH 终态回调(onApproved = 业务生效点,与审批同事务 D5):
 * 批准即按发起人执行既有版本发布,链路零改动。抛错 → 49009 整体回滚,审批人重试。
 */
@Slf4j
@Component
public class ModelPublishApprovalHandler implements ApprovalFlowHandler {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ModelVersionService versionService;
  private final DwdMappingPublicationService dwdPublisher;

  @Autowired
  public ModelPublishApprovalHandler(ModelVersionService versionService,
                                     DwdMappingPublicationService dwdPublisher) {
    this.versionService = versionService;
    this.dwdPublisher = dwdPublisher;
  }

  /** Backwards-compatible constructor for legacy no-binding unit fixtures. */
  public ModelPublishApprovalHandler(ModelVersionService versionService) {
    this(versionService, null);
  }

  @Override
  public String flowCode() {
    return ApprovalFlowCodes.MODEL_PUBLISH;
  }

  @Override
  public void onApproved(ApprovalDecision decision) {
    long modelId = readModelId(decision.payloadJson());
    String fingerprint = readStructureFingerprint(decision.payloadJson());
    Long revisionId = readOptionalMappingRevisionId(decision.payloadJson());
    if (revisionId != null) {
      if (dwdPublisher == null) throw new IllegalStateException("DWD 映射发布处理器未配置");
      var result = dwdPublisher.publishApproved(modelId, revisionId,
          readMappingContentSha(decision.payloadJson()), fingerprint, decision.applicant());
      log.info("DWD 映射设计审批发布: modelId={}, versionNo={}, created={}",
          modelId, result.versionNo(), result.created());
    } else {
      ModelVersionService.PublishResult result =
          versionService.publishApproved(modelId, fingerprint, decision.applicant());
      log.info("模型发布审批通过即发布: modelId={}, versionNo={}, created={}, applicant={}",
          modelId, result.version().versionNo(), result.created(), decision.applicant());
    }
  }

  private static String readMappingContentSha(String payloadJson) {
    try {
      String digest = MAPPER.readTree(payloadJson).path("mappingContentSha256").asText(null);
      if (digest == null || !digest.matches("[a-f0-9]{64}")) {
        throw new IllegalStateException("审批映射内容指纹缺失或无效");
      }
      return digest;
    } catch (Exception ex) {
      throw new IllegalStateException("审批映射证据解析失败", ex);
    }
  }

  private static Long readOptionalMappingRevisionId(String payloadJson) {
    try {
      JsonNode payload = MAPPER.readTree(payloadJson);
      if (!payload.has("mappingRevisionId")) {
        if (payload.has("mappingContentSha256")) {
          throw new IllegalStateException("审批映射指纹存在但 MappingRevision ID 缺失");
        }
        return null;
      }
      JsonNode id = payload.path("mappingRevisionId");
      if (!id.isIntegralNumber() || id.asLong() <= 0) {
        throw new IllegalStateException("审批中的 MappingRevision ID 非法");
      }
      return id.asLong();
    } catch (Exception ex) {
      throw new IllegalStateException("审批映射版本解析失败", ex);
    }
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
