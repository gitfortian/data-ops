package io.yak.ops.business.metadata.harvest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.metadata.dao.mapper.MdCollectJobMapper;
import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.business.metadata.schedule.MetadataScheduleEngineBridge;
import io.yak.ops.common.bean.po.metadata.MdCollectJobPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.ProviderType;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 采集/对账任务的读写入口（ticket 116）。物理采集与投影对账<b>共用这一张表和这一套接口</b>，
 * 区别只在 {@code provider_type}——不为对账再开一个入口是 ticket 122 的硬要求。
 *
 * <p>{@code PUT} 是<b>整行替换</b>（与 lifecycle 的策略接口同形）：没给的字段取默认值，不玩"缺省即保留"。
 * 前端表单本来就是载入后整份提交，而"漏一个字段会静默改掉什么"这种悬念不该留给接口。
 *
 * <h2>三条不能被"顺手改一下"改掉的规则</h2>
 * <ol>
 *   <li><b>新建恒为停用，且启用必须先有一次通过的 dry-run 预演</b>（plan §0.13）。闸门管的是<em>启用</em>
 *       这个动作：改过作用域会把 {@code dry_run_passed} 打回未通过，但已启用的任务不会被悄悄停用——
 *       静默停采比"配置改过、建议重新预演"这条提示危险得多。</li>
 *   <li><b>作用域一收窄，GONE 判据就跟着变</b>：{@code databaseName}/{@code schemaName}/
 *       {@code tablePattern} 是 ticket 115 判"谁有缺席资格"的输入，所以它们的任何改动都必须让旧预演失效。</li>
 *   <li><b>删除是软删，闹钟一起撤</b>：{@code yak_md_collect_run} 与 {@code yak_md_change} 都不动——
 *       历史是"这批目录行为什么长成这样"的唯一答案。</li>
 * </ol>
 */
@Slf4j
@Service
public class CollectJobAdminService {

  /** 默认每日 03:00（plan §11.2 第 5 条：物理采集可以慢，投影对账才需要更密）。 */
  static final String DEFAULT_CRON = "0 0 3 * * ?";

  private static final int JOB_CODE_MAX = 64;
  private static final int DEFAULT_COLLAPSE_PCT = 30;
  private static final int DEFAULT_MISSING_ROUNDS = 2;

  private final MdCollectJobMapper jobMapper;
  private final CurrentProject currentProject;
  private final MetadataScheduleEngineBridge scheduleBridge;

  public CollectJobAdminService(
      MdCollectJobMapper jobMapper,
      CurrentProject currentProject,
      MetadataScheduleEngineBridge scheduleBridge) {
    this.jobMapper = jobMapper;
    this.currentProject = currentProject;
    this.scheduleBridge = scheduleBridge;
  }

  /** 任务清单。{@code providerType}/{@code enabled} 为空即不过滤。 */
  public PageData<JobView> page(
      int pageNo, int pageSize, String providerType, Boolean enabled, String keyword) {
    LambdaQueryWrapper<MdCollectJobPO> query =
        new LambdaQueryWrapper<MdCollectJobPO>()
            .eq(MdCollectJobPO::getProjectId, currentProject.requireProjectId())
            .eq(MdCollectJobPO::getDeleted, false)
            .eq(providerType != null && !providerType.isBlank(),
                MdCollectJobPO::getProviderType, providerType)
            .eq(enabled != null, MdCollectJobPO::getEnabled, enabled)
            .orderByDesc(MdCollectJobPO::getId);
    if (keyword != null && !keyword.isBlank()) {
      String needle = keyword.trim();
      query.and(wrapper ->
          wrapper.like(MdCollectJobPO::getJobName, needle).or().like(MdCollectJobPO::getJobCode, needle));
    }
    Page<MdCollectJobPO> result = jobMapper.selectPage(new Page<>(pageNo, pageSize), query);
    return new PageData<>(
        result.getRecords().stream().map(JobView::from).toList(),
        result.getTotal(),
        result.getPages(),
        (int) result.getCurrent(),
        (int) result.getSize());
  }

  public JobView get(Long id) {
    return JobView.from(require(id));
  }

