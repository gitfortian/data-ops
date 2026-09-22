package io.yak.ops.business.quality.controller.v1.converter;

import io.yak.ops.business.quality.domain.QualityDomain.CustomTemplate;
import io.yak.ops.business.quality.domain.QualityDomain.Template;
import io.yak.ops.business.quality.domain.QualityDomain.TemplateFolder;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.template.CustomTemplateCommand;
import io.yak.ops.business.quality.template.CustomTemplatePolicy;
import io.yak.ops.business.quality.template.CustomTemplateReader.CustomTemplateList;
import io.yak.ops.business.quality.template.QualityTemplateReader.CatalogSummary;
import io.yak.ops.business.quality.template.QualityTemplateReader.TemplateList;
import io.yak.ops.common.bean.dto.quality.CustomQualityTemplateDTO;
import io.yak.ops.common.bean.vo.quality.CustomQualityTemplateVO;
import io.yak.ops.common.bean.vo.quality.QualityTemplateVO;
import io.yak.ops.common.enums.quality.QualityEnums.RuleScope;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class QualityTemplateConverter {
  public QualityQuery.Template templateQuery(String keyword, String dimension, RuleScope scope) { return new QualityQuery.Template(keyword, dimension, scope); }
  public QualityTemplateVO.ListView templateList(TemplateList list) { return new QualityTemplateVO.ListView(list.records().stream().map(this::template).toList(), new QualityTemplateVO.Summary(list.summary().total(), list.summary().dimensions())); }
  public QualityTemplateVO.CatalogSummary catalogSummary(CatalogSummary value) { return new QualityTemplateVO.CatalogSummary(value.systemTotal(), value.customTotal(), value.systemDimensions(), value.customDimensions()); }
  public QualityTemplateVO.Template template(Template value) { return new QualityTemplateVO.Template(value.id(), value.code(), value.name(), value.description(), value.ruleType(), value.scope(), value.dimension(), value.parameterSchema(), value.builtin(), value.enabled(), value.ruleCount(), value.sortOrder()); }
  public QualityQuery.CustomTemplate customQuery(String keyword, String dimension, Long folderId) { Long normalizedFolder = CustomTemplatePolicy.folderId(folderId); return new QualityQuery.CustomTemplate(text(keyword), text(dimension), normalizedFolder, folderId != null); }
  public CustomQualityTemplateVO.ListView customList(CustomTemplateList list) { return new CustomQualityTemplateVO.ListView(list.records().stream().map(this::customTemplate).toList(), new CustomQualityTemplateVO.Summary(list.summary().scopeTotal(), list.summary().systemTotal(), list.summary().customTotal(), list.summary().dimensions())); }
  public CustomQualityTemplateVO.Template customTemplate(CustomTemplate value) { return new CustomQualityTemplateVO.Template(value.id(), value.code(), value.name(), value.description(), value.ruleType(), value.scope(), value.dimension(), value.parameterSchema(), value.builtin(), value.enabled(), value.ruleCount(), value.sortOrder(), value.folderId(), value.folderName(), value.templateSql(), value.setFlag(), value.checkType(), value.checkMethod(), value.createdBy(), value.createdAt(), value.updatedAt()); }
  public List<CustomQualityTemplateVO.Folder> folders(List<TemplateFolder> values) { return values.stream().map(this::folder).toList(); }
  public CustomQualityTemplateVO.Folder folder(TemplateFolder value) { return new CustomQualityTemplateVO.Folder(value.id(), value.parentId(), value.name(), value.sortOrder(), value.templateCount(), value.childCount(), value.createdAt(), value.updatedAt()); }
  public CustomTemplateCommand.FolderSave folderCommand(CustomQualityTemplateDTO.SaveFolderRequest request) { return new CustomTemplateCommand.FolderSave(request.parentId(), request.name()); }
  public CustomTemplateCommand.Save customCommand(CustomQualityTemplateDTO.SaveTemplateRequest request) { return new CustomTemplateCommand.Save(request.name(), request.description(), request.dimension(), request.folderId(), request.customSql(), request.setFlag(), request.checkType(), request.checkMethod(), request.defaultOperator(), request.defaultThreshold(), request.defaultThresholdEnd()); }
  public CustomTemplateCommand.Copy copyCommand(CustomQualityTemplateDTO.CopyTemplateRequest request) { return new CustomTemplateCommand.Copy(request.name(), request.folderId()); }
  private static String text(String value) { if (value == null) return null; String normalized = value.trim(); return normalized.isEmpty() ? null : normalized; }
}
