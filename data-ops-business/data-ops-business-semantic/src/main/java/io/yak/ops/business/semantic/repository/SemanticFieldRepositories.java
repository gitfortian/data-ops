package io.yak.ops.business.semantic.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.semantic.dao.mapper.SemanticFieldMapper;
import io.yak.ops.business.semantic.dao.mapper.SemanticProcessFieldMapper;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.common.bean.po.semantic.SemanticFieldPO;
import io.yak.ops.common.bean.po.semantic.SemanticProcessFieldPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** MyBatis adapters for the field library and process-field references. */
@Repository
public class SemanticFieldRepositories implements SemanticFieldRepository, SemanticProcessFieldRepository {

  private final SemanticFieldMapper fieldMapper;
  private final SemanticProcessFieldMapper processFieldMapper;
  private final CurrentProject currentProject;

  public SemanticFieldRepositories(
      SemanticFieldMapper fieldMapper,
      SemanticProcessFieldMapper processFieldMapper,
      CurrentProject currentProject) {
    this.fieldMapper = fieldMapper;
    this.processFieldMapper = processFieldMapper;
    this.currentProject = currentProject;
  }

  // ---------- field library ----------

  @Override
  public StandardField insert(StandardField field, String operator) {
    Long projectId = requiredProjectId();
    LocalDateTime now = LocalDateTime.now();
    SemanticFieldPO po = toPo(field);
    po.setProjectId(projectId);
    po.setCreatedBy(operator);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    fieldMapper.insert(po);
    return field;
  }

