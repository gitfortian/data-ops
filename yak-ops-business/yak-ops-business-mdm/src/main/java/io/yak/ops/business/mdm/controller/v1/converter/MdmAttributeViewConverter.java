package io.yak.ops.business.mdm.controller.v1.converter;

import io.yak.ops.business.mdm.controller.v1.vo.MdmAttributeVO;
import io.yak.ops.business.mdm.domain.attribute.MdmAttribute;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Converts the master data attribute domain object to its view with labels. */
@Component
public class MdmAttributeViewConverter {

  public MdmAttributeVO toView(MdmAttribute attribute, Map<Long, String> labels) {
    return new MdmAttributeVO(
        attribute.id(),
        attribute.entityId(),
        attribute.code(),
        attribute.name(),
        attribute.type() == null ? null : attribute.type().name(),
        attribute.dataType(),
        attribute.stdTypeId(),
        label(labels, attribute.stdTypeId()),
        attribute.stdUnitId(),
        label(labels, attribute.stdUnitId()),
        attribute.stdCodeSetCode(),
        attribute.stdSecurityId(),
        label(labels, attribute.stdSecurityId()),
        attribute.required(),
        attribute.businessDesc(),
        attribute.sortOrder(),
        attribute.status(),
        attribute.createTime(),
        attribute.updateTime());
  }

  public List<MdmAttributeVO> toViews(List<MdmAttribute> attributes, Map<Long, String> labels) {
    return attributes.stream().map(attribute -> toView(attribute, labels)).toList();
  }

  private static String label(Map<Long, String> labels, Long id) {
    return id == null ? null : labels.get(id);
  }
}