  /** 新建：{@code enabled} <b>强制为 false</b>，无论请求里写了什么（plan §0.13）。 */
  public JobView create(UpsertCommand command, String operator) {
    Long projectId = currentProject.requireProjectId();
    MdCollectJobPO po = new MdCollectJobPO();
    po.setProjectId(projectId);
    apply(po, command, true);
    po.setJobCode(resolveJobCode(projectId, po));
    po.setEnabled(false);
    po.setDryRunPassed(false);
    po.setCreatedBy(operator);
    po.setUpdatedBy(operator);
    po.setCreateTime(LocalDateTime.now());
    po.setUpdateTime(LocalDateTime.now());
    po.setDeleted(false);
    try {
      jobMapper.insert(po);
    } catch (DuplicateKeyException failure) {
      // 查重与写入之间总有并发窗口，真正的守门人是 uk (project_id, job_code)。
      throw new MetadataException(MetadataErrorCode.PERSISTENCE_CONFLICT, "job_code=" + po.getJobCode());
    }
    return JobView.from(po);
  }

  /**
   * 修改配置。作用域字段有任一变动就把预演结论打回未通过——否则"已通过预演"这句话
   * 描述的已经不是这个任务了。改完仍在启用的任务要重存闹钟，因为 {@code cron_expression} 可改。
   */
  public JobView update(Long id, UpsertCommand command, String operator) {
    MdCollectJobPO existing = require(id);
    if (command.providerType() != null
        && !command.providerType().isBlank()
        && !command.providerType().trim().toUpperCase(Locale.ROOT).equals(existing.getProviderType())) {
      // 换通道等于换"这行由谁负责"：采集任务改成对账会留下一个指向错执行体的闹钟，反之会静默丢掉源域。
      throw new MetadataException(
          MetadataErrorCode.INVALID_ARGUMENT, "provider_type 不可修改，请新建任务 job=" + existing.getJobCode());
    }
    Scope before = Scope.of(existing);
    apply(existing, command, false);
    if (!Objects.equals(existing.getJobCode(), before.jobCode())) {
      existing.setJobCode(
          ensureFreeCode(existing.getProjectId(), existing.getJobCode(), existing.getId()));
    }
    existing.setUpdatedBy(operator);
    existing.setUpdateTime(LocalDateTime.now());
    if (!before.sameAs(existing)) {
      existing.setDryRunPassed(false);
    }
    try {
      jobMapper.updateById(existing);
    } catch (DuplicateKeyException failure) {
      throw new MetadataException(
          MetadataErrorCode.PERSISTENCE_CONFLICT, "job_code=" + existing.getJobCode());
    }
    if (Boolean.TRUE.equals(existing.getEnabled())) {
      scheduleBridge.register(existing);
    }
    return JobView.from(existing);
  }

  /** 启用/停用。启用是唯一会登记闹钟的入口，且要求先有一次通过的预演。 */
  public JobView changeEnabled(Long id, boolean enabled, String operator) {
    MdCollectJobPO existing = require(id);
    if (enabled && !Boolean.TRUE.equals(existing.getDryRunPassed())) {
      throw new MetadataException(MetadataErrorCode.DRY_RUN_REQUIRED, "job=" + existing.getJobCode());
    }
    if (enabled && !scheduleBridge.available()) {
      log.warn("调度器未装配：任务 {} 启用后只能靠手工触发，重启后也不会自动补跑", existing.getJobCode());
    }
    existing.setEnabled(enabled);
    existing.setUpdatedBy(operator);
    existing.setUpdateTime(LocalDateTime.now());
    jobMapper.updateById(existing);
    if (enabled) {
      scheduleBridge.register(existing);
      // register 存的是 enabled=true 的定义，再显式 resume 一次：闹钟可能本来就存在且处于暂停态。
      scheduleBridge.resumeIfPresent(existing.getId());
    } else {
      scheduleBridge.pauseIfPresent(existing.getId());
    }
    return JobView.from(existing);
  }

  /** 软删 + 撤闹钟；运行历史与变更流水一行都不动。 */
  public void delete(Long id, String operator) {
    MdCollectJobPO existing = require(id);
    existing.setDeleted(true);
    existing.setUpdatedBy(operator);
    existing.setUpdateTime(LocalDateTime.now());
    jobMapper.updateById(existing);
    scheduleBridge.deleteIfPresent(existing.getId());
  }

  private MdCollectJobPO require(Long id) {
    MdCollectJobPO po =
        jobMapper.selectOne(
            new LambdaQueryWrapper<MdCollectJobPO>()
                .eq(MdCollectJobPO::getId, id)
                .eq(MdCollectJobPO::getProjectId, currentProject.requireProjectId())
                .eq(MdCollectJobPO::getDeleted, false));
    if (po == null) {
      throw new MetadataException(MetadataErrorCode.COLLECT_JOB_NOT_FOUND, "id=" + id);
    }
    return po;
  }

