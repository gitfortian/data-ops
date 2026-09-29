package io.yak.ops.business.security.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.security.dao.mapper.AccessLogMapper;
import io.yak.ops.common.bean.po.security.DsecAccessLogPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 数据访问流水服务:留痕 + 安全视角查询与统计(聚合走 SQL,不内存统计)。 */
@Component
public class AccessLogService {

  private static final int TOP_LIMIT = 10;

  private final AccessLogMapper mapper;
  private final CurrentProject currentProject;

  public AccessLogService(AccessLogMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  public DsecAccessLogPO record(
      String actor,
      String resourceType,
      String resourceKey,
      String resourceName,
      String action,
      String levelCode,
      String decision,
      boolean masked,
      String algoCode,
      String source) {
    LocalDateTime now = LocalDateTime.now();
    DsecAccessLogPO po = new DsecAccessLogPO();
    po.setProjectId(currentProject.requireProjectId());
    po.setAccessTime(now);
    po.setActor(actor);
    po.setResourceType(resourceType);
    po.setResourceKey(resourceKey);
    po.setResourceName(resourceName);
    po.setAction(action);
    po.setLevelCode(levelCode);
    po.setDecision(decision);
    po.setMasked(masked ? 1 : 0);
    po.setAlgoCode(algoCode);
    po.setSource(source);
    po.setCreateTime(now);
    mapper.insert(po);
    return po;
  }

  public PageData<DsecAccessLogPO> page(
      int pageNo, int pageSize, String actor, String decision, String resourceKey,
      LocalDateTime start, LocalDateTime end) {
    Long projectId = currentProject.requireProjectId();
    QueryWrapper<DsecAccessLogPO> wrapper = new QueryWrapper<>();
    wrapper.eq("project_id", projectId);
    if (StringUtils.hasText(actor)) {
      wrapper.eq("actor", actor.trim());
    }
    if (StringUtils.hasText(decision)) {
      wrapper.eq("decision", decision.trim());
    }
    if (StringUtils.hasText(resourceKey)) {
      wrapper.like("resource_key", resourceKey.trim());
    }
    if (start != null) {
      wrapper.ge("access_time", start);
    }
    if (end != null) {
      wrapper.le("access_time", end);
    }
    wrapper.orderByDesc("access_time").orderByDesc("id");
    Page<DsecAccessLogPO> page = Page.of(Math.max(1, pageNo), Math.max(1, pageSize));
    var result = mapper.selectPage(page, wrapper);
    return new PageData<>(
        result.getRecords(), result.getTotal(), result.getPages(), (long) pageNo, (long) pageSize);
  }

  public long countByDecision(String decision) {
    Long c = mapper.selectCount(
        new QueryWrapper<DsecAccessLogPO>()
            .eq("project_id", currentProject.requireProjectId())
            .eq("decision", decision));
    return c == null ? 0L : c;
  }

  public long countMasked() {
    Long c = mapper.selectCount(
        new QueryWrapper<DsecAccessLogPO>()
            .eq("project_id", currentProject.requireProjectId())
            .eq("masked", 1));
    return c == null ? 0L : c;
  }

  public long countSince(LocalDateTime since) {
    Long c = mapper.selectCount(
        new QueryWrapper<DsecAccessLogPO>()
            .eq("project_id", currentProject.requireProjectId())
            .ge("access_time", since));
    return c == null ? 0L : c;
  }

  /** TOP 访问者(actor -> 次数),SQL group by 聚合。 */
  public List<Map<String, Object>> topActors() {
    return mapper.selectMaps(
        new QueryWrapper<DsecAccessLogPO>()
            .select("actor", "count(1) as cnt")
            .eq("project_id", currentProject.requireProjectId())
            .groupBy("actor")
            .orderByDesc("cnt")
            .last("limit " + TOP_LIMIT));
  }
}
