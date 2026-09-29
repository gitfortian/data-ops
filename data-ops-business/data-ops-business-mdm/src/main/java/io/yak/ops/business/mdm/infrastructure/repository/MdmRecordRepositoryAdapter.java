package io.yak.ops.business.mdm.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.mdm.dao.MdmDedupKeyRow;
import io.yak.ops.business.mdm.dao.mapper.MdmRecordMapper;
import io.yak.ops.business.mdm.domain.record.MdmRecord;
import io.yak.ops.business.mdm.domain.record.MdmRecordStatus;
import io.yak.ops.common.bean.po.mdm.MdmRecordPO;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** MyBatis adapter for the master data record (project-scoped). */
@Repository
public class MdmRecordRepositoryAdapter implements MdmRecordRepository {

  private final MdmRecordMapper mapper;
  private final CurrentProject currentProject;

  public MdmRecordRepositoryAdapter(MdmRecordMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public PageData<MdmRecord> page(
      Long entityId,
      int pageNo,
      int pageSize,
      String keyword,
      MdmRecordStatus status,
      List<String> attributeCodes) {
    Long projectId = requiredProjectId();
    LambdaQueryWrapper<MdmRecordPO> wrapper =
        new LambdaQueryWrapper<MdmRecordPO>()
            .eq(MdmRecordPO::getProjectId, projectId)
            .eq(entityId != null, MdmRecordPO::getEntityId, entityId);
    if (StringUtils.hasText(keyword)) {
      String trimmed = keyword.trim();
      List<String> codes = attributeCodes == null ? List.of() : attributeCodes;
      wrapper.and(
          w -> {
            w.like(MdmRecordPO::getMasterId, trimmed);
            for (String code : codes) {
              // code 已由服务层做安全标识符白名单;关键词走参数绑定,不拼接。
              w.or()
                  .apply(
                      "JSON_UNQUOTE(JSON_EXTRACT(attributes, {0})) LIKE CONCAT('%', {1}, '%')",
                      "$." + code,
                      trimmed);
            }
          });
    }
    if (status != null) {
      wrapper.eq(MdmRecordPO::getStatus, status.name());
    }
    wrapper.orderByDesc(MdmRecordPO::getUpdateTime).orderByDesc(MdmRecordPO::getId);
    Page<MdmRecordPO> page = Page.of(Math.max(1, pageNo), Math.max(1, pageSize));
    var result = mapper.selectPage(page, wrapper);
    return new PageData<>(
        result.getRecords().stream().map(MdmRecordRepositoryAdapter::toDomain).toList(),
        result.getTotal(),
        result.getPages(),
        (long) pageNo,
        (long) pageSize);
  }

  @Override
  public List<MdmDedupKeyRow> countDedupKeys(
      Long entityId, Long ruleId, String keyExpr, String valueCondition) {
    return mapper.countDedupKeys(requiredProjectId(), entityId, ruleId, keyExpr, valueCondition);
  }

  @Override
  public List<MdmRecord> listByDedupKey(Long entityId, String keyExpr, String key, int limit) {
    return mapper.selectByDedupKey(requiredProjectId(), entityId, keyExpr, key, limit).stream()
        .map(MdmRecordRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<MdmRecord> listActiveByIds(Long entityId, List<Long> ids) {
    Long projectId = requiredProjectId();
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    return mapper
        .selectList(
            new LambdaQueryWrapper<MdmRecordPO>()
                .eq(MdmRecordPO::getProjectId, projectId)
                .eq(MdmRecordPO::getEntityId, entityId)
                .eq(MdmRecordPO::getStatus, MdmRecordStatus.ACTIVE.name())
                .in(MdmRecordPO::getId, ids))
        .stream()
        .map(MdmRecordRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean update(MdmRecord record) {
    Long projectId = requiredProjectId();
    return mapper.update(
            null,
            new LambdaUpdateWrapper<MdmRecordPO>()
                .eq(MdmRecordPO::getId, record.id())
                .eq(MdmRecordPO::getProjectId, projectId)
                .set(MdmRecordPO::getAttributes, record.attributes())
                .set(MdmRecordPO::getSourceIds, record.sourceIds())
                .set(MdmRecordPO::getStatus, record.status().name())
                .set(MdmRecordPO::getVersion, record.version()))
        > 0;
  }

  @Override
  public List<MdmRecord> listActiveByEntity(Long entityId) {
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<MdmRecordPO>()
                .eq(MdmRecordPO::getProjectId, projectId)
                .eq(MdmRecordPO::getEntityId, entityId)
                .eq(MdmRecordPO::getStatus, MdmRecordStatus.ACTIVE.name())
                .orderByAsc(MdmRecordPO::getId)
                .last("LIMIT 10000"))
        .stream()
        .map(MdmRecordRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public Optional<MdmRecord> findByMasterId(Long entityId, String masterId) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<MdmRecordPO>()
                    .eq(MdmRecordPO::getProjectId, projectId)
                    .eq(MdmRecordPO::getEntityId, entityId)
                    .eq(MdmRecordPO::getMasterId, masterId)
                    .eq(MdmRecordPO::getStatus, MdmRecordStatus.ACTIVE.name())
                    .last("LIMIT 1")))
        .map(MdmRecordRepositoryAdapter::toDomain);
  }

  @Override
  public long countActiveByEntity(Long entityId) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
        new LambdaQueryWrapper<MdmRecordPO>()
            .eq(MdmRecordPO::getProjectId, projectId)
            .eq(MdmRecordPO::getEntityId, entityId)
            .eq(MdmRecordPO::getStatus, MdmRecordStatus.ACTIVE.name()));
  }

  @Override
  public long countByEntity(Long entityId, MdmRecordStatus status) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
        new LambdaQueryWrapper<MdmRecordPO>()
            .eq(MdmRecordPO::getProjectId, projectId)
            .eq(MdmRecordPO::getEntityId, entityId)
            .eq(MdmRecordPO::getStatus, status.name()));
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private static MdmRecord toDomain(MdmRecordPO po) {
    return new MdmRecord(
        po.getId(),
        po.getEntityId(),
        po.getMasterId(),
        po.getAttributes(),
        po.getSourceIds(),
        po.getStatus() == null ? null : MdmRecordStatus.valueOf(po.getStatus()),
        po.getVersion() == null ? 1 : po.getVersion(),
        po.getCreateTime(),
        po.getUpdateTime());
  }
}