  private void apply(MdCollectJobPO po, UpsertCommand command, boolean creating) {
    ProviderType provider = parseProvider(command.providerType(), creating);
    po.setProviderType(provider.name());
    po.setJobName(required(command.jobName(), "jobName", 128));
    if (command.jobCode() != null && !command.jobCode().isBlank()) {
      // 整行替换的唯一例外：编码是身份不是内容。留空时新建自动生成、修改保留原值。
      po.setJobCode(required(command.jobCode(), "jobCode", JOB_CODE_MAX).toLowerCase(Locale.ROOT));
    }
    po.setCronExpression(parseCron(command.cronExpression()));
    if (provider == ProviderType.HARVESTED) {
      if (command.dataSourceId() == null) {
        throw new MetadataException(
            MetadataErrorCode.COLLECT_JOB_SCOPE_INVALID, "物理采集任务必须绑定数据源");
      }
      po.setTypeName(null);
      po.setDataSourceId(command.dataSourceId());
      po.setDatabaseName(trimToNull(command.databaseName(), 128));
      po.setSchemaName(trimToNull(command.schemaName(), 128));
      po.setTablePattern(trimToNull(command.tablePattern(), 255));
      // 一期默认采到列级（plan §11.1.1）：留空当"采"，不采必须是明确决定。
      po.setCollectColumns(command.collectColumns() == null ? Boolean.TRUE : command.collectColumns());
    } else {
      po.setTypeName(required(command.typeName(), "typeName", 64));
      // 对账任务的作用域就是它的实体类型；留着上一通道的物理字段，ticket 135 会照着它们再采一遍不该采的东西。
      po.setDataSourceId(null);
      po.setDatabaseName(null);
      po.setSchemaName(null);
      po.setTablePattern(null);
      po.setCollectColumns(Boolean.TRUE);
    }
    po.setCollapseThresholdPct(
        parseRange(command.collapseThresholdPct(), DEFAULT_COLLAPSE_PCT, 1, 100, "collapseThresholdPct"));
    po.setMissingRounds(
        parseRange(command.missingRounds(), DEFAULT_MISSING_ROUNDS, 1, 5, "missingRounds"));
  }

  private ProviderType parseProvider(String raw, boolean creating) {
    String value =
        raw == null || raw.isBlank() ? (creating ? ProviderType.HARVESTED.name() : "") : raw.trim();
    if (value.isEmpty()) {
      throw new MetadataException(MetadataErrorCode.INVALID_ARGUMENT, "providerType 必填");
    }
    try {
      return ProviderType.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException unknown) {
      throw new MetadataException(
          MetadataErrorCode.INVALID_ARGUMENT, "providerType 只能是 HARVESTED|REGISTERED，实际=" + value);
    }
  }

  /**
   * cron 只做形状校验（6~7 段），语义校验留给引擎：本模块不引 Quartz，而 {@code gateway.save}
   * 失败会在启用那一步抛出来——闹钟没登记上必须让人看见，不能吞成"启用成功、从此不跑"。
   */
  private static String parseCron(String raw) {
    String value = raw == null || raw.isBlank() ? DEFAULT_CRON : raw.trim();
    int fields = value.split("\\s+").length;
    if (fields < 6 || fields > 7) {
      throw new MetadataException(
          MetadataErrorCode.INVALID_ARGUMENT, "cron 需为 6~7 段（秒 分 时 日 月 周[年]），实际=" + value);
    }
    return value;
  }

  private static Integer parseRange(Integer raw, int fallback, int min, int max, String field) {
    if (raw == null) {
      return fallback;
    }
    if (raw < min || raw > max) {
      throw new MetadataException(
          MetadataErrorCode.INVALID_ARGUMENT, field + " 需在 " + min + "~" + max + " 之间，实际=" + raw);
    }
    return raw;
  }

  private static String required(String raw, String field, int max) {
    String value = trimToNull(raw, max);
    if (value == null) {
      throw new MetadataException(MetadataErrorCode.INVALID_ARGUMENT, field + " 必填");
    }
    return value;
  }

