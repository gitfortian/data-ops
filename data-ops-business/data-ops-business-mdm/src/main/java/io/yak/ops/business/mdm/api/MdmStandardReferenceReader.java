package io.yak.ops.business.mdm.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.mdm.dao.mapper.MdmAttributeMapper;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardReferenceReader;
import io.yak.ops.common.bean.po.mdm.MdmAttributePO;
import io.yak.ops.core.project.CurrentProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Current MDM attribute references, including disabled entities. */
@Component
@RequiredArgsConstructor
public class MdmStandardReferenceReader implements StandardReferenceReader {
  private final MdmAttributeMapper mapper;
  private final CurrentProject currentProject;

  @Override
  public long countReferences(StandardKind kind, Long standardId, String codeSetCode) {
    var query = new LambdaQueryWrapper<MdmAttributePO>()
        .eq(MdmAttributePO::getProjectId, currentProject.requireProjectId());
    switch (kind) {
      case TYPE -> query.eq(MdmAttributePO::getStdTypeId, standardId);
      case UNIT -> query.eq(MdmAttributePO::getStdUnitId, standardId);
      case SECURITY -> query.eq(MdmAttributePO::getStdSecurityId, standardId);
      case CODE -> query.eq(MdmAttributePO::getStdCodeSetCode, codeSetCode);
      default -> { return 0; }
    }
    return mapper.selectCount(query);
  }
}
