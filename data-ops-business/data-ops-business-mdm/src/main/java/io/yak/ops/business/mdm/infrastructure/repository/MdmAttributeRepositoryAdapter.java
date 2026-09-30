package io.yak.ops.business.mdm.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.mdm.dao.mapper.MdmAttributeMapper;
import io.yak.ops.business.mdm.domain.attribute.MdmAttribute;
import io.yak.ops.business.mdm.domain.attribute.MdmAttributeType;
import io.yak.ops.common.bean.po.mdm.MdmAttributePO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for the master data attribute (project-scoped reads/writes). */
@Repository
public class MdmAttributeRepositoryAdapter implements MdmAttributeRepository {

  private final MdmAttributeMapper mapper;
  private final CurrentProject currentProject;

  public MdmAttributeRepositoryAdapter(MdmAttributeMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public MdmAttribute insert(MdmAttribute attribute, String operator) {
    Long projectId = requiredProjectId();
    LocalDateTime now = LocalDateTime.now();
    MdmAttributePO po = toPo(attribute);
    po.setProjectId(projectId);
    po.setCreatedBy(operator);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    mapper.insert(po);
    return attribute.withPersisted(po.getId(), operator, now);
  }

  @Override
  public Optional<MdmAttribute> findById(Long id) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<MdmAttributePO>()
                    .eq(MdmAttributePO::getId, id)
                    .eq(MdmAttributePO::getProjectId, projectId)))
        .map(MdmAttributeRepositoryAdapter::toDomain);
  }

  @Override
  public List<MdmAttribute> listByEntity(Long entityId) {
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<MdmAttributePO>()
                .eq(MdmAttributePO::getProjectId, projectId)
                .eq(MdmAttributePO::getEntityId, entityId)
                .orderByAsc(MdmAttributePO::getSortOrder)
                .orderByAsc(MdmAttributePO::getId))
        .stream()
        .map(MdmAttributeRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean existsByCode(Long entityId, String code) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<MdmAttributePO>()
                .eq(MdmAttributePO::getProjectId, projectId)
                .eq(MdmAttributePO::getEntityId, entityId)
                .eq(MdmAttributePO::getAttrCode, code))
        > 0;
  }

  @Override
  public boolean existsPk(Long entityId) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<MdmAttributePO>()
                .eq(MdmAttributePO::getProjectId, projectId)
                .eq(MdmAttributePO::getEntityId, entityId)
                .eq(MdmAttributePO::getAttrType, MdmAttributeType.PK.name()))
        > 0;
  }

  @Override
  public boolean hasReferences(Long entityId, String attributeCode) {
    return mapper.hasReferences(requiredProjectId(), entityId, attributeCode);
  }

  @Override
  public boolean update(MdmAttribute attribute) {
    Long projectId = requiredProjectId();
    MdmAttributePO po = new MdmAttributePO();
    po.setAttrName(attribute.name());
    po.setAttrType(attribute.type() == null ? null : attribute.type().name());
    po.setDataType(attribute.dataType());
    po.setStdTypeId(attribute.stdTypeId());
    po.setStdUnitId(attribute.stdUnitId());
    po.setStdCodeSetCode(attribute.stdCodeSetCode());
    po.setStdSecurityId(attribute.stdSecurityId());
    po.setIsRequired(attribute.required());
    po.setBusinessDesc(attribute.businessDesc());
    po.setSortOrder(attribute.sortOrder());
    return mapper.update(po, baseQuery(projectId, attribute.id())) > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    Long projectId = requiredProjectId();
    return mapper.delete(baseQuery(projectId, id)) > 0;
  }

  private LambdaQueryWrapper<MdmAttributePO> baseQuery(Long projectId, Long id) {
    return new LambdaQueryWrapper<MdmAttributePO>()
        .eq(MdmAttributePO::getId, id)
        .eq(MdmAttributePO::getProjectId, projectId);
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private static MdmAttributePO toPo(MdmAttribute attribute) {
    MdmAttributePO po = new MdmAttributePO();
    po.setId(attribute.id());
    po.setEntityId(attribute.entityId());
    po.setAttrCode(attribute.code());
    po.setAttrName(attribute.name());
    po.setAttrType(attribute.type() == null ? null : attribute.type().name());
    po.setDataType(attribute.dataType());
    po.setStdTypeId(attribute.stdTypeId());
    po.setStdUnitId(attribute.stdUnitId());
    po.setStdCodeSetCode(attribute.stdCodeSetCode());
    po.setStdSecurityId(attribute.stdSecurityId());
    po.setIsRequired(attribute.required());
    po.setBusinessDesc(attribute.businessDesc());
    po.setSortOrder(attribute.sortOrder());
    po.setStatus(attribute.status());
    return po;
  }

  private static MdmAttribute toDomain(MdmAttributePO po) {
    return new MdmAttribute(
        po.getId(),
        po.getEntityId(),
        po.getAttrCode(),
        po.getAttrName(),
        po.getAttrType() == null ? null : MdmAttributeType.valueOf(po.getAttrType()),
        po.getDataType(),
        po.getStdTypeId(),
        po.getStdUnitId(),
        po.getStdCodeSetCode(),
        po.getStdSecurityId(),
        Boolean.TRUE.equals(po.getIsRequired()),
        po.getBusinessDesc(),
        po.getSortOrder() == null ? 0 : po.getSortOrder(),
        po.getStatus(),
        po.getCreatedBy(),
        po.getCreateTime(),
        po.getUpdateTime());
  }
}
