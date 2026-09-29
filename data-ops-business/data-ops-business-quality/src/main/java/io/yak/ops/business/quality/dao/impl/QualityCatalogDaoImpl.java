package io.yak.ops.business.quality.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.dao.QualityCatalogDao;
import io.yak.ops.business.quality.dao.mapper.QualityQueryMapper;
import io.yak.ops.business.quality.dao.mapper.QualityRuleTemplateMapper;
import io.yak.ops.business.quality.dao.mapper.QualityTemplateFolderMapper;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.FolderRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.TemplateRow;
import io.yak.ops.common.bean.po.quality.QualityRuleTemplatePO;
import io.yak.ops.common.bean.po.quality.QualityTemplateFolderPO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnQualityEnabled
@DependsOn("qualityFlyway")
public class QualityCatalogDaoImpl implements QualityCatalogDao {
  private final QualityQueryMapper queryMapper;
  private final QualityRuleTemplateMapper templateMapper;
  private final QualityTemplateFolderMapper folderMapper;

  @Override
  public List<TemplateRow> selectTemplates(Map<String, Object> params) {
    return queryMapper.selectTemplates(params);
  }

  @Override
  public TemplateRow selectTemplate(long id, boolean customOnly) {
    return queryMapper.selectTemplateById(id, customOnly);
  }

  @Override
  public long countSystemTemplates() {
    return queryMapper.countSystemTemplates();
  }

  @Override
  public List<TemplateCount> selectTemplateCounts(Boolean builtin, Long folderId, boolean folderFilter) {
    QueryWrapper<QualityRuleTemplatePO> query = new QueryWrapper<>();
    query.select("builtin", "quality_dimension", "COUNT(*) AS template_count")
        .eq("enabled", true)
        .apply("COALESCE(deleted, 0) = 0");
    if (builtin != null) query.eq("builtin", builtin);
    if (folderFilter) {
      if (folderId == null || folderId == 0L) query.isNull("folder_id");
      else query.eq("folder_id", folderId);
    }
    query.groupBy("builtin", "quality_dimension");
    return templateMapper.selectMaps(query).stream()
        .map(row -> new TemplateCount(
            booleanValue(value(row, "builtin")),
            stringValue(value(row, "quality_dimension", "qualityDimension")),
            longValue(value(row, "template_count", "templateCount"))))
        .toList();
  }

  @Override
  public List<FolderRow> selectFolders() {
    return queryMapper.selectFolders();
  }

  @Override
  public boolean folderNameExists(Long parentId, String name, Long excludeId) {
    var query = Wrappers.<QualityTemplateFolderPO>lambdaQuery()
        .eq(QualityTemplateFolderPO::getDeleted, false)
        .apply("LOWER(folder_name) = LOWER({0})", name);
    if (parentId == null) query.isNull(QualityTemplateFolderPO::getParentId);
    else query.eq(QualityTemplateFolderPO::getParentId, parentId);
    if (excludeId != null) query.ne(QualityTemplateFolderPO::getId, excludeId);
    return folderMapper.selectCount(query) > 0;
  }

  @Override
  public long insertFolder(QualityTemplateFolderPO folder) {
    folderMapper.insert(folder);
    if (folder.getId() == null) throw new IllegalStateException("创建规则模板目录失败：未返回编号");
    return folder.getId();
  }

  @Override
  public boolean updateFolder(QualityTemplateFolderPO folder) {
    return folderMapper.update(
        folder,
        Wrappers.<QualityTemplateFolderPO>lambdaUpdate()
            .eq(QualityTemplateFolderPO::getId, folder.getId())
            .eq(QualityTemplateFolderPO::getDeleted, false)) > 0;
  }

  @Override
  public boolean softDeleteFolder(long id, String operator) {
    return folderMapper.update(
        null,
        Wrappers.<QualityTemplateFolderPO>lambdaUpdate()
            .eq(QualityTemplateFolderPO::getId, id)
            .eq(QualityTemplateFolderPO::getDeleted, false)
            .set(QualityTemplateFolderPO::getDeleted, true)
            .set(QualityTemplateFolderPO::getUpdatedBy, operator)
            .set(QualityTemplateFolderPO::getUpdatedAt, LocalDateTime.now())) > 0;
  }

  @Override
  public boolean templateNameExists(Long folderId, String name, Long excludeId) {
    var query = Wrappers.<QualityRuleTemplatePO>lambdaQuery()
        .eq(QualityRuleTemplatePO::getBuiltin, false)
        .eq(QualityRuleTemplatePO::getDeleted, false)
        .apply("LOWER(template_name) = LOWER({0})", name);
    if (folderId == null) query.isNull(QualityRuleTemplatePO::getFolderId);
    else query.eq(QualityRuleTemplatePO::getFolderId, folderId);
    if (excludeId != null) query.ne(QualityRuleTemplatePO::getId, excludeId);
    return templateMapper.selectCount(query) > 0;
  }

  @Override
  public long insertTemplate(QualityRuleTemplatePO template) {
    templateMapper.insert(template);
    if (template.getId() == null) throw new IllegalStateException("创建自定义规则模板失败：未返回编号");
    return template.getId();
  }

  @Override
  public boolean updateCustomTemplate(QualityRuleTemplatePO template) {
    return templateMapper.update(
        template,
        Wrappers.<QualityRuleTemplatePO>lambdaUpdate()
            .eq(QualityRuleTemplatePO::getId, template.getId())
            .eq(QualityRuleTemplatePO::getBuiltin, false)
            .eq(QualityRuleTemplatePO::getDeleted, false)) > 0;
  }

  @Override
  public boolean softDeleteCustomTemplate(long id) {
    return templateMapper.update(
        null,
        Wrappers.<QualityRuleTemplatePO>lambdaUpdate()
            .eq(QualityRuleTemplatePO::getId, id)
            .eq(QualityRuleTemplatePO::getBuiltin, false)
            .eq(QualityRuleTemplatePO::getDeleted, false)
            .set(QualityRuleTemplatePO::getEnabled, false)
            .set(QualityRuleTemplatePO::getDeleted, true)
            .set(QualityRuleTemplatePO::getUpdatedAt, LocalDateTime.now())) > 0;
  }

  private static Object value(Map<String, Object> row, String... keys) {
    for (String key : keys) {
      if (row.containsKey(key)) return row.get(key);
      String upper = key.toUpperCase();
      if (row.containsKey(upper)) return row.get(upper);
    }
    return null;
  }

  private static boolean booleanValue(Object value) {
    if (value instanceof Boolean result) return result;
    if (value instanceof Number number) return number.intValue() != 0;
    return value != null && Boolean.parseBoolean(value.toString());
  }

  private static long longValue(Object value) {
    if (value instanceof Number number) return number.longValue();
    return value == null ? 0L : Long.parseLong(value.toString());
  }

  private static String stringValue(Object value) {
    return value == null ? "" : value.toString();
  }
}
