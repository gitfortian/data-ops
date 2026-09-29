package io.yak.ops.business.semantic.controller.v1.converter;

import io.yak.framework.common.PagingData;
import io.yak.ops.business.semantic.controller.v1.vo.StandardFieldVO;
import io.yak.ops.business.semantic.api.StandardField;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Converts standard-field domain objects to API view objects. */
@Component
public class StandardFieldViewConverter {

  public StandardFieldVO toView(StandardField field) {
    return toView(field, Map.of());
  }

  /** 引用名称由服务端解析(2026-09-16):labels 为 标准ID → 名称（编码）。 */
  public StandardFieldVO toView(StandardField field, Map<Long, String> labels) {
    StandardFieldVO vo = new StandardFieldVO();
    vo.setId(field.id());
    vo.setCode(field.code());
    vo.setName(field.name());
    vo.setRole(field.role());
    vo.setStatus(field.status());
    vo.setDataType(field.dataType());
    vo.setStdTypeId(field.stdTypeId());
    vo.setStdUnitId(field.stdUnitId());
    vo.setStdCaliberId(field.stdCaliberId());
    vo.setStdCodeSetCode(field.stdCodeSetCode());
    vo.setStdSecurityId(field.stdSecurityId());
    vo.setStdTypeName(field.stdTypeId() != null ? labels.get(field.stdTypeId()) : null);
    vo.setStdUnitName(field.stdUnitId() != null ? labels.get(field.stdUnitId()) : null);
    vo.setStdCaliberName(field.stdCaliberId() != null ? labels.get(field.stdCaliberId()) : null);
    vo.setStdSecurityName(field.stdSecurityId() != null ? labels.get(field.stdSecurityId()) : null);
    vo.setBusinessDesc(field.businessDesc());
    vo.setSource(field.source());
    vo.setVersion(field.version());
    vo.setRequired(field.required());
    vo.setCreatedBy(field.createdBy());
    vo.setCreateTime(field.createTime());
    vo.setUpdateTime(field.updateTime());
    return vo;
  }

  public PagingData<StandardFieldVO> page(
      io.yak.framework.common.PageData<StandardField> page, Map<Long, String> labels) {
    return PagingData.from(page.map(field -> toView(field, labels)));
  }
}
