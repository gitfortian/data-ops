package io.yak.ops.business.security.approval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.approval.api.ApprovalApi;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.approval.api.ApprovalSubmitCommand;
import io.yak.ops.business.security.application.AccessPolicyService;
import io.yak.ops.business.security.exception.SecurityException;
import io.yak.ops.common.bean.po.security.DsecAccessPolicyPO;
import io.yak.ops.common.enums.security.SecurityErrorCode;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 权限申请审批发起(ticket 106):申请单本体即 AccessPolicy(自带 PENDING 态),
 * 本服务只负责把 PENDING 策略分流到审批中心;批准即执行既有 decideApproval。
 * 同一策略在途单唯一由审批中心 uk 保证(重复发起 → 49003)。
 */
@Service
@RequiredArgsConstructor
public class AccessPolicyApprovalService {

  public static final String BIZ_TYPE = "ACCESS_POLICY";
  static final String PAYLOAD_POLICY_ID = "policyId";
  static final String PAYLOAD_POLICY_NAME = "policyName";
  static final String PAYLOAD_SUBJECT = "subject";
  static final String PAYLOAD_RESOURCE = "resource";
  static final String PAYLOAD_ACCESS_TYPE = "accessType";
  static final String PAYLOAD_EFFECT = "effect";
  static final String PAYLOAD_VALID_TO = "validTo";

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final ApprovalApi approvalApi;
  private final AccessPolicyService policyService;

  public ApprovalInstanceView submit(Long policyId, String operator) {
    DsecAccessPolicyPO po = policyService.get(policyId);
    if (!"PENDING".equals(po.getStatus())) {
      throw new SecurityException(SecurityErrorCode.ACCESS_NOT_PENDING, po.getStatus());
    }
    String payload;
    try {
      payload = MAPPER.writeValueAsString(payloadOf(po));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("审批 payload 序列化失败", e);
    }
    return approvalApi.submit(new ApprovalSubmitCommand(
        ApprovalFlowCodes.ACCESS_GRANT, BIZ_TYPE, String.valueOf(policyId),
        "权限申请:" + po.getPolicyName(), payload, operator));
  }

  private static Map<String, Object> payloadOf(DsecAccessPolicyPO po) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(PAYLOAD_POLICY_ID, po.getId());
    payload.put(PAYLOAD_POLICY_NAME, po.getPolicyName());
    payload.put(PAYLOAD_SUBJECT, po.getSubjectType() + ":" + po.getSubjectKey());
    payload.put(PAYLOAD_RESOURCE, resourceOf(po));
    payload.put(PAYLOAD_ACCESS_TYPE, po.getAccessType());
    payload.put(PAYLOAD_EFFECT, po.getEffect());
    payload.put(PAYLOAD_VALID_TO,
        po.getValidTo() == null ? "长期" : DATE_TIME.format(po.getValidTo()));
    return payload;
  }

  private static String resourceOf(DsecAccessPolicyPO po) {
    StringBuilder resource = new StringBuilder();
    if (po.getDatasourceId() != null) {
      resource.append("数据源 ").append(po.getDatasourceId());
    }
    if (po.getDbName() != null) {
      resource.append(resource.length() > 0 ? " / " : "").append(po.getDbName());
    }
    if (po.getTableName() != null) {
      resource.append(resource.length() > 0 ? " / " : "").append(po.getTableName());
    }
    if (po.getColumnName() != null) {
      resource.append(resource.length() > 0 ? " / " : "").append(po.getColumnName());
    }
    if ("ALL".equals(po.getScopeType())) return "全部数据";
    return resource.length() > 0 ? resource.toString() : po.getScopeType();
  }
}
