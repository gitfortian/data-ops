package io.yak.ops.business.security.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.security.dao.mapper.SecurityLevelMapper;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardReferenceReader;
import io.yak.ops.common.bean.po.security.DsecSecurityLevelPO;
import io.yak.ops.core.project.CurrentProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Security levels retain their semantic template reference in every lifecycle state. */
@Component
@RequiredArgsConstructor
public class SecurityStandardReferenceReader implements StandardReferenceReader {
  private final SecurityLevelMapper mapper;
  private final CurrentProject currentProject;

  @Override
  public long countReferences(StandardKind kind, Long standardId, String codeSetCode) {
    if (kind != StandardKind.SECURITY) return 0;
    return mapper.selectCount(new LambdaQueryWrapper<DsecSecurityLevelPO>()
        .eq(DsecSecurityLevelPO::getProjectId, currentProject.requireProjectId())
        .eq(DsecSecurityLevelPO::getStdSecurityId, standardId));
  }
}