  @Override
  public Optional<StandardField> findById(Long id) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            fieldMapper.selectOne(
                new LambdaQueryWrapper<SemanticFieldPO>()
                    .eq(SemanticFieldPO::getId, id)
                    .eq(SemanticFieldPO::getProjectId, projectId)))
        .map(SemanticFieldRepositories::toDomain);
  }

  @Override
  public boolean existsByCode(String code) {
    Long projectId = requiredProjectId();
    return fieldMapper.selectCount(
            new LambdaQueryWrapper<SemanticFieldPO>()
                .eq(SemanticFieldPO::getProjectId, projectId)
                .eq(SemanticFieldPO::getFieldCode, code))
        > 0;
  }

  @Override
  public PageData<StandardField> page(int pageNo, int pageSize, String role, String keyword) {
    Long projectId = requiredProjectId();
    Page<SemanticFieldPO> page = Page.of(Math.max(1, pageNo), Math.max(1, pageSize));
    LambdaQueryWrapper<SemanticFieldPO> wrapper =
        new LambdaQueryWrapper<SemanticFieldPO>().eq(SemanticFieldPO::getProjectId, projectId);
    if (StringUtils.hasText(role)) {
      wrapper.eq(SemanticFieldPO::getRole, role);
    }
    if (StringUtils.hasText(keyword)) {
      wrapper.and(
          nested ->
              nested
                  .like(SemanticFieldPO::getFieldCode, keyword)
                  .or()
                  .like(SemanticFieldPO::getFieldName, keyword));
    }
    wrapper.orderByAsc(SemanticFieldPO::getId);
    Page<SemanticFieldPO> result = fieldMapper.selectPage(page, wrapper);
    List<StandardField> records =
        result.getRecords().stream().map(SemanticFieldRepositories::toDomain).toList();
    return new PageData<>(
        records, result.getTotal(), result.getPages(), (long) pageNo, (long) pageSize);
  }

  @Override
  public List<StandardField> listEnabled(String keyword) {
    Long projectId = requiredProjectId();
    LambdaQueryWrapper<SemanticFieldPO> wrapper =
        new LambdaQueryWrapper<SemanticFieldPO>()
            .eq(SemanticFieldPO::getProjectId, projectId)
            .eq(SemanticFieldPO::getStatus, StandardField.STATUS_ENABLED);
    if (StringUtils.hasText(keyword)) {
      String trimmed = keyword.trim();
      wrapper.and(
          nested ->
              nested
                  .like(SemanticFieldPO::getFieldCode, trimmed)
                  .or()
                  .like(SemanticFieldPO::getFieldName, trimmed));
    }
    wrapper.orderByAsc(SemanticFieldPO::getId);
    return fieldMapper.selectList(wrapper).stream()
        .map(SemanticFieldRepositories::toDomain)
        .toList();
  }

  @Override
  public StandardField update(StandardField field) {
    Long projectId = requiredProjectId();
    SemanticFieldPO po = toPo(field);
    po.setUpdateTime(LocalDateTime.now());
    fieldMapper.update(
        po,
        new LambdaQueryWrapper<SemanticFieldPO>()
            .eq(SemanticFieldPO::getId, field.id())
            .eq(SemanticFieldPO::getProjectId, projectId));
    return field.withUpdateTime(po.getUpdateTime());
  }

  @Override
  public boolean changeStatus(Long id, String status) {
    Long projectId = requiredProjectId();
    SemanticFieldPO po = new SemanticFieldPO();
    po.setStatus(status);
    po.setUpdateTime(LocalDateTime.now());
    return fieldMapper.update(
            po,
            new LambdaQueryWrapper<SemanticFieldPO>()
                .eq(SemanticFieldPO::getId, id)
                .eq(SemanticFieldPO::getProjectId, projectId))
        > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    Long projectId = requiredProjectId();
    return fieldMapper.delete(
            new LambdaQueryWrapper<SemanticFieldPO>()
                .eq(SemanticFieldPO::getId, id)
                .eq(SemanticFieldPO::getProjectId, projectId))
        > 0;
  }

  @Override
  public long countByCodeSet(String codeSetCode) {
    Long projectId = requiredProjectId();
    return fieldMapper.selectCount(
        new LambdaQueryWrapper<SemanticFieldPO>()
            .eq(SemanticFieldPO::getProjectId, projectId)
            .eq(SemanticFieldPO::getStdCodeSetCode, codeSetCode));
  }

  // ---------- process-field references ----------

  @Override
  public long countByField(Long fieldId) {
    Long projectId = requiredProjectId();
    return processFieldMapper.selectCount(
        new LambdaQueryWrapper<SemanticProcessFieldPO>()
            .eq(SemanticProcessFieldPO::getProjectId, projectId)
            .eq(SemanticProcessFieldPO::getFieldId, fieldId));
  }

  @Override
  public List<ProcessFieldBinding> bindingsByProcess(Long processId) {
    Long projectId = requiredProjectId();
    return processFieldMapper
        .selectList(
            new LambdaQueryWrapper<SemanticProcessFieldPO>()
                .eq(SemanticProcessFieldPO::getProjectId, projectId)
                .eq(SemanticProcessFieldPO::getProcessId, processId)
                .orderByAsc(SemanticProcessFieldPO::getSortOrder)
                .orderByAsc(SemanticProcessFieldPO::getId))
        .stream()
        .map(
            po ->
                new ProcessFieldBinding(
                    po.getFieldId(), Boolean.TRUE.equals(po.getIsRequired()), po.getSortOrder()))
        .toList();
  }

  @Override
  public void bind(Long processId, Long fieldId, boolean required, String operator) {
    Long projectId = requiredProjectId();
    Integer maxOrder = processFieldMapper
        .selectList(
            new LambdaQueryWrapper<SemanticProcessFieldPO>()
                .eq(SemanticProcessFieldPO::getProjectId, projectId)
                .eq(SemanticProcessFieldPO::getProcessId, processId)
                .orderByDesc(SemanticProcessFieldPO::getSortOrder)
                .last("LIMIT 1"))
        .stream()
        .findFirst()
        .map(SemanticProcessFieldPO::getSortOrder)
        .map(order -> order + 1)
        .orElse(0);
    SemanticProcessFieldPO po = new SemanticProcessFieldPO();
    po.setProjectId(projectId);
    po.setProcessId(processId);
    po.setFieldId(fieldId);
    po.setIsRequired(required);
    po.setSortOrder(maxOrder);
    po.setCreatedBy(operator == null ? "system" : operator);
    po.setCreateTime(LocalDateTime.now());
    processFieldMapper.insert(po);
  }

  @Override
  public void unbind(Long processId, Long fieldId) {
    Long projectId = requiredProjectId();
    processFieldMapper.delete(
        new LambdaQueryWrapper<SemanticProcessFieldPO>()
            .eq(SemanticProcessFieldPO::getProjectId, projectId)
            .eq(SemanticProcessFieldPO::getProcessId, processId)
            .eq(SemanticProcessFieldPO::getFieldId, fieldId));
  }

  @Override
  public boolean existsByProcessAndField(Long processId, Long fieldId) {
    Long projectId = requiredProjectId();
    return processFieldMapper.selectCount(
            new LambdaQueryWrapper<SemanticProcessFieldPO>()
                .eq(SemanticProcessFieldPO::getProjectId, projectId)
                .eq(SemanticProcessFieldPO::getProcessId, processId)
                .eq(SemanticProcessFieldPO::getFieldId, fieldId))
        > 0;
  }

  @Override
  public void reorder(Long processId, List<Long> orderedFieldIds) {
    Long projectId = requiredProjectId();
    for (int index = 0; index < orderedFieldIds.size(); index++) {
      SemanticProcessFieldPO po = new SemanticProcessFieldPO();
      po.setSortOrder(index);
      processFieldMapper.update(
          po,
          new LambdaQueryWrapper<SemanticProcessFieldPO>()
              .eq(SemanticProcessFieldPO::getProjectId, projectId)
              .eq(SemanticProcessFieldPO::getProcessId, processId)
              .eq(SemanticProcessFieldPO::getFieldId, orderedFieldIds.get(index)));
    }
  }

  @Override
  public List<ProcessFieldCount> countByProjectGroupedByProcess() {
    Long projectId = requiredProjectId();
    return processFieldMapper
        .selectMaps(
            new QueryWrapper<SemanticProcessFieldPO>()
                .select("process_id", "COUNT(*) AS field_count")
                .eq("project_id", projectId)
                .groupBy("process_id"))
        .stream()
        .map(
            row ->
                new ProcessFieldCount(
                    ((Number) row.get("process_id")).longValue(),
                    ((Number) row.get("field_count")).longValue()))
        .toList();
  }

  @Override
  public long countByProcess(Long processId) {
    Long projectId = requiredProjectId();
    return processFieldMapper.selectCount(
        new LambdaQueryWrapper<SemanticProcessFieldPO>()
            .eq(SemanticProcessFieldPO::getProjectId, projectId)
            .eq(SemanticProcessFieldPO::getProcessId, processId));
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private static SemanticFieldPO toPo(StandardField field) {
    SemanticFieldPO po = new SemanticFieldPO();
    po.setId(field.id());
    po.setFieldCode(field.code());
    po.setFieldName(field.name());
    po.setRole(field.role());
    po.setStatus(field.status());
    po.setDataType(field.dataType());
    po.setStdTypeId(field.stdTypeId());
    po.setStdUnitId(field.stdUnitId());
    po.setStdCaliberId(field.stdCaliberId());
    po.setStdCodeSetCode(field.stdCodeSetCode());
    po.setStdSecurityId(field.stdSecurityId());
    po.setBusinessDesc(field.businessDesc());
    po.setSource(field.source());
    po.setVersion(field.version());
    return po;
  }

  private static StandardField toDomain(SemanticFieldPO po) {
    return new StandardField(
        po.getId(),
        po.getFieldCode(),
        po.getFieldName(),
        po.getRole(),
        po.getStatus(),
        po.getDataType(),
        po.getStdTypeId(),
        po.getStdUnitId(),
        po.getStdCaliberId(),
        po.getStdCodeSetCode(),
        po.getStdSecurityId(),
        po.getBusinessDesc(),
        po.getSource(),
        po.getVersion(),
        false,
        po.getCreatedBy(),
        po.getCreateTime(),
        po.getUpdateTime());
  }
}
