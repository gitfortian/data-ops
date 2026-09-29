package io.yak.ops.business.mdm.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.mdm.dao.mapper.MdmRecordVersionMapper;
import io.yak.ops.business.mdm.domain.record.MdmRecordStatus;
import io.yak.ops.business.mdm.domain.record.MdmRecordVersion;
import io.yak.ops.common.bean.po.mdm.MdmRecordVersionPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** MyBatis-Plus adapter for record version snapshots; uk 冲突视为已快照(回调重试幂等)。 */
@Repository
public class MdmRecordVersionRepositoryAdapter implements MdmRecordVersionRepository {

  private final MdmRecordVersionMapper mapper;
  private final CurrentProject currentProject;

  public MdmRecordVersionRepositoryAdapter(
      MdmRecordVersionMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public boolean insertIfAbsent(MdmRecordVersion version) {
    Long projectId = currentProject.requireProjectId();
    MdmRecordVersionPO po = new MdmRecordVersionPO();
    po.setProjectId(projectId);
    po.setEntityId(version.entityId());
    po.setMasterId(version.masterId());
    po.setVersion(version.version());
    po.setAttributes(version.attributes());
    po.setStatus(version.status().name());
    po.setChangeId(version.changeId());
    po.setOperator(version.operator());
    po.setCreateTime(LocalDateTime.now());
    try {
      return mapper.insert(po) > 0;
    } catch (DuplicateKeyException exception) {
      return false;
    }
  }

  @Override
  public List<MdmRecordVersion> listByMaster(Long entityId, String masterId) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectList(
            new LambdaQueryWrapper<MdmRecordVersionPO>()
                .eq(MdmRecordVersionPO::getProjectId, projectId)
                .eq(MdmRecordVersionPO::getEntityId, entityId)
                .eq(MdmRecordVersionPO::getMasterId, masterId)
                .orderByAsc(MdmRecordVersionPO::getVersion))
        .stream()
        .map(MdmRecordVersionRepositoryAdapter::toDomain)
        .toList();
  }

  private static MdmRecordVersion toDomain(MdmRecordVersionPO po) {
    return new MdmRecordVersion(
        po.getId(),
        po.getEntityId(),
        po.getMasterId(),
        po.getVersion(),
        po.getAttributes(),
        MdmRecordStatus.valueOf(po.getStatus()),
        po.getChangeId(),
        po.getOperator(),
        po.getCreateTime());
  }
}
