package io.yak.ops.business.quality.repository;

import io.yak.ops.business.quality.domain.QualityDomain.CustomTemplate;
import io.yak.ops.business.quality.domain.QualityDomain.CustomTemplateSpec;
import io.yak.ops.business.quality.domain.QualityDomain.FolderSpec;
import io.yak.ops.business.quality.domain.QualityDomain.TemplateFolder;
import io.yak.ops.business.quality.domain.QualityQuery;
import java.util.List;
import java.util.Optional;

/** 自定义质量模板 Repository。 */
public interface CustomTemplateRepository {
  List<CustomTemplate> list(QualityQuery.CustomTemplate query);
  Optional<CustomTemplate> find(long id);
  List<TemplateFolder> listFolders();
  Optional<TemplateFolder> findFolder(long id);
  boolean folderNameExists(Long parentId, String name, Long excludeId);
  long insertFolder(FolderSpec folder);
  boolean updateFolder(long id, FolderSpec folder);
  boolean deleteFolder(long id, String operator);
  boolean templateNameExists(Long folderId, String name, Long excludeId);
  long insertTemplate(CustomTemplateSpec template);
  boolean updateTemplate(long id, CustomTemplateSpec template);
  boolean deleteTemplate(long id);
}
