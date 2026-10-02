package io.yak.ops.business.metadata.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 任务与触发那几条查询的形状守卫（ticket 116）。
 *
 * <p>又是扫文本，理由和 {@code CatalogPresenceStatementTest} 一样且更硬：MyBatis-Plus 的查询条件是
 * 渲染 SQL 时才拼出来的，而渲染要先有 Spring 建好的 {@code TableInfo} 缓存。单测里 {@code mock} 掉
 * mapper 之后，"这条 {@code selectOne} 到底带没带 project_id"<b>在运行时读不出来</b>——
 * 少写一个 {@code .eq(projectId)} 只会变成"A 项目能看见 B 项目的任务"，测试全绿、接口全对、
 * 直到有人在自己的目录里看见别人的表。
 *
 * <p>所以这里钉的四类事实都是"漏了不会报错、只会在现网出事"的那种：归属条件、回写只 patch
 * 两列、调度线程先恢复上下文、启动补齐<b>故意</b>不带项目过滤。最后一条正反都要钉：
 * 有人"顺手补个安全过滤"就会让重启后的别的项目的闹钟永远补不回来。
 */
class CollectRunStatementTest {

  private static final Path COLLECT_RUN_SERVICE =
      mainSource("harvest", "CollectRunService.java");
  private static final Path COLLECT_JOB_ADMIN_SERVICE =
      mainSource("harvest", "CollectJobAdminService.java");
  private static final Path SCHEDULE_HANDLER =
      mainSource("schedule", "MetadataCollectScheduleHandler.java");
  private static final Path SCHEDULE_BRIDGE =
      mainSource("schedule", "MetadataScheduleEngineBridge.java");

  @Test
  void everyTaskAndRunReadCarriesTheTrustedProject() throws IOException {
    String runs = read(COLLECT_RUN_SERVICE);
    // 调度线程的项目是从 payload 恢复进来的（见下面那条测试），两条路共用同一份过滤
    assertThat(runs)
        .contains(".eq(MdCollectJobPO::getProjectId, currentProject.requireProjectId())")
        .contains(".eq(MdCollectRunPO::getProjectId, currentProject.requireProjectId())");
    assertThat(read(COLLECT_JOB_ADMIN_SERVICE))
        .contains(".eq(MdCollectJobPO::getProjectId, currentProject.requireProjectId())");
  }

  @Test
  void readsNarrowToUndeletedRowsAndRefuseToResurrectASoftDeletedJob() throws IOException {
    assertThat(read(COLLECT_RUN_SERVICE)).contains(".eq(MdCollectJobPO::getDeleted, false)");
    assertThat(read(COLLECT_JOB_ADMIN_SERVICE)).contains(".eq(MdCollectJobPO::getDeleted, false)");
  }

  /**
   * "已有轮次在跑"这条判据全在查询条件里，运行时无从断言，只能钉文本。
   *
   * <p>少了 {@code status = RUNNING} 会把成功轮次也算成在跑，任务从此再也触发不动；
   * 少了那条时间下界则一次 JVM 崩溃留下的 RUNNING 行会让任务永久不可再跑。
   */
  @Test
  void theRunningCheckIsBothStatusBoundedAndTimeBounded() throws IOException {
    String check = between(read(COLLECT_RUN_SERVICE), "private void refuseIfRunning(", "\n  }");
    assertThat(check)
        .contains(".eq(MdCollectRunPO::getStatus, RunStatus.RUNNING.name())")
        .contains(".ge(")
        .contains("RUNNING_STALE_MINUTES")
        .contains("LIMIT 1");
  }

  @Test
  void dryRunsSkipTheRunningCheckAndRealRoundsDoNot() throws IOException {
    String execute = between(read(COLLECT_RUN_SERVICE), "private RunView execute(", "\n  }");
    assertThat(execute).contains("if (!dryRun) {").contains("refuseIfRunning(job)");
  }

  /**
   * 回写只 patch 两列。
   *
   * <p>{@code updateById(job)} 看起来更短，语义却是拿本轮开头读到的旧快照覆写整行：并发改配置时，
   * 对方刚存的 {@code cron_expression} 会被一次采集回写悄悄吃掉，而下一次按老时间跑之前没人会发现。
   */
  @Test
  void aRoundPatchesTwoColumnsInsteadOfOverwritingTheWholeJobRow() throws IOException {
    String service = read(COLLECT_RUN_SERVICE);
    String patch = between(service, "private void patchJobAfterRun(", "\n  }");
    assertThat(patch)
        .contains(".set(MdCollectJobPO::getLastRunId, summary.runId())")
        .contains(".eq(MdCollectJobPO::getId, job.getId())");
    assertThat(patch).contains("if (dryRun) {").contains("getDryRunPassed");
    assertThat(service).doesNotContain("jobMapper.updateById(");
  }

  @Test
  void theTaskAdminWritesWholeRowsButTheRunServiceNeverDoes() throws IOException {
    // 配置编辑是"读整行—改—存整行"，那里 updateById 才对；采集回写不是编辑，混用就会覆盖并发修改
    assertThat(read(COLLECT_JOB_ADMIN_SERVICE)).contains("jobMapper.updateById(");
    assertThat(read(COLLECT_RUN_SERVICE)).doesNotContain("updateById(");
  }

  /** 调度线程第一件事是恢复项目上下文；目录行的 project_id 是 NOT NULL，顺序错了就写脏数据。 */
  @Test
  void theHandlerRestoresProjectContextBeforeTouchingTheJob() throws IOException {
    String handler = read(SCHEDULE_HANDLER);
    assertThat(handler)
        .contains("long projectId = context.requiredLong(\"projectId\")")
        .contains("projectScope.call(")
        .contains("new ProjectContext(projectId, null)")
        .contains("runService.triggerScheduled(jobId)");
    assertThat(indexOf(handler, "projectScope.call("))
        .as("必须在读出 projectId 之后")
        .isGreaterThan(indexOf(handler, "requiredLong(\"projectId\")"));
  }

