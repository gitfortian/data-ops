package io.yak.ops.business.quality.template;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.TemplateFolder;
import io.yak.ops.business.quality.repository.CustomTemplateRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-side access to custom-template folders. */
@Component
@ConditionalOnQualityEnabled
public class TemplateFolderReader {
  private final CustomTemplateRepository repository;

  public TemplateFolderReader(CustomTemplateRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public List<TemplateFolder> list() {
    return repository.listFolders();
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public TemplateFolder require(long id) {
    return repository.findFolder(id)
        .orElseThrow(() -> new IllegalArgumentException("规则模板目录不存在：" + id));
  }
}
