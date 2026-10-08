package io.yak.ops.business.metric.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.metric.dao.mapper.MetricTagMapper;
import io.yak.ops.business.metric.dao.mapper.MetricTagRelMapper;
import io.yak.ops.business.metric.dao.model.MetricTagPO;
import io.yak.ops.business.metric.dao.model.MetricTagRelPO;
import io.yak.ops.business.metric.exception.MetricException;
import io.yak.ops.common.enums.metric.MetricErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Metric tag lookup and relation access must preserve project scoping and existing behavior. */
class MetricTagServiceTest {

  private MetricTagMapper tagMapper;
  private MetricTagRelMapper tagRelMapper;
  private MetricTagService service;

  @BeforeEach
  void setUp() {
    tagMapper = mock(MetricTagMapper.class);
    tagRelMapper = mock(MetricTagRelMapper.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    when(currentProject.requireProjectId()).thenReturn(7L);
    service = new MetricTagService(tagMapper, tagRelMapper, currentProject,
        mock(BusinessAuditService.class));
  }

  @Test
  void updateAndDeleteBothRejectUnknownTags() {
    assertThatThrownBy(() -> service.updateTag(11L, "renamed", 3))
        .isInstanceOfSatisfying(MetricException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(MetricErrorCode.TAG_NOT_FOUND));
    assertThatThrownBy(() -> service.deleteTag(11L))
        .isInstanceOfSatisfying(MetricException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(MetricErrorCode.TAG_NOT_FOUND));

    verify(tagMapper, times(2)).selectOne(any());
    verify(tagMapper, never()).updateById(any(MetricTagPO.class));
    verify(tagMapper, never()).deleteById((java.io.Serializable) any());
  }

  @Test
  void updatePreservesTagIdentityAndOptionalSortOrder() {
    MetricTagPO tag = tag(11L);
    tag.setSortOrder(8);
    when(tagMapper.selectOne(any())).thenReturn(tag);

    service.updateTag(11L, "new name", null);

    assertThat(tag.getTagName()).isEqualTo("new name");
    assertThat(tag.getSortOrder()).isEqualTo(8);
    assertThat(tag.getProjectId()).isEqualTo(7L);
    verify(tagMapper).updateById(tag);
  }

  @Test
  void deleteRejectsTagsStillReferencedByMetrics() {
    when(tagMapper.selectOne(any())).thenReturn(tag(11L));
    when(tagRelMapper.selectCount(any())).thenReturn(3L);

    assertThatThrownBy(() -> service.deleteTag(11L))
        .isInstanceOfSatisfying(MetricException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(MetricErrorCode.TAG_REFERENCED));
    verify(tagMapper, never()).deleteById((java.io.Serializable) any());
  }

  @Test
  void deleteRemovesUnreferencedTag() {
    when(tagMapper.selectOne(any())).thenReturn(tag(11L));
    when(tagRelMapper.selectCount(any())).thenReturn(0L);

    service.deleteTag(11L);

    verify(tagMapper).deleteById((java.io.Serializable) 11L);
  }

  @Test
  void assignSkipsExistingMetricTagRelation() {
    when(tagRelMapper.exists(any())).thenReturn(true);

    service.assignTag(42L, 11L);

    verify(tagRelMapper, never()).insert(any(MetricTagRelPO.class));
  }

  @Test
  void assignAndRemoveKeepProjectScopedRelationIdentity() {
    when(tagRelMapper.exists(any())).thenReturn(false);

    service.assignTag(42L, 11L);
    service.removeTag(42L, 11L);

    ArgumentCaptor<MetricTagRelPO> inserted = ArgumentCaptor.forClass(MetricTagRelPO.class);
    verify(tagRelMapper).insert(inserted.capture());
    assertThat(inserted.getValue().getProjectId()).isEqualTo(7L);
    assertThat(inserted.getValue().getMetricId()).isEqualTo(42L);
    assertThat(inserted.getValue().getTagId()).isEqualTo(11L);
    verify(tagRelMapper).delete(any());
  }

  @Test
  void listTagsByMetricPreservesResultRows() {
    MetricTagRelPO relation = new MetricTagRelPO();
    relation.setProjectId(7L);
    relation.setMetricId(42L);
    relation.setTagId(11L);
    when(tagRelMapper.selectList(any())).thenReturn(List.of(relation));

    assertThat(service.listTagsByMetric(42L)).containsExactly(relation);
  }

  private static MetricTagPO tag(Long id) {
    MetricTagPO tag = new MetricTagPO();
    tag.setId(id);
    tag.setProjectId(7L);
    tag.setTagCode("core");
    tag.setTagName("core");
    return tag;
  }
}
