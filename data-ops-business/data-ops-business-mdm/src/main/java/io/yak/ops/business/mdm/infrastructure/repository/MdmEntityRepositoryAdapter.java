package io.yak.ops.business.mdm.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.mdm.dao.mapper.MdmEntityMapper;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.entity.MdmEntityStatus;
import io.yak.ops.business.mdm.dao.model.MdmEntityPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** MyBatis adapter for the master data entity (project-scoped reads/writes). */
@Repository
public class MdmEntityRepositoryAdapter implements MdmEntityRepository {

  private final MdmEntityMapper mapper;
  private final CurrentProject currentProject;

  public MdmEntityRepositoryAdapter(MdmEntityMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public MdmEntity insert(MdmEntity entity, String operator) {
    Long projectId = requiredProjectId();
    LocalDateTime now = LocalDateTime.now();
    MdmEntityPO po = toPo(entity);
    po.setProjectId(projectId);
    po.setCreatedBy(operator);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    mapper.insert(po);
    return entity.withPersisted(po.getId(), operator, now);
  }

  @Override
  public Optional<MdmEntity> findById(Long id) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<MdmEntityPO>()
                    .eq(MdmEntityPO::getId, id)
                    .eq(MdmEntityPO::getProjectId, projectId)))
        .map(MdmEntityRepositoryAdapter::toDomain);
  }

  @Override
  public boolean existsByCode(String code) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<MdmEntityPO>()
                .eq(MdmEntityPO::getProjectId, projectId)
                .eq(MdmEntityPO::getEntityCode, code))
        > 0;
  }

  @Override
  public List<MdmEntity> findAll() {
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<MdmEntityPO>()
                .eq(MdmEntityPO::getProjectId, projectId)
                .orderByAsc(MdmEntityPO::getId))
        .stream()
        .map(MdmEntityRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public PageData<MdmEntity> page(
      int pageNo, int pageSize, String keyword, MdmEntityStatus status) {
    Long projectId = requiredProjectId();
    LambdaQueryWrapper<MdmEntityPO> wrapper =
        new LambdaQueryWrapper<MdmEntityPO>().eq(MdmEntityPO::getProjectId, projectId);
    if (StringUtils.hasText(keyword)) {
      String trimmed = keyword.trim();
      wrapper.and(
          condition ->
              condition
                  .like(MdmEntityPO::getEntityCode, trimmed)
                  .or()
                  .like(MdmEntityPO::getEntityName, trimmed));
    }
    if (status != null) {
      wrapper.eq(MdmEntityPO::getStatus, status.name());
    }
    wrapper.orderByDesc(MdmEntityPO::getUpdateTime).orderByDesc(MdmEntityPO::getId);
    Page<MdmEntityPO> page = Page.of(Math.max(1, pageNo), Math.max(1, pageSize));
    var result = mapper.selectPage(page, wrapper);
    return new PageData<>(
        result.getRecords().stream().map(MdmEntityRepositoryAdapter::toDomain).toList(),
        result.getTotal(),
        result.getPages(),
        (long) pageNo,
        (long) pageSize);
  }

  @Override
  public boolean update(MdmEntity entity) {
    Long projectId = requiredProjectId();
    MdmEntityPO po = new MdmEntityPO();
    po.setEntityName(entity.name());
    po.setOwner(entity.owner());
    po.setDescription(entity.description());
    return mapper.update(po, baseQuery(projectId, entity.id())) > 0;
  }

  @Override
  public boolean changeStatus(Long id, MdmEntityStatus status) {
    Long projectId = requiredProjectId();
    MdmEntityPO po = new MdmEntityPO();
    po.setStatus(status.name());
    return mapper.update(po, baseQuery(projectId, id)) > 0;
  }

  @Override
  public boolean hasReferences(Long id) {
    return mapper.hasReferences(requiredProjectId(), id);
  }

  @Override
  public boolean deleteById(Long id) {
    Long projectId = requiredProjectId();
    return mapper.delete(baseQuery(projectId, id)) > 0;
  }

  private LambdaQueryWrapper<MdmEntityPO> baseQuery(Long projectId, Long id) {
    return new LambdaQueryWrapper<MdmEntityPO>()
        .eq(MdmEntityPO::getId, id)
        .eq(MdmEntityPO::getProjectId, projectId);
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private static MdmEntityPO toPo(MdmEntity entity) {
    MdmEntityPO po = new MdmEntityPO();
    po.setId(entity.id());
    po.setEntityCode(entity.code());
    po.setEntityName(entity.name());
    po.setStatus(entity.status() == null ? null : entity.status().name());
    po.setOwner(entity.owner());
    po.setDescription(entity.description());
    return po;
  }

  private static MdmEntity toDomain(MdmEntityPO po) {
    return new MdmEntity(
        po.getId(),
        po.getEntityCode(),
        po.getEntityName(),
        po.getStatus() == null ? null : MdmEntityStatus.valueOf(po.getStatus()),
        po.getOwner(),
        po.getDescription(),
        po.getCreatedBy(),
        po.getCreateTime(),
        po.getUpdateTime());
  }
}
