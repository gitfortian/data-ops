package io.yak.ops.business.dataset;

import java.util.List;

/**
 * Source-owned execution attribution captured when a Dataset query is admitted.
 * This remains neutral Dataset evidence; Consumption decides how to normalize it.
 */
public record DatasetQuerySubject(
    String subjectType,
    String sourceDomain,
    String sourceIdentity,
    String displayHint,
    List<String> roles) {

  public DatasetQuerySubject {
    subjectType = requireText(subjectType, "subjectType");
    sourceDomain = requireText(sourceDomain, "sourceDomain");
    sourceIdentity = requireText(sourceIdentity, "sourceIdentity");
    displayHint = normalize(displayHint);
    roles = roles == null ? List.of() : roles.stream()
        .filter(role -> role != null && !role.isBlank())
        .map(String::trim)
        .distinct()
        .toList();
  }

  public DatasetQuerySubject(
      String subjectType, String sourceDomain, String sourceIdentity, String displayHint) {
    this(subjectType, sourceDomain, sourceIdentity, displayHint, List.of());
  }

  public static DatasetQuerySubject authenticatedUser(String principalName) {
    String identity = requireText(principalName, "principalName");
    return new DatasetQuerySubject("USER", "SECURITY_PRINCIPAL", identity, identity, List.of());
  }

  public static DatasetQuerySubject authenticatedUser(String principalName, List<String> roles) {
    String identity = requireText(principalName, "principalName");
    return new DatasetQuerySubject("USER", "SECURITY_PRINCIPAL", identity, identity, roles);
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
