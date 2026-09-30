package io.yak.ops.business.semantic.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.semantic.api.WarehouseLayer;
import io.yak.ops.common.bean.po.semantic.SemanticLayerPO;
import io.yak.ops.common.bean.po.semantic.SemanticLayerTemplatePO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis adapters for warehouse layers and their platform templates. */
@Repository
@RequiredArgsConstructor
public class SemanticLayerRepositories implements SemanticLayerRepository, SemanticLayerTemplateRepository {

  private final io.yak.ops.business.semantic.dao.mapper.SemanticLayerMapper layerMapper;
  private final io.yak.ops.business.semantic.dao.mapper.SemanticLayerTemplateMapper templateMapper;
  private final CurrentProject currentProject;

  // ---------- layer config (project-scoped) ----------

  @Override
  public WarehouseLayer insert(WarehouseLayer layer, String operator) {
    Long projectId = currentProject.requireProjectId();
    LocalDateTime now = LocalDateTime.now();
    SemanticLayerPO po = toPo(layer);
    po.setProjectId(projectId);
    po.setCreatedBy(operator);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    layerMapper.insert(po);
    return layer;
  }

  @Override
  public Optional<WarehouseLayer> findById(Long id) {
    Long projectId = currentProject.requireProjectId();
    return layerMapper
        .selectList(
            new LambdaQueryWrapper<SemanticLayerPO>()
                .eq(SemanticLayerPO::getId, id)
                .eq(SemanticLayerPO::getProjectId, projectId))
        .stream()
        .findFirst()
        .map(SemanticLayerRepositories::toDomain);
  }

  @Override
  public boolean existsByCode(String code) {
    Long projectId = currentProject.requireProjectId();
    return layerMapper.selectCount(
            new LambdaQueryWrapper<SemanticLayerPO>()
                .eq(SemanticLayerPO::getProjectId, projectId)
                .eq(SemanticLayerPO::getLayerCode, code))
        > 0;
  }

  @Override
  public List<WarehouseLayer> list() {
    Long projectId = currentProject.requireProjectId();
    return layerMapper
        .selectList(
            new LambdaQueryWrapper<SemanticLayerPO>()
                .eq(SemanticLayerPO::getProjectId, projectId)
                .orderByAsc(SemanticLayerPO::getSortOrder)
                .orderByAsc(SemanticLayerPO::getId))
        .stream()
        .map(SemanticLayerRepositories::toDomain)
        .toList();
  }

  @Override
  public boolean update(WarehouseLayer layer) {
    Long projectId = currentProject.requireProjectId();
    SemanticLayerPO po = toPo(layer);
    po.setUpdateTime(LocalDateTime.now());
    return layerMapper.update(
            po,
            new LambdaQueryWrapper<SemanticLayerPO>()
                .eq(SemanticLayerPO::getId, layer.id())
                .eq(SemanticLayerPO::getProjectId, projectId))
        > 0;
  }

  @Override
  public boolean changeStatus(Long id, String status) {
    Long projectId = currentProject.requireProjectId();
    SemanticLayerPO po = new SemanticLayerPO();
    po.setStatus(status);
    po.setUpdateTime(LocalDateTime.now());
    return layerMapper.update(
            po,
            new LambdaQueryWrapper<SemanticLayerPO>()
                .eq(SemanticLayerPO::getId, id)
                .eq(SemanticLayerPO::getProjectId, projectId))
        > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    Long projectId = currentProject.requireProjectId();
    return layerMapper.delete(
            new LambdaQueryWrapper<SemanticLayerPO>()
                .eq(SemanticLayerPO::getId, id)
                .eq(SemanticLayerPO::getProjectId, projectId))
        > 0;
  }

  @Override
  public long countByNamingStandard(Long standardId) {
    return layerMapper.selectCount(
        new LambdaQueryWrapper<SemanticLayerPO>()
            .eq(SemanticLayerPO::getProjectId, currentProject.requireProjectId())
            .eq(SemanticLayerPO::getStdNamingId, standardId));
  }

  @Override
  public int maxSortOrder() {
    Long projectId = currentProject.requireProjectId();
    List<Object> max =
        layerMapper.selectObjs(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SemanticLayerPO>()
                .select("COALESCE(MAX(sort_order), 0)")
                .eq("project_id", projectId));
    return max.isEmpty() || max.get(0) == null ? 0 : ((Number) max.get(0)).intValue();
  }

  // ---------- layer template (platform-level) ----------

  @Override
  public List<io.yak.ops.business.semantic.layer.LayerTemplate> findAll() {
    return templateMapper
        .selectList(
            new LambdaQueryWrapper<SemanticLayerTemplatePO>()
                .orderByAsc(SemanticLayerTemplatePO::getSortOrder)
                .orderByAsc(SemanticLayerTemplatePO::getId))
        .stream()
        .map(
            po ->
                new io.yak.ops.business.semantic.layer.LayerTemplate(
                    po.getId(), po.getLayerCode(), po.getLayerName(), po.getDescription(),
                    po.getDatabaseName(), po.getStdNamingCode(), po.getDefaultPartition(),
                    po.getStorageFormat(), po.getLifecycleDays(), po.getSortOrder(),
                    Boolean.TRUE.equals(po.getStdMandatory())))
        .toList();
  }

  private static WarehouseLayer toDomain(SemanticLayerPO po) {
    return new WarehouseLayer(
        po.getId(), po.getLayerCode(), po.getLayerName(), po.getDatabaseName(),
        po.getDatasourceId(), po.getStdNamingId(), po.getDefaultPartition(),
        po.getStorageFormat(), po.getLifecycleDays(), po.getDescription(), po.getSortOrder(),
        po.getStatus(), Boolean.TRUE.equals(po.getStdMandatory()),
        Boolean.TRUE.equals(po.getIsPreset()), po.getCreatedBy(),
        po.getCreateTime(), po.getUpdateTime());
  }

  private static SemanticLayerPO toPo(WarehouseLayer layer) {
    SemanticLayerPO po = new SemanticLayerPO();
    po.setId(layer.id());
    po.setLayerCode(layer.code());
    po.setLayerName(layer.name());
    po.setDatabaseName(layer.databaseName());
    po.setDatasourceId(layer.datasourceId());
    po.setStdNamingId(layer.stdNamingId());
    po.setDefaultPartition(layer.defaultPartition());
    po.setStorageFormat(layer.storageFormat());
    po.setLifecycleDays(layer.lifecycleDays());
    po.setDescription(layer.description());
    po.setSortOrder(layer.sortOrder());
    po.setStatus(layer.status());
    po.setStdMandatory(layer.stdMandatory());
    po.setIsPreset(layer.preset());
    return po;
  }
}
