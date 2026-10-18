package io.yak.ops.business.semantic.catalog;

import io.yak.ops.business.semantic.api.Standard;

import io.yak.ops.business.semantic.api.StandardQueryApi;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Default StandardQueryApi implementation. */
@Component
@RequiredArgsConstructor
public class StandardQueryApiImpl implements StandardQueryApi {

  private final SemanticStandardRepository standardRepository;

  @Override
  public Standard get(Long standardId) {
    return standardRepository.findById(standardId).orElse(null);
  }

  @Override
  public Map<Long, String> labels(Collection<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return Map.of();
    }
    return standardRepository.findByIds(ids).stream()
        .collect(
            Collectors.toMap(
                Standard::id,
                standard -> standard.name() + "（" + standard.code() + "）",
                (first, second) -> first));
  }

  @Override
  public boolean existsCodeSet(String codeSetCode) {
    return standardRepository.existsEnabledByCodeSet(codeSetCode);
  }

  @Override
  public StandardQueryApi.CodeSetReference codeSetReference(String codeSetCode) {
    if (codeSetCode == null || codeSetCode.isBlank()) {
      return new StandardQueryApi.CodeSetReference(codeSetCode, 0, 0, "NOT_FOUND", null);
    }
    var rows = standardRepository.listByCodeSetCode(codeSetCode);
    if (rows == null || rows.isEmpty()) {
      return new StandardQueryApi.CodeSetReference(codeSetCode, 0, 0, "NOT_FOUND", null);
    }
    var codeRows = rows.stream()
        .filter(row -> row.kind() == StandardKind.CODE)
        .filter(row -> row.fields() != null && codeSetCode.equals(row.fields().codeSetCode()))
        .toList();
    if (codeRows.isEmpty()) {
      return new StandardQueryApi.CodeSetReference(codeSetCode, 0, 0, "NOT_FOUND", null);
    }
    int enabled = (int) codeRows.stream()
        .filter(row -> row.status() == StandardStatus.ENABLED)
        .count();
    String status = enabled == codeRows.size() ? "ENABLED"
        : enabled == 0 ? "DISABLED" : "MIXED";
    return new StandardQueryApi.CodeSetReference(
        codeSetCode, codeRows.size(), enabled, status, CodeSetRevision.of(codeRows));
  }
}
