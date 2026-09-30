package io.yak.ops.business.dataset.gateway.security;

import io.yak.ops.business.security.api.AccessDecision;
import io.yak.ops.business.security.api.ClassificationView;
import io.yak.ops.business.security.api.MaskingDirective;
import io.yak.ops.business.security.api.SecurityAccessDecisionApi;
import io.yak.ops.business.security.api.SecurityClassificationQueryApi;
import io.yak.ops.business.security.api.SecurityMaskingApi;
import io.yak.ops.business.security.api.SecurityObjectKey;
import java.util.List;
import org.springframework.stereotype.Component;

/** Adapts the Security-owned SPI to Dataset's query-security boundary. */
@Component
public class SecurityDatasetAdapter implements DatasetSecurityGateway {

  private final SecurityAccessDecisionApi accessDecisionApi;
  private final SecurityClassificationQueryApi classificationQueryApi;
  private final SecurityMaskingApi maskingApi;

  public SecurityDatasetAdapter(
      SecurityAccessDecisionApi accessDecisionApi,
      SecurityClassificationQueryApi classificationQueryApi,
      SecurityMaskingApi maskingApi) {
    this.accessDecisionApi = accessDecisionApi;
    this.classificationQueryApi = classificationQueryApi;
    this.maskingApi = maskingApi;
  }

  @Override
  public String columnObjectKey(String datasourceId, String database, String table, String column) {
    return SecurityObjectKey.column(datasourceId, database, table, column);
  }

  @Override
  public Decision decide(String actor, List<String> roles, String objectKey, String action) {
    AccessDecision decision = accessDecisionApi.decide(actor, roles, objectKey, action);
    return new Decision(
        decision.allowed(), decision.decision(), decision.matchedPolicyId(),
        decision.maskingRequired(), decision.algoCode());
  }

  @Override
  public Classification classify(String objectKey) {
    ClassificationView view = classificationQueryApi.find(objectKey);
    return view == null ? null : new Classification(
        "ACTIVE".equals(view.status()), view.levelRank());
  }

  @Override
  public MaskingInstruction resolveMasking(String objectKey) {
    MaskingDirective directive = maskingApi.resolve(objectKey);
    return new MaskingInstruction(directive.mask(), directive.algoCode(), directive.algoParams());
  }

  @Override
  public String mask(String value, MaskingInstruction instruction) {
    return maskingApi.mask(value, instruction.algorithmCode(), instruction.parameters());
  }

  @Override
  public void recordAccess(
      String actor,
      String objectKey,
      String action,
      Decision decision,
      boolean maskingApplied,
      String source) {
    accessDecisionApi.recordAccess(
        actor,
        objectKey,
        action,
        new AccessDecision(
            decision.allowed(), decision.decision(), decision.matchedPolicyId(),
            decision.maskingRequired(), decision.algorithmCode()),
        maskingApplied,
        source);
  }
}
