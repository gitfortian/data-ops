package io.yak.ops.business.quality.template;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.CustomTemplate;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.repository.CustomTemplateRepository;
import io.yak.ops.business.quality.repository.QualityTemplateCatalogRepository;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-side access to custom quality templates. */
@Component
@ConditionalOnQualityEnabled
public class CustomTemplateReader {
  private final CustomTemplateRepository repository;
  private final QualityTemplateCatalogRepository catalogRepository;

  public CustomTemplateReader(
      CustomTemplateRepository repository,
      QualityTemplateCatalogRepository catalogRepository) {
    this.repository = repository;
    this.catalogRepository = catalogRepository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public CustomTemplateList list(QualityQuery.CustomTemplate query) {
    QualityQuery.CustomTemplate normalized = query == null
        ? new QualityQuery.CustomTemplate(null, null, null, false)
        : query;
    var catalog = catalogRepository.global();
    var scope = catalogRepository.customScope(normalized.folderId(), normalized.folderFilter());
    return new CustomTemplateList(
        repository.list(normalized),
        new Summary(
            scope.total(),
            catalog.systemTotal(),
            catalog.customTotal(),
            scope.dimensions()));
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public CustomTemplate require(long id) {
    return repository.find(id)
        .orElseThrow(() -> new IllegalArgumentException("自定义规则模板不存在：" + id));
  }

  public record CustomTemplateList(List<CustomTemplate> records, Summary summary) {
    public CustomTemplateList {
      records = records == null ? List.of() : List.copyOf(records);
    }
  }

  public record Summary(
      long scopeTotal,
      long systemTotal,
      long customTotal,
      Map<String, Long> dimensions) {
    public Summary {
      dimensions = dimensions == null
          ? Map.of()
          : Collections.unmodifiableMap(new LinkedHashMap<>(dimensions));
    }
  }
}
