package io.yak.ops.business.quality.repository;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.dao.QualityCatalogDao;
import io.yak.ops.business.quality.domain.QualityDomain.CustomTemplate;
import io.yak.ops.business.quality.domain.QualityDomain.CustomTemplateSpec;
import io.yak.ops.business.quality.domain.QualityDomain.FolderSpec;
import io.yak.ops.business.quality.domain.QualityDomain.TemplateFolder;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.FolderRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.TemplateRow;
import io.yak.ops.common.bean.po.quality.QualityRuleTemplatePO;
import io.yak.ops.common.bean.po.quality.QualityTemplateFolderPO;
import io.yak.ops.common.enums.quality.QualityEnums.CheckMethod;
import io.yak.ops.common.enums.quality.QualityEnums.CheckType;
import io.yak.ops.common.enums.quality.QualityEnums.RuleScope;
import io.yak.ops.common.enums.quality.QualityEnums.RuleType;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnQualityEnabled
@DependsOn("qualityFlyway")
public class CustomTemplateRepositoryAdapter implements CustomTemplateRepository {
  private final QualityCatalogDao catalogDao;

  @Override
  public List<CustomTemplate> list(QualityQuery.CustomTemplate query) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("customOnly", true);
    if (query != null) {
      if (hasText(query.keyword())) params.put("keyword", "%" + query.keyword().trim().toLowerCase() + "%");
      if (hasText(query.dimension())) params.put("dimension", query.dimension().trim());
      params.put("folderFilter", query.folderFilter());
      params.put("folderId", query.folderId());
    }
    return catalogDao.selectTemplates(params).stream().map(this::template).toList();
  }

  @Override public Optional<CustomTemplate> find(long id) { return Optional.ofNullable(catalogDao.selectTemplate(id, true)).map(this::template); }
  @Override public List<TemplateFolder> listFolders() { return catalogDao.selectFolders().stream().map(this::folder).toList(); }
  @Override public Optional<TemplateFolder> findFolder(long id) { return listFolders().stream().filter(value -> value.id() == id).findFirst(); }
  @Override public boolean folderNameExists(Long parentId, String name, Long excludeId) { return catalogDao.folderNameExists(parentId, name, excludeId); }

  @Override
  public long insertFolder(FolderSpec folder) {
    QualityTemplateFolderPO po = new QualityTemplateFolderPO();
    po.setParentId(folder.parentId()); po.setFolderName(folder.name()); po.setSortOrder(10); po.setDeleted(false);
    po.setCreatedBy(folder.operator()); po.setUpdatedBy(folder.operator());
    return catalogDao.insertFolder(po);
  }

  @Override
  public boolean updateFolder(long id, FolderSpec folder) {
    QualityTemplateFolderPO po = new QualityTemplateFolderPO();
    po.setId(id); po.setParentId(folder.parentId()); po.setFolderName(folder.name()); po.setUpdatedBy(folder.operator()); po.setUpdatedAt(LocalDateTime.now());
    return catalogDao.updateFolder(po);
  }

  @Override public boolean deleteFolder(long id, String operator) { return catalogDao.softDeleteFolder(id, operator); }
  @Override public boolean templateNameExists(Long folderId, String name, Long excludeId) { return catalogDao.templateNameExists(folderId, name, excludeId); }
  @Override public long insertTemplate(CustomTemplateSpec template) { return catalogDao.insertTemplate(templatePO(null, template)); }
  @Override public boolean updateTemplate(long id, CustomTemplateSpec template) { return catalogDao.updateCustomTemplate(templatePO(id, template)); }
  @Override public boolean deleteTemplate(long id) { return catalogDao.softDeleteCustomTemplate(id); }

  private QualityRuleTemplatePO templatePO(Long id, CustomTemplateSpec value) {
    QualityRuleTemplatePO po = new QualityRuleTemplatePO();
    po.setId(id); po.setTemplateCode(value.code()); po.setTemplateName(value.name()); po.setDescription(value.description());
    po.setRuleType(RuleType.CUSTOM_SQL.name()); po.setRuleScope(RuleScope.TABLE.name()); po.setQualityDimension(value.dimension());
    po.setParameterSchemaJson(value.parameterSchema()); po.setBuiltin(false); po.setEnabled(true); po.setSortOrder(1000);
    po.setFolderId(value.folderId()); po.setTemplateSql(value.templateSql()); po.setSetFlag(value.setFlag());
    po.setCheckType(value.checkType().name()); po.setCheckMethod(value.checkMethod().name()); po.setCreatedBy(value.operator()); po.setDeleted(false);
    return po;
  }

  private CustomTemplate template(TemplateRow row) {
    return new CustomTemplate(row.getId(), row.getTemplateCode(), row.getTemplateName(), row.getDescription(),
        RuleType.valueOf(row.getRuleType()), RuleScope.valueOf(row.getRuleScope()), row.getQualityDimension(),
        row.getParameterSchemaJson(), Boolean.TRUE.equals(row.getBuiltin()), Boolean.TRUE.equals(row.getEnabled()),
        nvl(row.getRuleCount()), nvl(row.getSortOrder()), row.getFolderId(), row.getFolderName(), row.getTemplateSql(),
        row.getSetFlag(), enumValue(CheckType.class, row.getCheckType(), CheckType.NUMERIC),
        enumValue(CheckMethod.class, row.getCheckMethod(), CheckMethod.FIXED_VALUE), row.getCreatedBy(), row.getCreatedAt(), row.getUpdatedAt());
  }

  private TemplateFolder folder(FolderRow row) {
    return new TemplateFolder(row.getId(), row.getParentId(), row.getFolderName(), nvl(row.getSortOrder()),
        nvl(row.getTemplateCount()), nvl(row.getChildCount()), row.getCreatedAt(), row.getUpdatedAt());
  }

  private static boolean hasText(String value) { return value != null && !value.isBlank(); }
  private static int nvl(Integer value) { return value == null ? 0 : value; }
  private static long nvl(Long value) { return value == null ? 0L : value; }
  private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) { return hasText(value) ? Enum.valueOf(type, value) : fallback; }
}
