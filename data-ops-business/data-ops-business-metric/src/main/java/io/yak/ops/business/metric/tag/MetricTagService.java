package io.yak.ops.business.metric.tag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.metric.dao.mapper.MetricTagMapper;
import io.yak.ops.business.metric.dao.mapper.MetricTagRelMapper;
import io.yak.ops.business.metric.exception.MetricException;
import io.yak.ops.business.metric.support.CodeGenerator;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.business.metric.dao.model.MetricTagPO;
import io.yak.ops.business.metric.dao.model.MetricTagRelPO;
import io.yak.ops.common.enums.metric.MetricErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 指标标签管理（T49）。 */
@Component
public class MetricTagService {

  private final MetricTagMapper tagMapper;
  private final MetricTagRelMapper tagRelMapper;
  private final CurrentProject currentProject;
  private final BusinessAuditService auditService;

  public MetricTagService(
      MetricTagMapper tagMapper,
      MetricTagRelMapper tagRelMapper,
      CurrentProject currentProject,
      BusinessAuditService auditService) {
    this.tagMapper = tagMapper;
    this.tagRelMapper = tagRelMapper;
    this.currentProject = currentProject;
    this.auditService = auditService;
  }

  // ── Tag CRUD ──

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public MetricTagPO createTag(String tagName, String operator) {
    Long projectId = currentProject.requireProjectId();
    String code = generateTagCode(tagName);
    if (tagMapper.exists(new LambdaQueryWrapper<MetricTagPO>()
        .eq(MetricTagPO::getProjectId, projectId)
        .eq(MetricTagPO::getTagCode, code))) {
      throw new MetricException(MetricErrorCode.TAG_DUPLICATE, code);
    }
    MetricTagPO po = new MetricTagPO();
    po.setProjectId(projectId);
    po.setTagCode(code);
    po.setTagName(tagName);
    po.setSortOrder(0);
    po.setStatus("ENABLED");
    po.setCreatedBy(operator);
    po.setUpdatedBy(operator);
    po.setCreateTime(LocalDateTime.now());
    po.setUpdateTime(LocalDateTime.now());
    tagMapper.insert(po);

    AuditOperationHandle audit = auditService.start(
        new AuditOperationRequest("METRIC_TAG_CREATE", "Create metric tag",
            "METRIC_TAG", String.valueOf(po.getId()), code,
            "APPLICATION", Map.of()));
    try {
      AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_CREATED,
          "Metric tag created", Map.of(), "Metric tag created");
    } catch (RuntimeException e) {
      audit.failure("METRIC_TAG_CREATE_FAILED", e);
      throw e;
    }
    return po;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void updateTag(Long tagId, String tagName, Integer sortOrder) {
    Long projectId = currentProject.requireProjectId();
    MetricTagPO existing = tagMapper.selectOne(new LambdaQueryWrapper<MetricTagPO>()
        .eq(MetricTagPO::getId, tagId)
        .eq(MetricTagPO::getProjectId, projectId));
    if (existing == null) {
      throw new MetricException(MetricErrorCode.TAG_NOT_FOUND, String.valueOf(tagId));
    }
    existing.setTagName(tagName);
    if (sortOrder != null) {
      existing.setSortOrder(sortOrder);
    }
    existing.setUpdateTime(LocalDateTime.now());
    tagMapper.updateById(existing);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void deleteTag(Long tagId) {
    Long projectId = currentProject.requireProjectId();
    MetricTagPO existing = tagMapper.selectOne(new LambdaQueryWrapper<MetricTagPO>()
        .eq(MetricTagPO::getId, tagId)
        .eq(MetricTagPO::getProjectId, projectId));
    if (existing == null) {
      throw new MetricException(MetricErrorCode.TAG_NOT_FOUND, String.valueOf(tagId));
    }
    long refCount = tagRelMapper.selectCount(new LambdaQueryWrapper<MetricTagRelPO>()
        .eq(MetricTagRelPO::getProjectId, projectId)
        .eq(MetricTagRelPO::getTagId, tagId));
    if (refCount > 0) {
      throw new MetricException(MetricErrorCode.TAG_REFERENCED,
          existing.getTagName() + " 已被 " + refCount + " 个指标使用");
    }
    tagMapper.deleteById(tagId);
  }

  public List<MetricTagPO> listTags() {
    Long projectId = currentProject.requireProjectId();
    return tagMapper.selectList(new LambdaQueryWrapper<MetricTagPO>()
        .eq(MetricTagPO::getProjectId, projectId)
        .orderByAsc(MetricTagPO::getSortOrder));
  }

  // ── Tag assignment ──

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void assignTag(Long metricId, Long tagId) {
    Long projectId = currentProject.requireProjectId();
    if (tagRelMapper.exists(new LambdaQueryWrapper<MetricTagRelPO>()
        .eq(MetricTagRelPO::getProjectId, projectId)
        .eq(MetricTagRelPO::getMetricId, metricId)
        .eq(MetricTagRelPO::getTagId, tagId))) {
      return;
    }
    MetricTagRelPO po = new MetricTagRelPO();
    po.setProjectId(projectId);
    po.setMetricId(metricId);
    po.setTagId(tagId);
    po.setCreateTime(LocalDateTime.now());
    tagRelMapper.insert(po);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void batchAssignTags(Long metricId, List<Long> tagIds) {
    if (tagIds == null) return;
    for (Long tagId : tagIds) {
      assignTag(metricId, tagId);
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void removeTag(Long metricId, Long tagId) {
    Long projectId = currentProject.requireProjectId();
    tagRelMapper.delete(new LambdaQueryWrapper<MetricTagRelPO>()
        .eq(MetricTagRelPO::getProjectId, projectId)
        .eq(MetricTagRelPO::getMetricId, metricId)
        .eq(MetricTagRelPO::getTagId, tagId));
  }

  public List<MetricTagRelPO> listTagsByMetric(Long metricId) {
    Long projectId = currentProject.requireProjectId();
    return tagRelMapper.selectList(new LambdaQueryWrapper<MetricTagRelPO>()
        .eq(MetricTagRelPO::getProjectId, projectId)
        .eq(MetricTagRelPO::getMetricId, metricId));
  }

  public List<MetricTagRelPO> listMetricsByTag(Long tagId) {
    Long projectId = currentProject.requireProjectId();
    return tagRelMapper.selectList(new LambdaQueryWrapper<MetricTagRelPO>()
        .eq(MetricTagRelPO::getProjectId, projectId)
        .eq(MetricTagRelPO::getTagId, tagId));
  }

  private static String generateTagCode(String tagName) {
    if (!StringUtils.hasText(tagName)) {
      throw new MetricException(MetricErrorCode.TAG_DUPLICATE, "标签名称不能为空");
    }
    String code = CodeGenerator.fromName(tagName, "tag");
    if (code.isEmpty()) {
      throw new MetricException(MetricErrorCode.TAG_DUPLICATE, "标签名称无法生成有效编码");
    }
    return code;
  }
}
