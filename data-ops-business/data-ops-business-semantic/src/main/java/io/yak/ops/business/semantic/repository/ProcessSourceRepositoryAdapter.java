package io.yak.ops.business.semantic.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.semantic.binding.ProcessSourceBinding;
import io.yak.ops.business.semantic.dao.mapper.SemanticProcessSourceMapper;
import io.yak.ops.business.semantic.dao.model.SemanticProcessSourcePO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for process-source bindings (project-scoped). */
@Repository
@RequiredArgsConstructor
public class ProcessSourceRepositoryAdapter implements SemanticProcessSourceRepository {

  private final SemanticProcessSourceMapper mapper;
  private final CurrentProject currentProject;

  @Override
  public ProcessSourceBinding insert(ProcessSourceBinding binding, String operator) {
    Long projectId = requiredProjectId();
    LocalDateTime now = LocalDateTime.now();
    SemanticProcessSourcePO po = toPo(binding);
    po.setProjectId(projectId);
    po.setCreatedBy(operator);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    mapper.insert(po);
    return toDomain(po);
  }

  @Override
  public List<ProcessSourceBinding> listByProcess(Long processId) {
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<SemanticProcessSourcePO>()
                .eq(SemanticProcessSourcePO::getProjectId, projectId)
                .eq(SemanticProcessSourcePO::getProcessId, processId)
                .orderByAsc(SemanticProcessSourcePO::getId))
        .stream()
        .map(ProcessSourceRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean existsByProcess(Long processId) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<SemanticProcessSourcePO>()
                .eq(SemanticProcessSourcePO::getProjectId, projectId)
                .eq(SemanticProcessSourcePO::getProcessId, processId))
        > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    Long projectId = requiredProjectId();
    return mapper.delete(
            new LambdaQueryWrapper<SemanticProcessSourcePO>()
                .eq(SemanticProcessSourcePO::getId, id)
                .eq(SemanticProcessSourcePO::getProjectId, projectId))
        > 0;
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private static SemanticProcessSourcePO toPo(ProcessSourceBinding binding) {
    SemanticProcessSourcePO po = new SemanticProcessSourcePO();
    po.setId(binding.id());
    po.setProcessId(binding.processId());
    po.setDatasourceId(binding.datasourceId());
    po.setSourceTable(binding.sourceTable());
    po.setTableRole(binding.tableRole());
    po.setJoinCondition(binding.joinCondition());
    return po;
  }

  private static ProcessSourceBinding toDomain(SemanticProcessSourcePO po) {
    return new ProcessSourceBinding(
        po.getId(),
        po.getProcessId(),
        po.getDatasourceId(),
        po.getSourceTable(),
        po.getTableRole(),
        po.getJoinCondition(),
        po.getCreatedBy(),
        po.getCreateTime(),
        po.getUpdateTime());
  }
}
