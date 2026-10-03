package io.yak.ops.business.metric.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.metric.dao.model.MetricActivePublicationPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Current Metric publication pointer mapper with atomic project-scoped replacement. */
@Mapper
public interface MetricActivePublicationMapper extends BaseMapper<MetricActivePublicationPO> {

  @Insert("INSERT INTO yak_metric_active_publication "
      + "(project_id, metric_id, publication_event_id, metric_version_id, metric_version, "
      + "snapshot_digest, published_by, published_at) VALUES "
      + "(#{p.projectId}, #{p.metricId}, #{p.publicationEventId}, #{p.metricVersionId}, "
      + "#{p.metricVersion}, #{p.snapshotDigest}, #{p.publishedBy}, #{p.publishedAt}) "
      + "ON DUPLICATE KEY UPDATE publication_event_id=VALUES(publication_event_id), "
      + "metric_version_id=VALUES(metric_version_id), metric_version=VALUES(metric_version), "
      + "snapshot_digest=VALUES(snapshot_digest), published_by=VALUES(published_by), "
      + "published_at=VALUES(published_at)")
  int upsert(@Param("p") MetricActivePublicationPO pointer);

  @Delete("DELETE FROM yak_metric_active_publication "
      + "WHERE project_id=#{projectId} AND metric_id=#{metricId} "
      + "AND publication_event_id=#{publicationEventId}")
  int deleteExpected(
      @Param("projectId") Long projectId,
      @Param("metricId") Long metricId,
      @Param("publicationEventId") Long publicationEventId);
}
