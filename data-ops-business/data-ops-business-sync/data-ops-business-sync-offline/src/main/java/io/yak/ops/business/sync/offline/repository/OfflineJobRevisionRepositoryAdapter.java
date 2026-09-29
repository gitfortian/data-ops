package io.yak.ops.business.sync.offline.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineJobRevisionMapper;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobRevisionPO;
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
  public List<OfflineJobRevisionPO> findAllByJobDefinitionId(Long jobDefinitionId) {
    return revisionMapper.selectList(
        new LambdaQueryWrapper<OfflineJobRevisionPO>()
            .eq(OfflineJobRevisionPO::getJobDefinitionId, jobDefinitionId)
            .orderByDesc(OfflineJobRevisionPO::getVersionNo));
  }

  @Override
  public Optional<OfflineJobRevisionPO> findByJobDefinitionIdAndVersionNo(
      Long jobDefinitionId, int versionNo) {
    return Optional.ofNullable(
        revisionMapper.selectOne(
            new LambdaQueryWrapper<OfflineJobRevisionPO>()
                .eq(OfflineJobRevisionPO::getJobDefinitionId, jobDefinitionId)
                .eq(OfflineJobRevisionPO::getVersionNo, versionNo)));
  }

  @Override
  public Optional<OfflineJobRevisionPO> findById(Long revisionId) {
    return Optional.ofNullable(revisionMapper.selectById(revisionId));
  }

  @Override
  public List<OfflineJobRevisionPO> findLatestByJobDefinitionIds(
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
    return latestRowIds.isEmpty() ? List.of() : revisionMapper.selectBatchIds(latestRowIds);
  }

  @Override
  public Optional<OfflineJobRevisionPO> findLatestByJobDefinitionId(Long jobDefinitionId) {
    return Optional.ofNullable(
        revisionMapper.selectOne(
            new LambdaQueryWrapper<OfflineJobRevisionPO>()
                .eq(OfflineJobRevisionPO::getJobDefinitionId, jobDefinitionId)
                .orderByDesc(OfflineJobRevisionPO::getVersionNo)
                .last("LIMIT 1")));
  }

  @Override
  public int nextVersionNo(Long jobDefinitionId) {
    return revisionMapper.nextVersionNo(jobDefinitionId);
  }

  @Override
  public OfflineJobRevisionPO insert(OfflineJobRevisionPO revision) {
    revisionMapper.insert(revision);
    return revision;
  }
}
