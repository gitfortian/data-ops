package io.yak.ops.business.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.yak.ops.business.asset.api.AssetSectionResult;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionType;
import java.util.List;
import java.util.Set;

/** Category-specific scalar projection; a missing source never becomes a readable zero. */
final class AssetImpactProjection {
  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private enum Category {
    PAGE_ACTIVITY("pageActivity", "ASSET", Set.of("windowDays", "meaning", "viewCount", "distinctViewerCount", "lastViewedAt")),
    STRUCTURAL_USAGE("structuralUsage", "LINEAGE", Set.of("direction", "hop", "downstreamReferenceCount")),
    BUSINESS_CONSUMPTION("businessConsumption", "FEDERATED", Set.of("scope", "totalCount", "reportCount", "datasetCount",
        "dashboardCount", "apiCount", "screenCount", "consumerCount", "userCount", "teamCount", "dataServiceCount",
        "jobCount", "successfulUsageCount", "activeSubscriptionCount", "lastObservedAt", "coverageNote"));

    private final String field;
    private final String owner;
    private final Set<String> fields;
    Category(String field, String owner, Set<String> fields) {
      this.field = field; this.owner = owner; this.fields = fields;
    }
    boolean acceptsOwner(String suppliedOwner) {
      return this == BUSINESS_CONSUMPTION ? Set.of("METRIC", "CONSUMING_DOMAINS").contains(suppliedOwner)
          : owner.equals(suppliedOwner);
    }
  }

  private AssetImpactProjection() {}
  record Part(String category, String owner, String status, String facts) {}

  static List<Part> failure(String status) {
    return java.util.Arrays.stream(Category.values())
        .map(category -> new Part(category.field, category.owner, status, "{}")).toList();
  }

  static List<Part> project(AssetSectionResult result) {
    if (result == null || result.sectionType() != SectionType.USAGE || result.status() == null) {
      return failure("UNAVAILABLE");
    }
    if (result.status() != SectionStatus.OK && result.status() != SectionStatus.EMPTY) {
      return failure(result.status().name());
    }
    JsonNode values = result.summary() == null ? JSON.nullNode() : JSON.valueToTree(result.summary().values());
    return java.util.Arrays.stream(Category.values()).map(category -> part(values.path(category.field), category)).toList();
  }

  private static Part part(JsonNode source, Category category) {
    String owner = category.owner;
    try {
      if (!source.isObject()) throw new IllegalArgumentException();
      String suppliedOwner = source.path("ownerDomain").asText();
      if (!category.acceptsOwner(suppliedOwner)) {
        throw new IllegalArgumentException();
      }
      owner = suppliedOwner;
      String status = SectionStatus.valueOf(source.path("status").asText()).name();
      if (!"OK".equals(status) && !"EMPTY".equals(status)) return new Part(category.field, owner, status, "{}");
      var facts = JSON.createObjectNode().put("ownerDomain", owner).put("status", status);
      for (String field : category.fields) {
        JsonNode value = source.get(field);
        if (value == null) continue;
        if (!value.isNull() && !(value.isTextual() || value.isIntegralNumber())) throw new IllegalArgumentException();
        if (value.isTextual() && value.asText().length() > 512) throw new IllegalArgumentException();
        if (field.endsWith("Count") || "windowDays".equals(field) || "hop".equals(field)) {
          if (!value.isNull() && (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0)) {
            throw new IllegalArgumentException();
          }
        } else if (!value.isNull() && !value.isTextual()) throw new IllegalArgumentException();
        facts.set(field, value);
      }
      if (category == Category.PAGE_ACTIVITY && (!facts.path("windowDays").isIntegralNumber() || facts.path("windowDays").longValue() <= 0)) throw new IllegalArgumentException();
      if (category == Category.STRUCTURAL_USAGE && (!"DOWNSTREAM".equals(facts.path("direction").asText())
          || !facts.path("hop").isIntegralNumber() || facts.path("hop").longValue() != 1
          || !facts.path("downstreamReferenceCount").isIntegralNumber())) throw new IllegalArgumentException();
      if (category == Category.BUSINESS_CONSUMPTION && (!facts.path("scope").isTextual() || facts.path("scope").asText().isBlank())) throw new IllegalArgumentException();
      String encoded = JSON.writeValueAsString(facts);
      if (encoded.length() > 6000) throw new IllegalArgumentException();
      return new Part(category.field, owner, status, encoded);
    } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException invalid) {
      return new Part(category.field, owner, "UNAVAILABLE", "{}");
    }
  }
}
