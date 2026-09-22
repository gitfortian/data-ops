package io.yak.ops.business.lifecycle.monitor;

import io.yak.framework.common.PageData;
import io.yak.ops.business.lifecycle.binding.ModelTtlBindingService;
import io.yak.ops.business.lifecycle.binding.ModelTtlResolution;
import io.yak.ops.business.lifecycle.dispatch.TtlDispatchService;
import io.yak.ops.business.lifecycle.dispatch.TtlDispatchService.DispatchOutcome;
import io.yak.ops.common.bean.po.lifecycle.LifecycleDispatchRecordPO;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.BindingSource;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.ModelState;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.TriggerType;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** TTL 监控(ticket 87):各模型生效状态 + 漂移清单 + 异常告警 + 手工重发。 */
@Service
@RequiredArgsConstructor
public class TtlMonitorService {

  public record MonitorModelView(
      Long modelId,
      String modelCode,
      String modelName,
      String layerCode,
      String state,
      String policyName,
      String bindingSource,
      Integer destroyDays,
      String lastDispatchStatus,
      LocalDateTime lastDispatchTime,
      String message) {}

  public record MonitorSummary(
      long total,
      long applied,
      long drift,
      long failed,
      long unset,
      List<MonitorAlert> alerts) {}

  public record MonitorAlert(
      Long recordId,
      Long modelId,
      String status,
      Integer attempts,
      String errorMessage,
      LocalDateTime time) {}

  private final ModelTtlBindingService bindingService;
  private final TtlDispatchService dispatchService;

  public MonitorSummary summary() {
    Map<ModelState, Long> counts = new LinkedHashMap<>();
    for (ModelState s : ModelState.values()) {
      counts.put(s, 0L);
    }
    List<ModelTtlResolution> all = bindingService.resolveAll();
    all.forEach(r -> counts.merge(r.state(), 1L, Long::sum));
    List<MonitorAlert> alerts = dispatchService.recentProblems(10).stream()
        .map(r -> new MonitorAlert(r.getId(), r.getModelId(), r.getStatus(),
            r.getAttempts(), r.getErrorMessage(), r.getCreateTime()))
        .toList();
    return new MonitorSummary(all.size(),
        counts.get(ModelState.APPLIED), counts.get(ModelState.DRIFT),
        counts.get(ModelState.FAILED), counts.get(ModelState.UNSET), alerts);
  }

  /** 监控列表:状态/分层/关键字过滤 + 内存分页(模型量级内可控)。 */
  public PageData<MonitorModelView> pageModels(int pageNo, int pageSize,
      String state, String layerCode, String keyword) {
    List<MonitorModelView> rows = bindingService.resolveAll().stream()
        .map(this::toView)
        .sorted(Comparator.comparing((MonitorModelView v) -> order(v.state()))
            .thenComparing(v -> v.modelCode() == null ? "" : v.modelCode()))
        .filter(v -> !StringUtils.hasText(state) || state.equalsIgnoreCase(v.state()))
        .filter(v -> !StringUtils.hasText(layerCode)
            || layerCode.equalsIgnoreCase(v.layerCode()))
        .filter(v -> !StringUtils.hasText(keyword)
            || contains(v.modelCode(), keyword) || contains(v.modelName(), keyword))
        .toList();
    int from = Math.max(0, (pageNo - 1) * pageSize);
    int to = Math.min(rows.size(), from + pageSize);
    List<MonitorModelView> page = from >= rows.size() ? List.of() : rows.subList(from, to);
    long pages = (rows.size() + pageSize - 1L) / pageSize;
    return new PageData<>(page, rows.size(), pages, pageNo, pageSize);
  }

  /** 单模型立即重发(监控页"重试"按钮;跳过确认令牌,用于已预览过的补发)。 */
  public DispatchOutcome redispatch(Long modelId, String operator) {
    return dispatchService.dispatchBatch(List.of(modelId), operator).stream()
        .findFirst().orElseThrow();
  }

  public PageData<LifecycleDispatchRecordPO> pageRecords(int pageNo, int pageSize,
      String status, Long modelId) {
    return dispatchService.pageRecords(pageNo, pageSize, status, modelId);
  }

  public DispatchOutcome retryRecord(Long recordId, String operator) {
    return dispatchService.retry(recordId, operator, TriggerType.RETRY);
  }

  private MonitorModelView toView(ModelTtlResolution r) {
    LifecycleDispatchRecordPO last = r.lastDispatch();
    String message = r.policy() == null ? r.notPreviewableReason()
        : switch (r.state()) {
          case UNSET -> r.notPreviewableReason();
          case APPLIED -> "生效中" + retention(r);
          case DRIFT -> r.bindingSource() == BindingSource.NONE || r.policy() == null
              ? "待下发" : last == null
              ? "TTL 尚未下发到存储,请预览并下发" : "策略与存储端不一致,需重新下发";
          case FAILED -> last == null ? null : last.getErrorMessage();
        };
    return new MonitorModelView(r.model().id(), r.model().code(), r.model().name(),
        r.model().layerCode(), r.state().name(),
        r.policy() == null ? null : r.policy().getPolicyName(),
        r.bindingSource().name(),
        r.policy() == null ? null : r.policy().getDestroyDays(),
        last == null ? null : last.getStatus(),
        last == null ? null : last.getCreateTime(), message);
  }

  private static String retention(ModelTtlResolution r) {
    Integer destroy = r.policy().getDestroyDays();
    return destroy == null ? "(永久保留)" : "(保留 " + destroy + " 天)";
  }

  private static boolean contains(String value, String keyword) {
    return value != null && value.toLowerCase().contains(keyword.toLowerCase());
  }

  /** 异常态置顶:FAILED > DRIFT > UNSET > APPLIED。 */
  private static int order(String state) {
    return switch (Objects.requireNonNull(ModelState.valueOf(state))) {
      case FAILED -> 0;
      case DRIFT -> 1;
      case UNSET -> 2;
      case APPLIED -> 3;
    };
  }
}