  private static String trimToNull(String raw, int max) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String value = raw.trim();
    if (value.length() > max) {
      throw new MetadataException(MetadataErrorCode.INVALID_ARGUMENT, "长度不能超过 " + max);
    }
    return value;
  }

  /**
   * 编码自动生成（plan §0.13"能默认就不留空"）：前缀按通道给出可读值，撞了就加序号。
   * 上限 99 次而不是无限循环——真撞满时让人手工指定，比造出一个看不出来源的编码好。
   */
  private String resolveJobCode(Long projectId, MdCollectJobPO po) {
    if (po.getJobCode() != null) {
      return ensureFreeCode(projectId, po.getJobCode(), po.getId());
    }
    String base =
        ProviderType.HARVESTED.name().equals(po.getProviderType())
            ? "harvest-ds" + po.getDataSourceId()
            : "reconcile-" + po.getTypeName();
    for (int suffix = 1; suffix <= 99; suffix++) {
      String candidate = suffix == 1 ? base : base + "-" + suffix;
      if (candidate.length() > JOB_CODE_MAX) {
        candidate = candidate.substring(0, JOB_CODE_MAX);
      }
      if (findByCode(projectId, candidate) == null) {
        return candidate;
      }
    }
    throw new MetadataException(
        MetadataErrorCode.PERSISTENCE_CONFLICT, "同前缀任务编码已用满，请手工指定 jobCode");
  }

  private String ensureFreeCode(Long projectId, String code, Long selfId) {
    MdCollectJobPO holder = findByCode(projectId, code);
    if (holder != null && !Objects.equals(holder.getId(), selfId)) {
      throw new MetadataException(MetadataErrorCode.PERSISTENCE_CONFLICT, "job_code=" + code);
    }
    return code;
  }

  private MdCollectJobPO findByCode(Long projectId, String code) {
    List<MdCollectJobPO> rows =
        jobMapper.selectList(
            new LambdaQueryWrapper<MdCollectJobPO>()
                .eq(MdCollectJobPO::getProjectId, projectId)
                .eq(MdCollectJobPO::getJobCode, code)
                .last("LIMIT 1"));
    return rows == null || rows.isEmpty() ? null : rows.get(0);
  }

  /**
   * 预演结论还成立吗——只有作用域变了才不成立。改名、改 cron 都不影响目录里会多出什么，
   * 把 {@code dry_run_passed} 一起打回去会让用户白跑一次预演，下次他就学会不预演了。
   */
  private record Scope(String jobCode, Long dataSourceId, String databaseName, String schemaName,
      String tablePattern, Boolean collectColumns, String typeName) {

    static Scope of(MdCollectJobPO po) {
      return new Scope(
          po.getJobCode(),
          po.getDataSourceId(),
          po.getDatabaseName(),
          po.getSchemaName(),
          po.getTablePattern(),
          po.getCollectColumns(),
          po.getTypeName());
    }

    boolean sameAs(MdCollectJobPO other) {
      return Objects.equals(dataSourceId, other.getDataSourceId())
          && Objects.equals(databaseName, other.getDatabaseName())
          && Objects.equals(schemaName, other.getSchemaName())
          && Objects.equals(tablePattern, other.getTablePattern())
          && Objects.equals(collectColumns, other.getCollectColumns())
          && Objects.equals(typeName, other.getTypeName());
    }
  }

  /** 表单到命令的映射；启用与否不在这里，走 {@code POST /collect-jobs/{id}/enabled}。 */
  public record UpsertCommand(
      String jobCode,
      String jobName,
      String providerType,
      String typeName,
      Long dataSourceId,
      String databaseName,
      String schemaName,
      String tablePattern,
      Boolean collectColumns,
      String cronExpression,
      Integer collapseThresholdPct,
      Integer missingRounds) {}

  /** 任务行的对外形状。 */
  public record JobView(
      Long id,
      String jobCode,
      String jobName,
      String providerType,
      String typeName,
      Long dataSourceId,
      String databaseName,
      String schemaName,
      String tablePattern,
      Boolean collectColumns,
      String cronExpression,
      Boolean enabled,
      Boolean dryRunPassed,
      Integer collapseThresholdPct,
      Integer missingRounds,
      Long lastRunId,
      String createdBy,
      String updatedBy,
      LocalDateTime createTime,
      LocalDateTime updateTime) {

    static JobView from(MdCollectJobPO po) {
      return new JobView(
          po.getId(),
          po.getJobCode(),
          po.getJobName(),
          po.getProviderType(),
          po.getTypeName(),
          po.getDataSourceId(),
          po.getDatabaseName(),
          po.getSchemaName(),
          po.getTablePattern(),
          po.getCollectColumns(),
          po.getCronExpression(),
          po.getEnabled(),
          po.getDryRunPassed(),
          po.getCollapseThresholdPct(),
          po.getMissingRounds(),
          po.getLastRunId(),
          po.getCreatedBy(),
          po.getUpdatedBy(),
          po.getCreateTime(),
          po.getUpdateTime());
    }
  }
}
