package io.yak.ops.business.mdm.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.mdm.dao.mapper.MdmCollectLinkMapper;
import io.yak.ops.business.mdm.domain.collect.MdmCollectLink;
import io.yak.ops.business.mdm.dao.model.MdmCollectLinkPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for the collection landing link (project-scoped). */
@Repository
public class MdmCollectLinkRepositoryAdapter implements MdmCollectLinkRepository {

  private final MdmCollectLinkMapper mapper;
  private final CurrentProject currentProject;

  public MdmCollectLinkRepositoryAdapter(
      MdmCollectLinkMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public MdmCollectLink insert(MdmCollectLink link, String operator) {
    Long projectId = currentProject.requireProjectId();
    LocalDateTime now = LocalDateTime.now();
    MdmCollectLinkPO po = toPo(link);
    po.setProjectId(projectId);
    po.setCreatedBy(operator);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    mapper.insert(po);
    return link.withPersisted(po.getId(), operator, now);
  }

  @Override
  public Optional<MdmCollectLink> findBySourceId(Long sourceId) {
    Long projectId = currentProject.requireProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<MdmCollectLinkPO>()
                    .eq(MdmCollectLinkPO::getProjectId, projectId)
                    .eq(MdmCollectLinkPO::getSourceId, sourceId)))
        .map(MdmCollectLinkRepositoryAdapter::toDomain);
  }

  @Override
  public List<MdmCollectLink> listByEntity(Long entityId) {
    Long projectId = currentProject.requireProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<MdmCollectLinkPO>()
                .eq(MdmCollectLinkPO::getProjectId, projectId)
                .eq(MdmCollectLinkPO::getEntityId, entityId)
                .orderByAsc(MdmCollectLinkPO::getId))
        .stream()
        .map(MdmCollectLinkRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<MdmCollectLink> listAll() {
    Long projectId = currentProject.requireProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<MdmCollectLinkPO>()
                .eq(MdmCollectLinkPO::getProjectId, projectId)
                .orderByAsc(MdmCollectLinkPO::getId))
        .stream()
        .map(MdmCollectLinkRepositoryAdapter::toDomain)
        .toList();
  }

  private static MdmCollectLinkPO toPo(MdmCollectLink link) {
    MdmCollectLinkPO po = new MdmCollectLinkPO();
    po.setId(link.id());
    po.setEntityId(link.entityId());
    po.setSourceId(link.sourceId());
    po.setDatasourceId(link.datasourceId());
    po.setLandingTable(link.landingTable());
    po.setJobDefinitionId(link.jobDefinitionId());
    return po;
  }

  private static MdmCollectLink toDomain(MdmCollectLinkPO po) {
    return new MdmCollectLink(
        po.getId(),
        po.getEntityId(),
        po.getSourceId(),
        po.getDatasourceId(),
        po.getLandingTable(),
        po.getJobDefinitionId(),
        po.getCreatedBy(),
        po.getCreateTime(),
        po.getUpdateTime());
  }
}
