package io.yak.ops.business.metric.impact;

import io.yak.ops.business.metric.repository.MetricDependencyRepository;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardReferenceReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Protects semantic unit and caliber standards referenced by published metric dependencies. */
@Component
@RequiredArgsConstructor
public class MetricStandardReferenceReader implements StandardReferenceReader {

  private final MetricDependencyRepository dependencies;

  @Override
  public long countReferences(StandardKind kind, Long standardId, String codeSetCode) {
    return switch (kind) {
      case UNIT -> dependencies.countByDependency("UNIT", standardId);
      case CALIBER -> dependencies.countByDependency("CALIBER", standardId);
      default -> 0;
    };
  }
}
