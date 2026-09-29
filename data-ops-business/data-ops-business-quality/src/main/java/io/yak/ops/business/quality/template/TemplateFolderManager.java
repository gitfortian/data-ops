package io.yak.ops.business.quality.template;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.FolderSpec;
import io.yak.ops.business.quality.domain.QualityDomain.TemplateFolder;
import io.yak.ops.business.quality.repository.CustomTemplateRepository;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Command-side lifecycle for custom-template folders. */
@Component
@ConditionalOnQualityEnabled
public class TemplateFolderManager {
  private final CustomTemplateRepository repository;

  public TemplateFolderManager(CustomTemplateRepository repository) {
    this.repository = repository;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public TemplateFolder create(CustomTemplateCommand.FolderSave command, String operator) {
    Long parentId = CustomTemplatePolicy.folderId(command.parentId());
    validateParent(parentId, null);
    String name = CustomTemplatePolicy.requireText(command.name(), "目录名称不能为空");
    uniqueFolder(parentId, name, null);
    long id = repository.insertFolder(
        new FolderSpec(parentId, name, CustomTemplatePolicy.normalizeOperator(operator)));
    return require(id);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public TemplateFolder update(
      long id,
      CustomTemplateCommand.FolderSave command,
      String operator) {
    require(id);
    Long parentId = CustomTemplatePolicy.folderId(command.parentId());
    validateParent(parentId, id);
    String name = CustomTemplatePolicy.requireText(command.name(), "目录名称不能为空");
    uniqueFolder(parentId, name, id);
    if (!repository.updateFolder(
        id, new FolderSpec(parentId, name, CustomTemplatePolicy.normalizeOperator(operator)))) {
      throw new IllegalArgumentException("规则模板目录不存在：" + id);
    }
    return require(id);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public boolean delete(long id, String operator) {
    TemplateFolder value = require(id);
    if (value.childCount() > 0) {
      throw new IllegalStateException("当前目录包含子目录，请先删除或移动子目录");
    }
    if (value.templateCount() > 0) {
      throw new IllegalStateException("当前目录包含自定义模板，请先删除或移动模板");
    }
    if (!repository.deleteFolder(id, CustomTemplatePolicy.normalizeOperator(operator))) {
      throw new IllegalArgumentException("规则模板目录不存在：" + id);
    }
    return true;
  }

  private void validateParent(Long parentId, Long currentId) {
    if (parentId == null) return;
    if (parentId.equals(currentId)) {
      throw new IllegalArgumentException("规则模板目录不能选择自身作为上级目录");
    }
    require(parentId);
    if (currentId == null) return;
    Map<Long, Long> parents = new HashMap<>();
    repository.listFolders().forEach(value -> parents.put(value.id(), value.parentId()));
    for (Long cursor = parentId; cursor != null; cursor = parents.get(cursor)) {
      if (cursor.equals(currentId)) {
        throw new IllegalArgumentException("规则模板目录不能移动到自己的子目录中");
      }
    }
  }

  private void uniqueFolder(Long parentId, String name, Long excludeId) {
    if (repository.folderNameExists(parentId, name, excludeId)) {
      throw new IllegalStateException("同级目录下已经存在名称为“" + name + "”的目录");
    }
  }

  private TemplateFolder require(long id) {
    return repository.findFolder(id)
        .orElseThrow(() -> new IllegalArgumentException("规则模板目录不存在：" + id));
  }
}
