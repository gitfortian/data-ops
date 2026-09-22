package io.yak.ops.business.semantic.catalog;

import io.yak.ops.business.semantic.api.Standard;

import io.yak.ops.business.semantic.api.StandardQueryApi;
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
}
