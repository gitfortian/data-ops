package io.yak.ops.business.quality.dao;

import io.yak.ops.common.bean.po.quality.QualityQueryPO.FolderRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.TemplateRow;
import io.yak.ops.common.bean.po.quality.QualityRuleTemplatePO;
import io.yak.ops.common.bean.po.quality.QualityTemplateFolderPO;
import java.util.List;
import java.util.Map;

/** 规则模板与目录数据访问边界。 */
public interface QualityCatalogDao {
  List<TemplateRow> selectTemplates(Map<String, Object> params);
  TemplateRow selectTemplate(long id, boolean customOnly);
  long countSystemTemplates();
  List<TemplateCount> selectTemplateCounts(Boolean builtin, Long folderId, boolean folderFilter);
  List<FolderRow> selectFolders();
  boolean folderNameExists(Long parentId, String name, Long excludeId);
  long insertFolder(QualityTemplateFolderPO folder);
  boolean updateFolder(QualityTemplateFolderPO folder);
  boolean softDeleteFolder(long id, String operator);
  boolean templateNameExists(Long folderId, String name, Long excludeId);
  long insertTemplate(QualityRuleTemplatePO template);
  boolean updateCustomTemplate(QualityRuleTemplatePO template);
  boolean softDeleteCustomTemplate(long id);

  record TemplateCount(boolean builtin, String dimension, long count) {}
}
