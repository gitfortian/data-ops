package io.yak.ops.business.agent.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.yak.ops.business.agent.domain.ConsumerVersionImpactTarget;
import io.yak.ops.business.consumption.api.ConsumerVersionImpactQueryApi;
import io.yak.ops.spi.section.SectionStatus;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixed identities and small source windows; unreadable parts never expose payload. */
final class ConsumerVersionImpactProjection {
  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  record Part(String category, String status, String facts) {}
  static List<Part> failure(String status) {
    return List.of(new Part("versionMembership", status, "{}"), new Part("subscriptions", status, "{}"), new Part("versionUsage", status, "{}"));
  }
  static List<Part> project(ConsumerVersionImpactTarget target, ConsumerVersionImpactQueryApi.Result result) {
    if (result == null || !target.productType().equals(result.productType())
        || !target.productIdentity().equals(result.productIdentity())
        || !target.sourceVersionIdentity().equals(result.sourceVersionIdentity()) || result.status() == null) return failure("UNAVAILABLE");
    if (result.status() != SectionStatus.OK) return failure(result.status().name());
    if (result.membershipBasis() == null || !Set.of("ACTIVE_SOURCE_REFERENCE", "NORMALIZED_SUCCESS_REFERENCE").contains(result.membershipBasis())) return failure("UNAVAILABLE");
    return List.of(new Part("versionMembership", "OK", encode(Map.of("productType", target.productType(),
        "productIdentity", target.productIdentity(), "sourceVersionIdentity", target.sourceVersionIdentity(),
        "membershipBasis", result.membershipBasis(), "sourceReconciliation", "NOT_PERFORMED"))),
        window("subscriptions", result.subscriptions(), false), window("versionUsage", result.usage(), true));
  }
  private static Part window(String category, ConsumerVersionImpactQueryApi.Window window, boolean observed) {
    try {
      if (window == null || window.status() == null) throw new IllegalArgumentException();
      if (window.status() != SectionStatus.OK && window.status() != SectionStatus.EMPTY) return new Part(category, window.status().name(), "{}");
      int limit = ConsumerVersionImpactQueryApi.WINDOW_LIMIT;
      if (window.recordCount() < 0 || window.recordCount() > limit || window.consumers().size() > limit
          || (window.status() == SectionStatus.EMPTY) != (window.recordCount() == 0)
          || !(window.recordCount() == limit ? "LIMIT_REACHED" : "WITHIN_LIMIT").equals(window.windowState())) throw new IllegalArgumentException();
      Set<List<String>> identities = new HashSet<>(); int sum = 0;
      for (var row : window.consumers()) {
        if (row == null || !Set.of("USER", "TEAM", "DASHBOARD", "DATA_SERVICE", "JOB").contains(row.consumerType())
            || !text(row.sourceDomain()) || !text(row.sourceIdentity()) || row.recordCount() < 1 || row.recordCount() > limit
            || (!observed && row.lastObservedAt() != null) || (observed && row.lastObservedAt() == null)
            || !identities.add(List.of(row.consumerType(), row.sourceDomain(), row.sourceIdentity()))) throw new IllegalArgumentException();
        sum += row.recordCount();
      }
      if (sum != window.recordCount()) throw new IllegalArgumentException();
      var facts = new LinkedHashMap<String, Object>();
      facts.put("scope", observed ? "所选来源版本的归一化成功使用窗口" : "产品有效声明窗口；声明不绑定来源版本");
      facts.put("recordCount", window.recordCount()); facts.put("windowLimit", limit);
      facts.put("windowState", window.windowState()); facts.put("sourceReconciliation", "NOT_PERFORMED");
      facts.put("consumers", window.consumers());
      return new Part(category, window.status().name(), encode(facts));
    } catch (RuntimeException invalid) { return new Part(category, "UNAVAILABLE", "{}"); }
  }
  private static boolean text(String text) { return text != null && !text.isBlank() && text.length() <= 128; }
  private static String encode(Object facts) {
    try {
      String encoded = JSON.writeValueAsString(facts);
      if (encoded.length() > 6000) throw new IllegalArgumentException();
      return encoded;
    } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalArgumentException(); }
  }
}
