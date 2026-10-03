package io.yak.ops.business.sync.offline.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineJobRevisionMapper;
import io.yak.ops.business.sync.offline.dao.model.OfflineJobRevisionPO;
import io.yak.ops.business.sync.offline.domain.OfflineJobRevision;
import org.springframework.beans.BeanUtils;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for published Offline Sync revision storage. */
@Repository
@RequiredArgsConstructor
public class OfflineJobRevisionRepositoryAdapter implements OfflineJobRevisionRepository {

  private final OfflineJobRevisionMapper revisionMapper;

  @Override
  public List<OfflineJobRevision> findAllByJobDefinitionId(Long jobDefinitionId) {
    return revisionMapper.selectList(
        new LambdaQueryWrapper<OfflineJobRevisionPO>()
            .eq(OfflineJobRevisionPO::getJobDefinitionId, jobDefinitionId)
            .orderByDesc(OfflineJobRevisionPO::getVersionNo)).stream().map(this::toDomain).toList();
  }

  @Override
  public Optional<OfflineJobRevision> findByJobDefinitionIdAndVersionNo(
      Long jobDefinitionId, int versionNo) {
    return Optional.ofNullable(
        revisionMapper.selectOne(
            new LambdaQueryWrapper<OfflineJobRevisionPO>()
                .eq(OfflineJobRevisionPO::getJobDefinitionId, jobDefinitionId)
                .eq(OfflineJobRevisionPO::getVersionNo, versionNo))).map(this::toDomain);
  }

  @Override
  public Optional<OfflineJobRevision> findById(Long revisionId) {
    return Optional.ofNullable(revisionMapper.selectById(revisionId)).map(this::toDomain);
  }

  @Override
  public List<OfflineJobRevision> findLatestByJobDefinitionIds(
      Collection<Long> jobDefinitionIds) {
    if (jobDefinitionIds == null || jobDefinitionIds.isEmpty()) {
      return List.of();
    }
    List<Long> latestRowIds =
        revisionMapper
            .selectList(
                new QueryWrapper<OfflineJobRevisionPO>()
                    .select("job_definition_id", "MAX(id) AS id")
                    .in("job_definition_id", jobDefinitionIds)
                    .groupBy("job_definition_id"))
            .stream()
            .map(OfflineJobRevisionPO::getId)
            .filter(id -> id != null)
            .toList();
    return latestRowIds.isEmpty() ? List.of() : revisionMapper.selectBatchIds(latestRowIds).stream().map(this::toDomain).toList();
  }

  @Override
  public Optional<OfflineJobRevision> findLatestByJobDefinitionId(Long jobDefinitionId) {
    return Optional.ofNullable(
        revisionMapper.selectOne(
            new LambdaQueryWrapper<OfflineJobRevisionPO>()
                .eq(OfflineJobRevisionPO::getJobDefinitionId, jobDefinitionId)
                .orderByDesc(OfflineJobRevisionPO::getVersionNo)
                .last("LIMIT 1"))).map(this::toDomain);
  }

  @Override
  public int nextVersionNo(Long jobDefinitionId) {
    return revisionMapper.nextVersionNo(jobDefinitionId);
  }

  private OfflineJobRevision toDomain(OfflineJobRevisionPO row) {
    OfflineJobRevision revision = new OfflineJobRevision();
    BeanUtils.copyProperties(row, revision);
    return revision;
  }

  @Override
  public OfflineJobRevision insert(OfflineJobRevision revision) {
    OfflineJobRevisionPO row = new OfflineJobRevisionPO();
    BeanUtils.copyProperties(revision, row);
    revisionMapper.insert(row);
    revision.setId(row.getId());
    revision.setCreateTime(row.getCreateTime());
    return revision;
  }
}
