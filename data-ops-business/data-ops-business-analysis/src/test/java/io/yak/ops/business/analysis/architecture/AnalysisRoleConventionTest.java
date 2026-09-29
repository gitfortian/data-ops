package io.yak.ops.business.analysis.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.analysis.AnalysisReferenceService;
import io.yak.ops.business.analysis.AnalysisService;
import io.yak.ops.business.analysis.controller.v1.converter.AnalysisRequestConverter;
import io.yak.ops.business.analysis.controller.v1.converter.AnalysisViewConverter;
import io.yak.ops.business.analysis.definition.AnalysisDefinitionNormalizer;
import io.yak.ops.business.analysis.definition.AnalysisManager;
import io.yak.ops.business.analysis.definition.AnalysisReader;
import io.yak.ops.business.analysis.gateway.dataset.DatasetAnalysisAdapter;
import io.yak.ops.business.analysis.gateway.lineage.LineageAnalysisAdapter;
import io.yak.ops.business.analysis.lineage.AnalysisFieldUsageExtractor;
import io.yak.ops.business.analysis.lineage.AnalysisLineageRefreshListener;
import io.yak.ops.business.analysis.lineage.AnalysisLineageSynchronizer;
import io.yak.ops.business.analysis.query.AnalysisFieldReferenceCollector;
import io.yak.ops.business.analysis.query.AnalysisQueryNormalizer;
import io.yak.ops.business.analysis.reference.AnalysisReferenceReader;
import io.yak.ops.business.analysis.repository.AnalysisRepositoryAdapter;
import io.yak.ops.business.analysis.repository.codec.AnalysisJsonCodec;
import io.yak.ops.business.analysis.visualization.AnalysisChartBindingPolicy;
import io.yak.ops.business.analysis.visualization.AnalysisVisualPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

class AnalysisRoleConventionTest {

  @Test
  void stableApplicationFacadesRemainServices() {
    for (Class<?> role : List.of(AnalysisService.class, AnalysisReferenceService.class)) {
      assertThat(role.getAnnotation(Service.class))
          .as("%s is a stable Analysis facade", role.getSimpleName())
          .isNotNull();
    }
  }

  @Test
  void internalRolesRemainExplicitComponents() {
    for (Class<?> role : List.of(
        AnalysisManager.class,
        AnalysisReader.class,
        AnalysisDefinitionNormalizer.class,
        AnalysisQueryNormalizer.class,
        AnalysisFieldReferenceCollector.class,
        AnalysisChartBindingPolicy.class,
        AnalysisVisualPolicy.class,
        AnalysisReferenceReader.class,
        AnalysisFieldUsageExtractor.class,
        AnalysisLineageRefreshListener.class,
        AnalysisLineageSynchronizer.class,
        DatasetAnalysisAdapter.class,
        LineageAnalysisAdapter.class,
        AnalysisJsonCodec.class,
        AnalysisRequestConverter.class,
        AnalysisViewConverter.class)) {
      assertThat(role.getAnnotation(Component.class))
          .as("%s must remain an explicit internal component role", role.getSimpleName())
          .isNotNull();
      assertThat(role.getAnnotation(Service.class))
          .as("%s must not masquerade as generic Application Service", role.getSimpleName())
          .isNull();
    }
  }

  @Test
  void persistenceAdapterRemainsRepositoryRole() {
    assertThat(AnalysisRepositoryAdapter.class.getAnnotation(Repository.class)).isNotNull();
    assertThat(AnalysisRepositoryAdapter.class.getAnnotation(Service.class)).isNull();
  }
}
