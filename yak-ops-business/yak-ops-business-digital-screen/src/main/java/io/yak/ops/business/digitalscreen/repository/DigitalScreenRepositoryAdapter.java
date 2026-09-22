package io.yak.ops.business.digitalscreen.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.digitalscreen.dao.mapper.DigitalScreenMapper;
import io.yak.ops.business.digitalscreen.dao.model.DigitalScreenPO;
import io.yak.ops.business.digitalscreen.domain.DigitalScreen;
import io.yak.ops.business.digitalscreen.domain.DigitalScreenStatus;
import io.yak.ops.business.digitalscreen.repository.codec.DigitalScreenBindingsCodec;
import io.yak.ops.core.project.CurrentProject;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** MyBatis-Plus adapter for project-owned Digital Screen drafts and publication pointers. */
@Repository
@DependsOn("yakDigitalScreenFlyway")
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DigitalScreenRepositoryAdapter implements DigitalScreenRepository {

  private final DigitalScreenMapper mapper;
  private final DigitalScreenBindingsCodec bindingsCodec;
  private final CurrentProject currentProject;

  @Override
  public List<DigitalScreen> list() {
    return mapper.selectList(
            Wrappers.<DigitalScreenPO>lambdaQuery()
                .eq(DigitalScreenPO::getProjectId, projectId())
                .orderByDesc(DigitalScreenPO::getUpdateTime)
                .orderByDesc(DigitalScreenPO::getId))
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public long count() {
    Long count = mapper.selectCount(
        Wrappers.<DigitalScreenPO>lambdaQuery()
            .eq(DigitalScreenPO::getProjectId, projectId()));
    return count == null ? 0L : count;
  }

  @Override
  public Optional<DigitalScreen> findById(long id) {
    return Optional.ofNullable(mapper.selectOne(
            Wrappers.<DigitalScreenPO>lambdaQuery()
                .eq(DigitalScreenPO::getProjectId, projectId())
                .eq(DigitalScreenPO::getId, id)))
        .map(this::toDomain);
  }

  @Override
  public DigitalScreen lockById(long id) {
    DigitalScreenPO row = mapper.selectOne(
        Wrappers.<DigitalScreenPO>lambdaQuery()
            .eq(DigitalScreenPO::getProjectId, projectId())
            .eq(DigitalScreenPO::getId, id)
            .last("FOR UPDATE"));
    if (row == null) throw notFound(id);
    return toDomain(row);
  }

  @Override
  public DigitalScreen insert(
      String name,
      String description,
      String templateId,
      int templateVersion,
      Map<String, Object> bindings) {
    Instant now = Instant.now();
    DigitalScreenPO row = new DigitalScreenPO();
    row.setProjectId(projectId());
    row.setName(name);
    row.setDescription(description);
    row.setTemplateId(templateId);
    row.setTemplateVersion(templateVersion);
    row.setStatus(DigitalScreenStatus.DRAFT.name());
    row.setBindingsJson(bindingsCodec.encode(bindings));
    row.setRevision(1L);
    row.setPublishedVersionNo(0);
    row.setCreateTime(Timestamp.from(now));
    row.setUpdateTime(Timestamp.from(now));
    if (mapper.insert(row) != 1 || row.getId() == null) {
      throw new IllegalStateException("创建数字化大屏失败");
    }
    return toDomain(row);
  }

  @Override
  public DigitalScreen update(
      long id,
      String name,
      String description,
      Map<String, Object> bindings) {
    int updated = mapper.update(
        null,
        Wrappers.<DigitalScreenPO>lambdaUpdate()
            .eq(DigitalScreenPO::getProjectId, projectId())
            .eq(DigitalScreenPO::getId, id)
            .set(DigitalScreenPO::getName, name)
            .set(DigitalScreenPO::getDescription, description)
            .set(DigitalScreenPO::getBindingsJson, bindingsCodec.encode(bindings))
            .set(DigitalScreenPO::getUpdateTime, Timestamp.from(Instant.now()))
            .setSql("revision = revision + 1"));
    requireUpdated(updated, id);
    return required(id);
  }

  @Override
  public DigitalScreen restoreDraft(
      long id,
      String name,
      String description,
      String templateId,
      int templateVersion,
      Map<String, Object> bindings) {
    int updated = mapper.update(
        null,
        Wrappers.<DigitalScreenPO>lambdaUpdate()
            .eq(DigitalScreenPO::getProjectId, projectId())
            .eq(DigitalScreenPO::getId, id)
            .set(DigitalScreenPO::getName, name)
            .set(DigitalScreenPO::getDescription, description)
            .set(DigitalScreenPO::getTemplateId, templateId)
            .set(DigitalScreenPO::getTemplateVersion, templateVersion)
            .set(DigitalScreenPO::getBindingsJson, bindingsCodec.encode(bindings))
            .set(DigitalScreenPO::getUpdateTime, Timestamp.from(Instant.now()))
            .setSql("revision = revision + 1"));
    requireUpdated(updated, id);
    return required(id);
  }

  @Override
  public DigitalScreen markPublished(
      long id,
      long versionId,
      int versionNo,
      long publishedRevision,
      Instant publishedTime) {
    Instant now = Instant.now();
    int updated = mapper.update(
        null,
        Wrappers.<DigitalScreenPO>lambdaUpdate()
            .eq(DigitalScreenPO::getProjectId, projectId())
            .eq(DigitalScreenPO::getId, id)
            .set(DigitalScreenPO::getStatus, DigitalScreenStatus.PUBLISHED.name())
            .set(DigitalScreenPO::getPublishedVersionId, versionId)
            .set(DigitalScreenPO::getPublishedVersionNo, versionNo)
            .set(DigitalScreenPO::getPublishedRevision, publishedRevision)
            .set(DigitalScreenPO::getPublishedTime, Timestamp.from(publishedTime))
            .set(DigitalScreenPO::getUpdateTime, Timestamp.from(now)));
    requireUpdated(updated, id);
    return required(id);
  }

  @Override
  public DigitalScreen offline(long id) {
    int updated = mapper.update(
        null,
        Wrappers.<DigitalScreenPO>lambdaUpdate()
            .eq(DigitalScreenPO::getProjectId, projectId())
            .eq(DigitalScreenPO::getId, id)
            .set(DigitalScreenPO::getStatus, DigitalScreenStatus.DRAFT.name())
            .set(DigitalScreenPO::getPublishedVersionId, null)
            .set(DigitalScreenPO::getPublishedVersionNo, 0)
            .set(DigitalScreenPO::getPublishedRevision, null)
            .set(DigitalScreenPO::getPublishedTime, null)
            .set(DigitalScreenPO::getUpdateTime, Timestamp.from(Instant.now())));
    requireUpdated(updated, id);
    return required(id);
  }

  @Override
  public boolean deleteById(long id) {
    return mapper.delete(
        Wrappers.<DigitalScreenPO>lambdaQuery()
            .eq(DigitalScreenPO::getProjectId, projectId())
            .eq(DigitalScreenPO::getId, id)) == 1;
  }

  private DigitalScreen required(long id) {
    return findById(id).orElseThrow(() -> notFound(id));
  }

  private void requireUpdated(int count, long id) {
    if (count != 1) throw notFound(id);
  }

  private IllegalArgumentException notFound(long id) {
    return new IllegalArgumentException("数字化大屏不存在或已被删除：" + id);
  }

  private DigitalScreen toDomain(DigitalScreenPO row) {
    Long publishedVersionId = row.getPublishedVersionId();
    DigitalScreenStatus status = publishedVersionId == null
        ? DigitalScreenStatus.DRAFT
        : DigitalScreenStatus.PUBLISHED;
    return new DigitalScreen(
        row.getId(),
        row.getName(),
        row.getDescription(),
        row.getTemplateId(),
        row.getTemplateVersion() == null ? 1 : row.getTemplateVersion(),
        status,
        bindingsCodec.decode(row.getBindingsJson()),
        row.getRevision() == null ? 1L : row.getRevision(),
        row.getPublishedRevision(),
        publishedVersionId,
        row.getPublishedVersionNo() == null ? 0 : row.getPublishedVersionNo(),
        instant(row.getPublishedTime()),
        instant(row.getCreateTime()),
        instant(row.getUpdateTime()));
  }

  private Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  private long projectId() {
    return currentProject.requireProjectId();
  }
}
