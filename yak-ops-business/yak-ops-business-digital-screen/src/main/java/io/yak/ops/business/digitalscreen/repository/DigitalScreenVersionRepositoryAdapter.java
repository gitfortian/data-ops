package io.yak.ops.business.digitalscreen.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.digitalscreen.dao.mapper.DigitalScreenVersionMapper;
import io.yak.ops.business.digitalscreen.dao.model.DigitalScreenVersionPO;
import io.yak.ops.business.digitalscreen.domain.DigitalScreen;
import io.yak.ops.business.digitalscreen.domain.DigitalScreenVersion;
import io.yak.ops.business.digitalscreen.repository.codec.DigitalScreenBindingsCodec;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** MyBatis-Plus adapter for inherited, append-only Digital Screen published snapshots. */
@Repository
@DependsOn("yakDigitalScreenFlyway")
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DigitalScreenVersionRepositoryAdapter implements DigitalScreenVersionRepository {

  private final DigitalScreenVersionMapper mapper;
  private final DigitalScreenRepository screens;
  private final DigitalScreenBindingsCodec bindingsCodec;

  @Override
  public List<DigitalScreenVersion> list(long screenId) {
    requireOwnedScreen(screenId);
    return mapper.selectList(
            Wrappers.<DigitalScreenVersionPO>lambdaQuery()
                .eq(DigitalScreenVersionPO::getScreenId, screenId)
                .orderByDesc(DigitalScreenVersionPO::getVersionNo))
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public Optional<DigitalScreenVersion> findById(long versionId) {
    DigitalScreenVersionPO row = mapper.selectById(versionId);
    if (row == null || screens.findById(row.getScreenId()).isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(toDomain(row));
  }

  @Override
  public Optional<DigitalScreenVersion> findByVersionNo(long screenId, int versionNo) {
    requireOwnedScreen(screenId);
    return Optional.ofNullable(mapper.selectOne(
            Wrappers.<DigitalScreenVersionPO>lambdaQuery()
                .eq(DigitalScreenVersionPO::getScreenId, screenId)
                .eq(DigitalScreenVersionPO::getVersionNo, versionNo)))
        .map(this::toDomain);
  }

  @Override
  public int nextVersionNo(long screenId) {
    requireOwnedScreen(screenId);
    DigitalScreenVersionPO latest = mapper.selectOne(
        Wrappers.<DigitalScreenVersionPO>lambdaQuery()
            .eq(DigitalScreenVersionPO::getScreenId, screenId)
            .orderByDesc(DigitalScreenVersionPO::getVersionNo)
            .last("LIMIT 1"));
    return latest == null || latest.getVersionNo() == null ? 1 : latest.getVersionNo() + 1;
  }

  @Override
  public DigitalScreenVersion insert(DigitalScreen draft, int versionNo, Instant publishedTime) {
    Objects.requireNonNull(draft, "draft");
    requireOwnedScreen(draft.id());
    DigitalScreenVersionPO row = new DigitalScreenVersionPO();
    row.setScreenId(draft.id());
    row.setVersionNo(versionNo);
    row.setSourceRevision(draft.revision());
    row.setNameSnapshot(draft.name());
    row.setDescriptionSnapshot(draft.description());
    row.setTemplateIdSnapshot(draft.templateId());
    row.setTemplateVersionSnapshot(draft.templateVersion());
    row.setBindingsJson(bindingsCodec.encode(draft.bindings()));
    row.setPublishedTime(Timestamp.from(publishedTime));
    row.setCreateTime(Timestamp.from(publishedTime));
    if (mapper.insert(row) != 1 || row.getId() == null) {
      throw new IllegalStateException("创建数字化大屏发布版本失败");
    }
    return toDomain(row);
  }

  @Override
  public void deleteByScreenId(long screenId) {
    requireOwnedScreen(screenId);
    mapper.delete(Wrappers.<DigitalScreenVersionPO>lambdaQuery()
        .eq(DigitalScreenVersionPO::getScreenId, screenId));
  }

  private void requireOwnedScreen(long screenId) {
    screens.findById(screenId).orElseThrow(() -> new IllegalArgumentException(
        "数字化大屏不存在或已被删除：" + screenId));
  }

  private DigitalScreenVersion toDomain(DigitalScreenVersionPO row) {
    return new DigitalScreenVersion(
        row.getId(),
        row.getScreenId(),
        row.getVersionNo(),
        row.getSourceRevision(),
        row.getNameSnapshot(),
        row.getDescriptionSnapshot(),
        row.getTemplateIdSnapshot(),
        row.getTemplateVersionSnapshot() == null ? 1 : row.getTemplateVersionSnapshot(),
        bindingsCodec.decode(row.getBindingsJson()),
        row.getPublishedTime().toInstant(),
        row.getCreateTime().toInstant());
  }
}
