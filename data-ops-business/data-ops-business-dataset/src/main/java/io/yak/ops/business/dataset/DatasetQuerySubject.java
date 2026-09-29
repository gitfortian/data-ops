package io.yak.ops.business.dataset;

/**
 * Source-owned execution attribution captured when a Dataset query is admitted.
 * This remains neutral Dataset evidence; Consumption decides how to normalize it.
 */
public record DatasetQuerySubject(
    String subjectType,
    String sourceDomain,
    String sourceIdentity,
    String displayHint) {

  public DatasetQuerySubject {
    subjectType = requireText(subjectType, "subjectType");
    sourceDomain = requireText(sourceDomain, "sourceDomain");
    sourceIdentity = requireText(sourceIdentity, "sourceIdentity");
    displayHint = normalize(displayHint);
  }

  public static DatasetQuerySubject authenticatedUser(String principalName) {
    String identity = requireText(principalName, "principalName");
    return new DatasetQuerySubject("USER", "SECURITY_PRINCIPAL", identity, identity);
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
