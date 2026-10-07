package io.yak.ops.business.semantic.catalog;

import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.business.semantic.api.StandardSuggestionQueryApi;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import io.yak.ops.common.constant.semantic.SemanticPermissionCode;
import io.yak.ops.core.security.ActionAuthorization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StandardSuggestionQueryAdapter implements StandardSuggestionQueryApi {
  private final SemanticStandardRepository standards;
  private final ActionAuthorization authorization;

  @Override public Pool types(String keyword) {
    authorization.requirePermission(SemanticPermissionCode.READ);
    if (keyword != null && keyword.length() > 64) throw new IllegalArgumentException("标准检索词最多64字");
    var rows = standards.searchTypeCandidates(keyword == null ? "" : keyword.trim());
    return new Pool(rows.stream().limit(20).map(StandardSuggestionQueryAdapter::candidate).toList(), rows.size() > 20);
  }

  @Override public TypeCandidate requireType(long id, int expectedVersion) {
    authorization.requirePermission(SemanticPermissionCode.READ);
    var standard = standards.findById(id).orElseThrow(() -> new IllegalArgumentException("标准已删除或不属于当前项目"));
    if (standard.kind() != StandardKind.TYPE || standard.status() != StandardStatus.ENABLED
        || standard.version() != expectedVersion) throw new IllegalArgumentException("标准类别、启停或版本已变化，请重新生成");
    return candidate(standard);
  }

  private static TypeCandidate candidate(Standard standard) {
    String description = standard.description();
    return new TypeCandidate(standard.id(), standard.version(), standard.code(), standard.name(),
        standard.fields().stdType(), description == null ? "" : description.substring(0, Math.min(512, description.length())));
  }
}
