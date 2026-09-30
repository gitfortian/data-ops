package io.yak.ops.business.dataset.gateway.security;

import java.util.List;

/** Dataset-owned boundary for decisions, masking instructions and actual consumption evidence. */
public interface DatasetSecurityGateway {

  String columnObjectKey(String datasourceId, String database, String table, String column);

  Decision decide(String actor, List<String> roles, String objectKey, String action);

  Classification classify(String objectKey);

  MaskingInstruction resolveMasking(String objectKey);

  String mask(String value, MaskingInstruction instruction);

  void recordAccess(
      String actor,
      String objectKey,
      String action,
      Decision decision,
      boolean maskingApplied,
      String source);

  record Decision(
      boolean allowed,
      String decision,
      Long matchedPolicyId,
      boolean maskingRequired,
      String algorithmCode) {}

  record Classification(boolean active, Integer rank) {}

  record MaskingInstruction(boolean required, String algorithmCode, String parameters) {
    public static MaskingInstruction none() {
      return new MaskingInstruction(false, null, null);
    }
  }
}
