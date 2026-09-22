package io.yak.ops.business.semantic.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.semantic.dao.mapper.SemanticDomainMapper;
import io.yak.ops.business.semantic.api.BusinessDomain;
import io.yak.ops.common.bean.po.semantic.SemanticDomainPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** MyBatis adapter for the business domain tree (project-scoped reads/writes). */
@Repository
public class BusinessDomainRepositoryAdapter implements SemanticDomainRepository {

  private final SemanticDomainMapper mapper;
  private final CurrentProject currentProject;

  public BusinessDomainRepositoryAdapter(SemanticDomainMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public BusinessDomain insert(BusinessDomain domain, String operator) {
    Long projectId = requiredProjectId();
    LocalDateTime now = LocalDateTime.now();
    SemanticDomainPO po = toPo(domain);
    po.setProjectId(projectId);
    po.setCreatedBy(operator);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    mapper.insert(po);
    return domain.withPersisted(po.getId(), operator, now);
  }

  @Override
  public Optional<BusinessDomain> findById(Long id) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<SemanticDomainPO>()
                    .eq(SemanticDomainPO::getId, id)
                    .eq(SemanticDomainPO::getProjectId, projectId)))
        .map(BusinessDomainRepositoryAdapter::toDomain);
  }

  @Override
  public boolean existsByCode(String code) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<SemanticDomainPO>()
                .eq(SemanticDomainPO::getProjectId, projectId)
                .eq(SemanticDomainPO::getDomainCode, code))
        > 0;
  }

  @Override
  public List<BusinessDomain> findAll() {
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<SemanticDomainPO>()
                .eq(SemanticDomainPO::getProjectId, projectId)
                .orderByAsc(SemanticDomainPO::getSortOrder)
                .orderByAsc(SemanticDomainPO::getId))
        .stream()
        .map(BusinessDomainRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean existsByParent(Long parentId) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<SemanticDomainPO>()
                .eq(SemanticDomainPO::getProjectId, projectId)
                .eq(SemanticDomainPO::getParentId, parentId))
        > 0;
  }

  @Override
  public boolean update(BusinessDomain domain) {
    Long projectId = requiredProjectId();
    SemanticDomainPO po = new SemanticDomainPO();
    po.setId(domain.id());
    po.setDomainName(domain.name());
    po.setOwner(domain.owner());
    po.setDescription(domain.description());
    po.setSortOrder(domain.sortOrder());
    return mapper.update(po, baseQuery(projectId, domain.id())) > 0;
  }

  @Override
  public boolean move(Long id, Long parentId, int sortOrder) {
    Long projectId = requiredProjectId();
    SemanticDomainPO po = new SemanticDomainPO();
    po.setParentId(parentId);
    po.setSortOrder(sortOrder);
    return mapper.update(po, baseQuery(projectId, id)) > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    Long projectId = requiredProjectId();
    return mapper.delete(baseQuery(projectId, id)) > 0;
  }

  private LambdaQueryWrapper<SemanticDomainPO> baseQuery(Long projectId, Long id) {
    return new LambdaQueryWrapper<SemanticDomainPO>()
        .eq(SemanticDomainPO::getId, id)
        .eq(SemanticDomainPO::getProjectId, projectId);
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private static SemanticDomainPO toPo(BusinessDomain domain) {
    SemanticDomainPO po = new SemanticDomainPO();
    po.setId(domain.id());
    po.setDomainCode(domain.code());
    po.setDomainName(domain.name());
    po.setParentId(domain.parentId());
    po.setOwner(domain.owner());
    po.setDescription(domain.description());
    po.setSortOrder(domain.sortOrder());
    return po;
  }

  private static BusinessDomain toDomain(SemanticDomainPO po) {
    return new BusinessDomain(
        po.getId(),
        po.getDomainCode(),
        po.getDomainName(),
        po.getParentId(),
        po.getOwner(),
        po.getDescription(),
        po.getSortOrder(),
        po.getCreatedBy(),
        po.getCreateTime(),
        po.getUpdateTime());
  }
}
