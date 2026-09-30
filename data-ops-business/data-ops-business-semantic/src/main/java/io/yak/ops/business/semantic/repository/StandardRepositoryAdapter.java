package io.yak.ops.business.semantic.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.business.semantic.dao.StandardListRow;
import io.yak.ops.business.semantic.dao.mapper.SemanticStandardMapper;
import io.yak.ops.common.bean.po.semantic.SemanticStandardPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * MyBatis adapter for the semantic standard catalog. Every read and write is
 * bound to the project space from the trusted server-side context
 * (PROJECT_SCOPE). Deletion is physical (dictionary row; DOMAIN.md).
 */
@Repository
public class StandardRepositoryAdapter implements SemanticStandardRepository {

  private final SemanticStandardMapper mapper;
  private final CurrentProject currentProject;

  public StandardRepositoryAdapter(SemanticStandardMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public Standard insert(Standard standard, String operator) {
    Long projectId = requiredProjectId();
    LocalDateTime now = LocalDateTime.now();
    SemanticStandardPO po = toPo(standard);
    po.setProjectId(projectId);
    po.setCreatedBy(operator);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    mapper.insert(po);
    return standard.withPersisted(po.getId(), operator, now);
  }

  @Override
  public Optional<Standard> findById(Long id) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<SemanticStandardPO>()
                    .eq(SemanticStandardPO::getId, id)
                    .eq(SemanticStandardPO::getProjectId, projectId)))
        .map(StandardRepositoryAdapter::toDomain);
  }

  @Override
  public Optional<Standard> findByIdForUpdate(Long id) {
    return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<SemanticStandardPO>()
        .eq(SemanticStandardPO::getId, id)
        .eq(SemanticStandardPO::getProjectId, requiredProjectId()).last("FOR UPDATE")))
        .map(StandardRepositoryAdapter::toDomain);
  }

  @Override
  public boolean existsByCode(StandardKind kind, String code) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<SemanticStandardPO>()
                .eq(SemanticStandardPO::getProjectId, projectId)
                .eq(SemanticStandardPO::getKind, kind.name())
                .eq(SemanticStandardPO::getStdCode, code))
        > 0;
  }

  @Override
  public PageData<StandardListRow> pageListRows(
      int pageNo, int pageSize, StandardKind kind, String keyword, StandardStatus status) {
    Page<StandardListRow> page = Page.of(Math.max(1, pageNo), Math.max(1, pageSize));
    IPage<StandardListRow> result =
        mapper.selectListRows(
            page,
            requiredProjectId(),
            kind == null ? null : kind.name(),
            StringUtils.hasText(keyword) ? keyword.trim() : null,
            status == null ? null : status.name());
    return new PageData<>(
        result.getRecords(), result.getTotal(), result.getPages(), (long) pageNo, (long) pageSize);
  }

  @Override
  public List<StandardListRow> listEnabledCodeSetOptions() {
    return mapper.selectEnabledCodeSetOptions(requiredProjectId());
  }

  @Override
  public Optional<Standard> findByCode(StandardKind kind, String code) {
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<SemanticStandardPO>()
                .eq(SemanticStandardPO::getProjectId, projectId)
                .eq(SemanticStandardPO::getKind, kind.name())
                .eq(SemanticStandardPO::getStdCode, code))
        .stream()
        .findFirst()
        .map(StandardRepositoryAdapter::toDomain);
  }

  @Override
  public List<Standard> findByIds(java.util.Collection<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<SemanticStandardPO>()
                .eq(SemanticStandardPO::getProjectId, projectId)
                .in(SemanticStandardPO::getId, ids))
        .stream()
        .map(StandardRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<Standard> listEnabledByKind(StandardKind kind) {
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<SemanticStandardPO>()
                .eq(SemanticStandardPO::getProjectId, projectId)
                .eq(SemanticStandardPO::getKind, kind.name())
                .eq(SemanticStandardPO::getStatus, StandardStatus.ENABLED.name())
                .orderByAsc(SemanticStandardPO::getSortOrder)
                .orderByAsc(SemanticStandardPO::getId))
        .stream()
        .map(StandardRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean existsEnabledByCodeSet(String codeSetCode) {
    Long projectId = requiredProjectId();
    long total = mapper.selectCount(new LambdaQueryWrapper<SemanticStandardPO>()
        .eq(SemanticStandardPO::getProjectId, projectId)
        .eq(SemanticStandardPO::getKind, StandardKind.CODE.name())
        .eq(SemanticStandardPO::getCodeSetCode, codeSetCode));
    if (total == 0) return false;
    long enabled = mapper.selectCount(new LambdaQueryWrapper<SemanticStandardPO>()
        .eq(SemanticStandardPO::getProjectId, projectId)
        .eq(SemanticStandardPO::getKind, StandardKind.CODE.name())
        .eq(SemanticStandardPO::getCodeSetCode, codeSetCode)
        .eq(SemanticStandardPO::getStatus, StandardStatus.ENABLED.name()));
    return total == enabled;
  }

  @Override
  public Standard update(Standard standard, Integer expectedVersion, String operator) {
    Long projectId = requiredProjectId();
    LocalDateTime now = LocalDateTime.now();
    SemanticStandardPO po = toPo(standard);
    LambdaUpdateWrapper<SemanticStandardPO> update = new LambdaUpdateWrapper<SemanticStandardPO>()
        .set(SemanticStandardPO::getStdName, po.getStdName())
        .set(SemanticStandardPO::getStatus, po.getStatus())
        .set(SemanticStandardPO::getVersion, po.getVersion())
        .set(SemanticStandardPO::getSortOrder, po.getSortOrder())
        .set(SemanticStandardPO::getDescription, po.getDescription())
        .set(SemanticStandardPO::getScope, po.getScope())
        .set(SemanticStandardPO::getLayer, po.getLayer())
        .set(SemanticStandardPO::getRuleExpr, po.getRuleExpr())
        .set(SemanticStandardPO::getExample, po.getExample())
        .set(SemanticStandardPO::getTypeCode, po.getTypeCode())
        .set(SemanticStandardPO::getStdType, po.getStdType())
        .set(SemanticStandardPO::getSourceMapping, po.getSourceMapping())
        .set(SemanticStandardPO::getCodeSetCode, po.getCodeSetCode())
        .set(SemanticStandardPO::getCodeValue, po.getCodeValue())
        .set(SemanticStandardPO::getCodeLabel, po.getCodeLabel())
        .set(SemanticStandardPO::getUnitCode, po.getUnitCode())
        .set(SemanticStandardPO::getUnitType, po.getUnitType())
        .set(SemanticStandardPO::getCaliberCode, po.getCaliberCode())
        .set(SemanticStandardPO::getCalRule, po.getCalRule())
        .set(SemanticStandardPO::getBusinessDesc, po.getBusinessDesc())
        .set(SemanticStandardPO::getLevelCode, po.getLevelCode())
        .set(SemanticStandardPO::getMaskRule, po.getMaskRule())
        .set(SemanticStandardPO::getUpdateTime, now)
        .eq(SemanticStandardPO::getId, standard.id())
        .eq(SemanticStandardPO::getProjectId, projectId)
        .eq(SemanticStandardPO::getVersion, expectedVersion);
    int updated = mapper.update(null, update);
    if (updated == 0) {
      throw new io.yak.ops.business.semantic.exception.SemanticException(
          io.yak.ops.common.enums.semantic.SemanticErrorCode.VERSION_CONFLICT,
          "标准已被他人修改，请刷新后重试");
    }
    return standard.withVersion(standard.version(), now);
  }

  @Override
  public boolean updateStatus(Long id, StandardStatus status, String operator) {
    Long projectId = requiredProjectId();
    SemanticStandardPO po = new SemanticStandardPO();
    po.setStatus(status.name());
    po.setUpdateTime(LocalDateTime.now());
    return mapper.update(
            po,
            new LambdaQueryWrapper<SemanticStandardPO>()
                .eq(SemanticStandardPO::getId, id)
                .eq(SemanticStandardPO::getProjectId, projectId))
        > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    Long projectId = requiredProjectId();
    return mapper.delete(
            new LambdaQueryWrapper<SemanticStandardPO>()
                .eq(SemanticStandardPO::getId, id)
                .eq(SemanticStandardPO::getProjectId, projectId))
        > 0;
  }

  @Override
  public long countByProject() {
    return mapper.selectCount(
        new LambdaQueryWrapper<SemanticStandardPO>().eq(SemanticStandardPO::getProjectId, requiredProjectId()));
  }

  // ── 码集方法(32.1) ──

  /**
   * 组定位:常规码集按 code_set_code;存量空码集行(code_set_code 为空)按
   * std_code 独立成组,编辑时可补全码集编码采纳进新码集。
   */
  private LambdaQueryWrapper<SemanticStandardPO> codeSetGroupWrapper(Long projectId, String groupKey) {
    return new LambdaQueryWrapper<SemanticStandardPO>()
        .eq(SemanticStandardPO::getProjectId, projectId)
        .eq(SemanticStandardPO::getKind, StandardKind.CODE.name())
        .and(
            nested ->
                nested
                    .eq(SemanticStandardPO::getCodeSetCode, groupKey)
                    .or(
                        legacy ->
                            legacy
                            .eq(SemanticStandardPO::getStdCode, groupKey)
                            .and(noSet -> noSet.isNull(SemanticStandardPO::getCodeSetCode)
                                .or().eq(SemanticStandardPO::getCodeSetCode, ""))));
  }

  @Override
  public List<Standard> listByCodeSetCode(String codeSetCode) {
    return mapper
        .selectList(
            codeSetGroupWrapper(requiredProjectId(), codeSetCode)
                .orderByAsc(SemanticStandardPO::getSortOrder)
                .orderByAsc(SemanticStandardPO::getId))
        .stream()
        .map(StandardRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<Standard> lockCodeSet(String codeSetCode) {
    return mapper.selectList(codeSetGroupWrapper(requiredProjectId(), codeSetCode)
        .orderByAsc(SemanticStandardPO::getId).last("FOR UPDATE")).stream()
        .map(StandardRepositoryAdapter::toDomain).toList();
  }

  /** 严格按 code_set_code 匹配(不含存量空码集行);创建判重与存在性检查用。 */
  @Override
  public boolean existsByCodeSetCode(String codeSetCode) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<SemanticStandardPO>()
                .eq(SemanticStandardPO::getProjectId, projectId)
                .eq(SemanticStandardPO::getKind, StandardKind.CODE.name())
                .eq(SemanticStandardPO::getCodeSetCode, codeSetCode))
        > 0;
  }

  @Override
  public int deleteByCodeSetCode(String codeSetCode) {
    return mapper.delete(codeSetGroupWrapper(requiredProjectId(), codeSetCode));
  }

  @Override
  public int updateStatusByCodeSetCode(String codeSetCode, StandardStatus status, String operator) {
    SemanticStandardPO po = new SemanticStandardPO();
    po.setStatus(status.name());
    po.setUpdateTime(LocalDateTime.now());
    return mapper.update(po, codeSetGroupWrapper(requiredProjectId(), codeSetCode));
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private static SemanticStandardPO toPo(Standard standard) {
    SemanticStandardPO po = new SemanticStandardPO();
    po.setId(standard.id());
    po.setKind(standard.kind().name());
    po.setStdCode(standard.code());
    po.setStdName(standard.name());
    po.setStatus(standard.status().name());
    po.setVersion(standard.version());
    po.setSortOrder(standard.sortOrder());
    po.setIsPreset(standard.preset());
    po.setDescription(standard.description());
    Standard.KindFields fields = standard.fields();
    po.setScope(fields.scope());
    po.setLayer(fields.layer());
    po.setRuleExpr(fields.ruleExpr());
    po.setExample(fields.example());
    po.setTypeCode(fields.typeCode());
    po.setStdType(fields.stdType());
    po.setSourceMapping(fields.sourceMapping());
    po.setCodeSetCode(fields.codeSetCode());
    po.setCodeValue(fields.codeValue());
    po.setCodeLabel(fields.codeLabel());
    po.setUnitCode(fields.unitCode());
    po.setUnitType(fields.unitType());
    po.setCaliberCode(fields.caliberCode());
    po.setCalRule(fields.calRule());
    po.setBusinessDesc(fields.businessDesc());
    po.setLevelCode(fields.levelCode());
    po.setMaskRule(fields.maskRule());
    return po;
  }

  private static Standard toDomain(SemanticStandardPO po) {
    return new Standard(
        po.getId(),
        StandardKind.fromStored(po.getKind()).orElseThrow(() -> new IllegalArgumentException(po.getKind())),
        po.getStdCode(),
        po.getStdName(),
        StandardStatus.fromStored(po.getStatus()).orElse(StandardStatus.ENABLED),
        po.getVersion(),
        po.getSortOrder(),
        Boolean.TRUE.equals(po.getIsPreset()),
        po.getDescription(),
        new Standard.KindFields(
            po.getScope(),
            po.getLayer(),
            po.getRuleExpr(),
            po.getExample(),
            po.getTypeCode(),
            po.getStdType(),
            po.getSourceMapping(),
            po.getCodeSetCode(),
            po.getCodeValue(),
            po.getCodeLabel(),
            po.getUnitCode(),
            po.getUnitType(),
            po.getCaliberCode(),
            po.getCalRule(),
            po.getBusinessDesc(),
            po.getLevelCode(),
            po.getMaskRule()),
        po.getCreatedBy(),
        po.getCreateTime(),
        po.getUpdateTime());
  }
}
