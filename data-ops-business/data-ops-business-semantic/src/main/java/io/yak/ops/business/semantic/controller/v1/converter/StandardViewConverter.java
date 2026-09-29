package io.yak.ops.business.semantic.controller.v1.converter;

import io.yak.framework.common.PagingData;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.catalog.StandardVersion;
import io.yak.ops.business.semantic.controller.v1.vo.CodeSetDetailVO;
import io.yak.ops.business.semantic.controller.v1.vo.CodeSetOptionVO;
import io.yak.ops.business.semantic.controller.v1.vo.CodeSetVO;
import io.yak.ops.business.semantic.controller.v1.vo.StandardOptionVO;
import io.yak.ops.business.semantic.controller.v1.vo.StandardVO;
import io.yak.ops.business.semantic.controller.v1.vo.StandardVersionVO;
import io.yak.ops.business.semantic.dao.StandardListRow;
import java.util.List;
import org.springframework.stereotype.Component;

/** Converts semantic standard domain objects to API view objects. */
@Component
public class StandardViewConverter {

  public StandardVO toView(Standard standard) {
    StandardVO vo = new StandardVO();
    vo.setId(standard.id());
    vo.setKind(standard.kind().name());
    vo.setCode(standard.code());
    vo.setName(standard.name());
    vo.setStatus(standard.status().name());
    vo.setVersion(standard.version());
    vo.setSortOrder(standard.sortOrder());
    vo.setPreset(standard.preset());
    vo.setDescription(standard.description());
    Standard.KindFields fields = standard.fields();
    vo.setScope(fields.scope());
    vo.setLayer(fields.layer());
    vo.setRuleExpr(fields.ruleExpr());
    vo.setExample(fields.example());
    vo.setTypeCode(fields.typeCode());
    vo.setStdType(fields.stdType());
    vo.setSourceMapping(fields.sourceMapping());
    vo.setCodeSetCode(fields.codeSetCode());
    vo.setCodeValue(fields.codeValue());
    vo.setCodeLabel(fields.codeLabel());
    vo.setUnitCode(fields.unitCode());
    vo.setUnitType(fields.unitType());
    vo.setCaliberCode(fields.caliberCode());
    vo.setCalRule(fields.calRule());
    vo.setBusinessDesc(fields.businessDesc());
    vo.setLevelCode(fields.levelCode());
    vo.setMaskRule(fields.maskRule());
    vo.setCreatedBy(standard.createdBy());
    vo.setCreateTime(standard.createTime());
    vo.setUpdateTime(standard.updateTime());
    return vo;
  }

  /** 统一分页行视图(32.1):原始行全量字段;码集组行 code=码集编码、携带 codeValueCount。 */
  public StandardVO toRowView(StandardListRow row) {
    StandardVO vo = new StandardVO();
    vo.setId(row.getId());
    vo.setKind(row.getKind());
    vo.setCode(row.getCode());
    vo.setName(row.getName());
    vo.setStatus(row.getStatus());
    vo.setVersion(row.getVersion());
    vo.setSortOrder(row.getSortOrder());
    vo.setPreset(row.getPreset());
    vo.setDescription(row.getDescription());
    vo.setScope(row.getScope());
    vo.setLayer(row.getLayer());
    vo.setRuleExpr(row.getRuleExpr());
    vo.setExample(row.getExample());
    vo.setTypeCode(row.getTypeCode());
    vo.setStdType(row.getStdType());
    vo.setSourceMapping(row.getSourceMapping());
    vo.setCodeSetCode(row.getCodeSetCode());
    vo.setCodeValue(row.getCodeValue());
    vo.setCodeLabel(row.getCodeLabel());
    vo.setUnitCode(row.getUnitCode());
    vo.setUnitType(row.getUnitType());
    vo.setCaliberCode(row.getCaliberCode());
    vo.setCalRule(row.getCalRule());
    vo.setBusinessDesc(row.getBusinessDesc());
    vo.setLevelCode(row.getLevelCode());
    vo.setMaskRule(row.getMaskRule());
    vo.setCodeValueCount(row.getValueCount());
    vo.setUpdateTime(row.getUpdateTime());
    return vo;
  }

  public PagingData<StandardVO> page(io.yak.framework.common.PageData<StandardListRow> page) {
    return PagingData.from(page.map(this::toRowView));
  }

  public StandardVersionVO toVersionView(StandardVersion version) {
    StandardVersionVO vo = new StandardVersionVO();
    vo.setStandardId(version.standardId());
    vo.setVersion(version.version());
    vo.setOperatedBy(version.operatedBy());
    vo.setCreateTime(version.createTime());
    vo.setPayload(version.payload());
    return vo;
  }

  // ── 码集转换 ──

  /** 码集列表行视图(32.1):来源同统一分页 SQL(kind=CODE 组行)。 */
  public CodeSetVO toCodeSetView(StandardListRow row) {
    CodeSetVO vo = new CodeSetVO();
    vo.setCodeSetCode(row.getCode());
    vo.setName(row.getName());
    vo.setValueCount(row.getValueCount() == null ? 0 : row.getValueCount());
    vo.setStatus(row.getStatus());
    vo.setVersion(row.getVersion());
    vo.setPreset(row.getPreset());
    vo.setSortOrder(row.getSortOrder());
    vo.setDescription(row.getDescription());
    vo.setUpdateTime(row.getUpdateTime());
    return vo;
  }

  /** 码集详情:聚合字段(状态/预置/更新时间)由组内码值行推导,legacy 标记存量空码集行。 */
  public CodeSetDetailVO toCodeSetDetailView(String codeSetCode, List<Standard> values, boolean legacy) {
    CodeSetDetailVO vo = new CodeSetDetailVO();
    vo.setCodeSetCode(codeSetCode);
    vo.setLegacy(legacy);
    vo.setValueCount(values.size());
    vo.setValues(values.stream().map(this::toView).toList());
    if (!values.isEmpty()) {
      Standard head = values.get(0);
      vo.setName(head.name());
      vo.setDescription(head.description());
      vo.setSortOrder(head.sortOrder());
      vo.setStatus(head.status().name());
      vo.setPreset(head.preset());
      vo.setUpdateTime(
          values.stream().map(Standard::updateTime).max(java.time.LocalDateTime::compareTo).orElse(null));
    }
    return vo;
  }

  /** 启用码集下拉选项(35):value = code_set_code。 */
  public CodeSetOptionVO toCodeSetOptionView(StandardListRow row) {
    CodeSetOptionVO vo = new CodeSetOptionVO();
    vo.setCodeSetCode(row.getCode());
    vo.setName(row.getName());
    return vo;
  }

  /** 启用标准下拉选项(35):类型/单位/口径/安全,引用列存 ID。 */
  public StandardOptionVO toOptionView(Standard standard) {
    StandardOptionVO vo = new StandardOptionVO();
    vo.setId(standard.id());
    vo.setKind(standard.kind().name());
    vo.setCode(standard.code());
    vo.setName(standard.name());
    vo.setStdType(standard.fields().stdType());
    vo.setTypeCode(standard.fields().typeCode());
    vo.setUnitType(standard.fields().unitType());
    return vo;
  }

  public PagingData<CodeSetVO> pageCodeSet(io.yak.framework.common.PageData<StandardListRow> page) {
    return PagingData.from(page.map(this::toCodeSetView));
  }
}
