package io.yak.ops.business.semantic.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.semantic.dao.mapper.SemanticProcessMapper;
import io.yak.ops.business.semantic.api.BusinessProcess;
import io.yak.ops.business.semantic.dao.model.SemanticProcessPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** MyBatis adapter for business processes (project-scoped). */
@Repository
public class BusinessProcessRepositoryAdapter implements SemanticProcessRepository {

  private final SemanticProcessMapper mapper;
  private final CurrentProject currentProject;

  public BusinessProcessRepositoryAdapter(SemanticProcessMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public BusinessProcess insert(BusinessProcess process, String operator) {
    Long projectId = requiredProjectId();
    LocalDateTime now = LocalDateTime.now();
    SemanticProcessPO po = toPo(process);
    po.setProjectId(projectId);
    po.setCreatedBy(operator);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    mapper.insert(po);
    return toDomain(po);
  }

  @Override
  public Optional<BusinessProcess> findById(Long id) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<SemanticProcessPO>()
                    .eq(SemanticProcessPO::getId, id)
                    .eq(SemanticProcessPO::getProjectId, projectId)))
        .map(BusinessProcessRepositoryAdapter::toDomain);
  }

  @Override
  public boolean existsByCode(String code) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<SemanticProcessPO>()
                .eq(SemanticProcessPO::getProjectId, projectId)
                .eq(SemanticProcessPO::getProcessCode, code))
        > 0;
  }

  @Override
  public PageData<BusinessProcess> page(
      int pageNo, int pageSize, Long domainId, String keyword, String bizType) {
    Long projectId = requiredProjectId();
    Page<SemanticProcessPO> page = Page.of(Math.max(1, pageNo), Math.max(1, pageSize));
    LambdaQueryWrapper<SemanticProcessPO> wrapper =
        new LambdaQueryWrapper<SemanticProcessPO>().eq(SemanticProcessPO::getProjectId, projectId);
    if (domainId != null) {
      wrapper.eq(SemanticProcessPO::getDomainId, domainId);
    }
    if (StringUtils.hasText(bizType)) {
      wrapper.eq(SemanticProcessPO::getBizType, bizType);
    }
    if (StringUtils.hasText(keyword)) {
      wrapper.and(
          nested ->
              nested
                  .like(SemanticProcessPO::getProcessCode, keyword)
                  .or()
                  .like(SemanticProcessPO::getProcessName, keyword));
    }
    wrapper.orderByAsc(SemanticProcessPO::getSortOrder).orderByAsc(SemanticProcessPO::getId);
    Page<SemanticProcessPO> result = mapper.selectPage(page, wrapper);
    List<BusinessProcess> records =
        result.getRecords().stream().map(BusinessProcessRepositoryAdapter::toDomain).toList();
    return new PageData<>(
        records, result.getTotal(), result.getPages(), (long) pageNo, (long) pageSize);
  }

  @Override
  public List<BusinessProcess> listByDomain(Long domainId) {
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<SemanticProcessPO>()
                .eq(SemanticProcessPO::getProjectId, projectId)
                .eq(SemanticProcessPO::getDomainId, domainId)
                .orderByAsc(SemanticProcessPO::getSortOrder)
                .orderByAsc(SemanticProcessPO::getId))
        .stream()
        .map(BusinessProcessRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean existsByDomain(Long domainId) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<SemanticProcessPO>()
                .eq(SemanticProcessPO::getProjectId, projectId)
                .eq(SemanticProcessPO::getDomainId, domainId))
        > 0;
  }

  @Override
  public boolean update(BusinessProcess process) {
    Long projectId = requiredProjectId();
    SemanticProcessPO po = toPo(process);
    po.setUpdateTime(LocalDateTime.now());
    return mapper.update(
            po,
            new LambdaUpdateWrapper<SemanticProcessPO>()
                .set(SemanticProcessPO::getGrain, process.grain())
                .set(SemanticProcessPO::getOwner, process.owner())
                .set(SemanticProcessPO::getDescription, process.description())
                .eq(SemanticProcessPO::getId, process.id())
                .eq(SemanticProcessPO::getProjectId, projectId))
        > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    Long projectId = requiredProjectId();
    return mapper.delete(
            new LambdaQueryWrapper<SemanticProcessPO>()
                .eq(SemanticProcessPO::getId, id)
                .eq(SemanticProcessPO::getProjectId, projectId))
        > 0;
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private static SemanticProcessPO toPo(BusinessProcess process) {
    SemanticProcessPO po = new SemanticProcessPO();
    po.setId(process.id());
    po.setProcessCode(process.code());
    po.setProcessName(process.name());
    po.setDomainId(process.domainId());
    po.setGrain(process.grain());
    po.setBizType(process.bizType());
    po.setOwner(process.owner());
    po.setDescription(process.description());
    po.setSortOrder(process.sortOrder());
    return po;
  }

  private static BusinessProcess toDomain(SemanticProcessPO po) {
    return new BusinessProcess(
        po.getId(),
        po.getProcessCode(),
        po.getProcessName(),
        po.getDomainId(),
        po.getGrain(),
        po.getBizType(),
        po.getOwner(),
        po.getDescription(),
        po.getSortOrder(),
        po.getCreatedBy(),
        po.getCreateTime(),
        po.getUpdateTime());
  }
}
