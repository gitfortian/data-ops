package io.yak.ops.business.quality.repository;

import io.yak.ops.business.quality.domain.QualityDomain.Template;
import io.yak.ops.business.quality.domain.QualityQuery;
import java.util.List;
import java.util.Optional;

/** Read-side repository for built-in quality templates. */
public interface QualityTemplateRepository {
  List<Template> listTemplates(QualityQuery.Template query);
  Optional<Template> findTemplate(long id);
}
