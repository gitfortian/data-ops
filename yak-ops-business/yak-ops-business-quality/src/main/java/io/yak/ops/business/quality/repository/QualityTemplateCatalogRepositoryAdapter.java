package io.yak.ops.business.quality.repository;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.dao.QualityCatalogDao;
import io.yak.ops.business.quality.dao.QualityCatalogDao.TemplateCount;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnQualityEnabled
@DependsOn("qualityFlyway")
public class QualityTemplateCatalogRepositoryAdapter implements QualityTemplateCatalogRepository {
  private final QualityCatalogDao catalogDao;

  @Override
  public CatalogSummary global() {
    List<TemplateCount> counts = catalogDao.selectTemplateCounts(null, null, false);
    Map<String, Long> systemDimensions = new LinkedHashMap<>();
    Map<String, Long> customDimensions = new LinkedHashMap<>();
    long systemTotal = 0L;
    long customTotal = 0L;
    for (TemplateCount count : counts) {
      if (count.builtin()) {
        systemTotal += count.count();
        systemDimensions.merge(count.dimension(), count.count(), Long::sum);
      } else {
        customTotal += count.count();
        customDimensions.merge(count.dimension(), count.count(), Long::sum);
      }
    }
    return new CatalogSummary(systemTotal, customTotal, systemDimensions, customDimensions);
  }

  @Override
  public ScopeSummary customScope(Long folderId, boolean folderFilter) {
    List<TemplateCount> counts = catalogDao.selectTemplateCounts(false, folderId, folderFilter);
    Map<String, Long> dimensions = new LinkedHashMap<>();
    long total = 0L;
    for (TemplateCount count : counts) {
      total += count.count();
      dimensions.merge(count.dimension(), count.count(), Long::sum);
    }
    return new ScopeSummary(total, dimensions);
  }
}
