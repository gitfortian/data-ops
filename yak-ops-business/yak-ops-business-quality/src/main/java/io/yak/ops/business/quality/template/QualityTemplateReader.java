package io.yak.ops.business.quality.template;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Template;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.repository.QualityTemplateCatalogRepository;
import io.yak.ops.business.quality.repository.QualityTemplateRepository;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-side access to built-in quality rule templates. */
@Component
@ConditionalOnQualityEnabled
public class QualityTemplateReader {
  private final QualityTemplateRepository repository;
  private final QualityTemplateCatalogRepository catalogRepository;

  public QualityTemplateReader(
      QualityTemplateRepository repository,
      QualityTemplateCatalogRepository catalogRepository) {
    this.repository = repository;
    this.catalogRepository = catalogRepository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public TemplateList list(QualityQuery.Template query) {
    var catalog = catalogRepository.global();
    Map<String, Long> dimensions = new LinkedHashMap<>(catalog.systemDimensions());
    catalog.customDimensions().forEach((dimension, count) -> dimensions.merge(dimension, count, Long::sum));
    List<Template> records = repository.listTemplates(
        query == null ? new QualityQuery.Template(null, null, null) : query);
    return new TemplateList(
        records,
        new Summary(catalog.systemTotal() + catalog.customTotal(), dimensions));
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public CatalogSummary catalog() {
    var value = catalogRepository.global();
    return new CatalogSummary(
        value.systemTotal(),
        value.customTotal(),
        value.systemDimensions(),
        value.customDimensions());
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public Template require(long id) {
    return repository.findTemplate(id)
        .orElseThrow(() -> new IllegalArgumentException("规则模板不存在：" + id));
  }

  public record TemplateList(List<Template> records, Summary summary) {
    public TemplateList {
      records = records == null ? List.of() : List.copyOf(records);
    }
  }

  public record Summary(long total, Map<String, Long> dimensions) {
    public Summary {
      dimensions = immutable(dimensions);
    }
  }

  public record CatalogSummary(
      long systemTotal,
      long customTotal,
      Map<String, Long> systemDimensions,
      Map<String, Long> customDimensions) {
    public CatalogSummary {
      systemDimensions = immutable(systemDimensions);
      customDimensions = immutable(customDimensions);
    }
  }

  private static Map<String, Long> immutable(Map<String, Long> values) {
    return values == null
        ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }
}
