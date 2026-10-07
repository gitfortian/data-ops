package io.yak.ops.business.semantic.api;

import java.util.List;

/** Authorized, bounded candidate discovery and live validation; never creates or changes standards. */
public interface StandardSuggestionQueryApi {
  Pool types(String keyword);
  TypeCandidate requireType(long id, int expectedVersion);

  record TypeCandidate(long id, int version, String code, String name, String stdType, String description) {}
  record Pool(List<TypeCandidate> candidates, boolean truncated) {}
}