  /** 调度轮次没有 HTTP 头可取用户，操作人由代码给定；留 null 会让第一轮 GONE 的写事务在最深处回滚。 */
  @Test
  void theScheduledRoundNamesItsOperatorInsteadOfLookingForOne() throws IOException {
    String service = read(COLLECT_RUN_SERVICE);
    assertThat(service).contains("static final String SCHEDULE_OPERATOR = \"system\"");
    assertThat(between(service, "public TriggerOutcome triggerScheduled(", "\n  }"))
        .contains("TriggerType.SCHEDULE, false, SCHEDULE_OPERATOR");
  }

  @Test
  void onlyTheHarvestChannelIsGivenAnAlarm() throws IOException {
    String bridge = read(SCHEDULE_BRIDGE);
    assertThat(between(bridge, "static boolean schedulable(", "\n  }"))
        .contains("ProviderType.HARVESTED.name().equals(job.getProviderType())");
  }

  /**
   * 启动补齐<b>故意</b>不带 project 过滤，且只认"启用且未删"。
   *
   * <p>这条发生在任何请求之外，没有可信项目可窄化；给它加一个过滤等于让别的项目重启后
   * 永远缺闹钟，而"任务行是启用状态、引擎里没有它"这件事本身不报错。
   */
  @Test
  void theStartupRebuildScansEveryProjectOnPurpose() throws IOException {
    String bridge = read(SCHEDULE_BRIDGE);
    String query = between(bridge, "private List<MdCollectJobPO> enabledJobs(", "\n  }");
    assertThat(query)
        .contains(".eq(MdCollectJobPO::getEnabled, true)")
        .contains(".eq(MdCollectJobPO::getDeleted, false)");
    assertThat(query).doesNotContain("getProjectId");
    assertThat(bridge).doesNotContain("requireProjectId");
  }

  /** 补齐必须晚于迁移：Flyway bean 是条件装配的，用 {@code @DependsOn} 钉会在关掉持久化时把启动钉死。 */
  @Test
  void theStartupRebuildRunsAfterMigrationsWithoutADependsOn() throws IOException {
    String bridge = read(SCHEDULE_BRIDGE);
    assertThat(bridge)
        .contains("@EventListener(ApplicationReadyEvent.class)")
        .contains("@Order(40)");
    assertThat(bridge).doesNotContain("@DependsOn").doesNotContain("Flyway");
  }

  /** 写路径每次存定义（改过 cron 要生效），只有启动补齐那条路径才允许幂等短路。 */
  @Test
  void onlyTheStartupPathShortCircuitsOnAnExistingAlarm() throws IOException {
    String bridge = read(SCHEDULE_BRIDGE);
    assertThat(between(bridge, "public void register(", "\n  }")).doesNotContain("snapshot(");
    assertThat(between(bridge, "public void registerEnabledJobs(", "\n  }"))
        .contains("gateway.snapshot(name(job.getId())).isPresent()");
  }

  /** ticket 116 只登记与回写，不开新的物理删除口子。 */
  @Test
  void ticketOneSixtySixAddsNoDeletePathAnywhereOnTheJobOrRunTables() throws IOException {
    for (Path file : List.of(COLLECT_RUN_SERVICE, SCHEDULE_HANDLER, SCHEDULE_BRIDGE)) {
      String source = read(file);
      assertThat(source).as("%s 里出现了物理删除", file.getFileName()).doesNotContain("deleteById(");
    }
    // 任务侧唯一允许删除的是软删 + 撤闹钟
    String admin = read(COLLECT_JOB_ADMIN_SERVICE);
    assertThat(admin).doesNotContain("jobMapper.delete(").doesNotContain("deleteById(");
    assertThat(between(admin, "public void delete(", "\n  }"))
        .contains("existing.setDeleted(true)")
        .contains("scheduleBridge.deleteIfPresent(");
  }

  private static int indexOf(String source, String needle) {
    int index = source.indexOf(needle);
    assertThat(index).as("源码里找不到 %s", needle).isNotNegative();
    return index;
  }

  private static String between(String source, String from, String to) {
    int start = source.indexOf(from);
    assertThat(start).as("源码里找不到起点 %s", from).isNotNegative();
    int end = source.indexOf(to, start);
    assertThat(end).as("源码里找不到终点 %s", to).isNotNegative();
    return source.substring(start, end);
  }

  private static Path mainSource(String pkg, String fileName) {
    return Path.of(
        "src", "main", "java", "io", "yak", "ops", "business", "metadata", pkg, fileName);
  }

  private String read(Path relative) throws IOException {
    return Files.readString(moduleRoot().resolve(relative), StandardCharsets.UTF_8);
  }

  /** maven 与 IDE 的工作目录不同：从几个候选根依次找元数据模块根（与同包其它守卫同一口径）。 */
  private Path moduleRoot() {
    Path probe =
        Path.of(
            "src", "main", "java", "io", "yak", "ops", "business", "metadata", "dao", "mapper",
            "LineageCatalogRowMapper.java");
    List<Path> candidates =
        List.of(
            Path.of("").toAbsolutePath().normalize(),
            Path.of("..", "data-ops-business-metadata").toAbsolutePath().normalize(),
            Path.of("data-ops-business", "data-ops-business-metadata").toAbsolutePath().normalize());
    return candidates.stream()
        .filter(path -> Files.isRegularFile(path.resolve(probe)))
        .findFirst()
        .orElseThrow(() -> new AssertionError("找不到元数据模块根目录，尝试过：" + candidates));
  }
}
