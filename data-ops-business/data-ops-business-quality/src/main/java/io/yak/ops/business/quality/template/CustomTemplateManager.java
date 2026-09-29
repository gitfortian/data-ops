package io.yak.ops.business.quality.template;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.CustomTemplate;
import io.yak.ops.business.quality.domain.QualityDomain.CustomTemplateSpec;
import io.yak.ops.business.quality.repository.CustomTemplateRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Command-side lifecycle for custom quality templates. */
@Component
@ConditionalOnQualityEnabled
public class CustomTemplateManager {
  private final CustomTemplateRepository repository;
  private final CustomTemplatePolicy policy;

  public CustomTemplateManager(
      CustomTemplateRepository repository,
      CustomTemplatePolicy policy) {
    this.repository = repository;
    this.policy = policy;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public CustomTemplate create(CustomTemplateCommand.Save command, String operator) {
    Long folderId = normalizeFolder(command.folderId());
    uniqueTemplate(folderId, command.name(), null);
    long id = repository.insertTemplate(
        policy.write(CustomTemplatePolicy.newCode(), command, folderId, operator));
    return require(id);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public CustomTemplate update(
      long id,
      CustomTemplateCommand.Save command,
      String operator) {
    CustomTemplate existing = require(id);
    Long folderId = normalizeFolder(command.folderId());
    uniqueTemplate(folderId, command.name(), id);
    if (!repository.updateTemplate(
        id, policy.write(existing.code(), command, folderId, operator))) {
      throw new IllegalArgumentException("自定义规则模板不存在：" + id);
    }
    return require(id);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public CustomTemplate copy(
      long id,
      CustomTemplateCommand.Copy command,
      String operator) {
    CustomTemplate source = require(id);
    Long folderId = normalizeFolder(command.folderId());
    String name = CustomTemplatePolicy.requireText(command.name(), "模板名称不能为空");
    uniqueTemplate(folderId, name, null);
    long copiedId = repository.insertTemplate(new CustomTemplateSpec(
        CustomTemplatePolicy.newCode(), name, source.description(), source.dimension(),
        source.parameterSchema(), folderId, source.templateSql(), source.setFlag(),
        source.checkType(), source.checkMethod(), CustomTemplatePolicy.normalizeOperator(operator)));
    return require(copiedId);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public boolean delete(long id) {
    require(id);
    if (!repository.deleteTemplate(id)) {
      throw new IllegalArgumentException("自定义规则模板不存在：" + id);
    }
    return true;
  }

  private Long normalizeFolder(Long value) {
    Long folderId = CustomTemplatePolicy.folderId(value);
    if (folderId != null && repository.findFolder(folderId).isEmpty()) {
      throw new IllegalArgumentException("规则模板目录不存在：" + folderId);
    }
    return folderId;
  }

  private void uniqueTemplate(Long folderId, String value, Long excludeId) {
    String name = CustomTemplatePolicy.requireText(value, "模板名称不能为空");
    if (repository.templateNameExists(folderId, name, excludeId)) {
      throw new IllegalStateException("当前目录下已经存在名称为“" + name + "”的规则模板");
    }
  }

  private CustomTemplate require(long id) {
    return repository.find(id)
        .orElseThrow(() -> new IllegalArgumentException("自定义规则模板不存在：" + id));
  }
}
