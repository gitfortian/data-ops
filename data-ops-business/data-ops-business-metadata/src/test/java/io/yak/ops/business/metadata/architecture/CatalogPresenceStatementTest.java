package io.yak.ops.business.metadata.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 在场性两条语句的形状守卫（ticket 115，plan §3.4、工单 115 验收清单）。
 *
 * <p>为什么又是扫文本：{@code DELETE FROM yak_metadata_asset} 与"SET 里多一列"都不会让任何测试变红，
 * 数据库也不报错——它们只在生产上表现为"某天的目录空了"。共表的列归属由
 * {@code SharedTableWritePathContractTest} 守 lineage 那一侧，本类守自己这一侧：
 * 目录<b>永远只有软删</b>，且软删不许顺手碰别人的列。
 *
 * <p>{@code MetadataPresenceService} 的四道闸里，"上一轮"那一条完全写在查询条件上
 * （排除 dry-run/FAILED、按开始时刻倒序取一条），SQL 与 Java 两侧都无断言可依，只能在文本上钉住。
 */
class CatalogPresenceStatementTest {

  private static final String TABLE = "yak_metadata_asset";
  private static final Path MAPPER_XML =
      Path.of("src", "main", "resources", "mapper", "metadata", "LineageCatalogRowMapper.xml");
  private static final Path PRESENCE_SERVICE =
      Path.of("src", "main", "java", "io", "yak", "ops", "business", "metadata", "harvest",
          "MetadataPresenceService.java");
  private static final Path HARVEST_SERVICE =
      Path.of("src", "main", "java", "io", "yak", "ops", "business", "metadata", "harvest",
          "MetadataHarvestService.java");

  @Test
  void theCatalogNeverDeletesASharedTableRowPhysically() throws IOException {
    // 实体消失不代表它从未存在：治理历史、标签、人工状态都挂在这一行上（plan §2.4.5）。
    // 真要从库里抹掉，那是 ticket 119 之后一次显式的归档工单，不是采集的副作用。
    // 本模块的 mapper 只碰共表，所以整目录一条 DELETE 都不该有。
    for (Path xml : mapperXmls()) {
      String sql = stripComments(Files.readString(xml, StandardCharsets.UTF_8)).toLowerCase();
      assertThat(Pattern.compile("delete\\s+from").matcher(sql).find())
          .as("%s 里出现了物理删除", xml.getFileName())
          .isFalse();
    }
  }

  @Test
  void thePresenceScanCarriesAllFourHardBoundaries() throws IOException {
    String select = statement("selectPresenceRows");
    // 少一条就会去软删别人的实体：lineage 用同一个数字当它 DATASOURCE 行的 source_id。
    assertThat(select)
        .contains("source_type = 'metadata'")
        .contains("provider_type = 'harvested'")
        .contains("gone_at is null")
        .contains("source_id = #{sourceid}")
        .contains("project_id = #{projectid}")
        .contains("limit");
  }

  @Test
  void thePresenceScanReadsOnlyTheColumnsJudgingNeeds() throws IOException {
    String select = statement("selectPresenceRows");
    // md_attributes / properties 一旦被带回来，一轮采集就把整库治理信息搬进内存；
    // 而 properties 是 lineage 的整包覆写列，读它等于埋一个静默丢数据的坑。
    assertThat(select).doesNotContain("md_attributes").doesNotContain("properties");
    assertThat(select)
        .contains("last_collect_at")
        .contains("asset_type")
        .contains("database_name")
        .contains("schema_name")
        .contains("table_name");
  }

  @Test
  void theSoftDeleteTouchesOnlyItsOwnTwoColumns() throws IOException {
    String update = statement("markGone");
    String setClause = between(update, "set ", " where").substring("set ".length());
    assertThat(splitAssignments(setClause))
        .as("GONE 只置 gone_at 与 update_time：first_seen_at / entity_status / 标签一律留在原地")
        .containsExactly("gone_at", "update_time");
    assertThat(update.toLowerCase())
        // WHERE 重带 gone_at IS NULL：候选集是几轮判定拼出来的，重跑不该覆盖已软删行的时间戳。
        .contains("gone_at is null")
        // 也重带归属边界：id 是候选池里读出来的，但这条语句必须自成一体地只碰目录自己的行。
        .contains("source_type = 'metadata'");
  }

  @Test
  void thePreviousRoundBoundaryExcludesRoundsThatSawNothing() throws IOException {
    String source = read(PRESENCE_SERVICE);
    String method = between(source, "public LocalDateTime previousRoundStartedAt(", "\n  }");
    // dry-run 不改现场、FAILED 什么都没采到，两者都不能充当"那时它还该被看见"的证据。
    assertThat(method)
        .contains("getDryRun, false")
        .contains("RunStatus.SUCCESS.name(), RunStatus.SUSPECT.name()")
        .contains("getId, currentRunId")
        .contains("LIMIT 1");
    assertThat(method).doesNotContain("RunStatus.FAILED");
  }

  @Test
  void absenceIsAlwaysTwoRoundsDeepAndNeverAssumedFromAMissingTimestamp() throws IOException {
    String source = read(PRESENCE_SERVICE);
    String helper = between(source, "private static boolean absent(", "\n  }");
    assertThat(helper).contains("getLastCollectAt() != null").contains("isBefore(");
  }

  @Test
  void theSoftDeleteEntryPointHasExactlyOneCallerOutsideTheRepository() throws IOException {
    // 写入口散开是共表最容易失守的方式：多一处 markGone 就多一处绕过熔断判定和流水的地方。
    List<Path> users;
    try (var paths = Files.walk(sourceRoot())) {
      users =
          paths
              .filter(path -> path.toString().endsWith(".java"))
              .filter(path -> !path.getFileName().toString().equals("AssetUpsertRepository.java"))
              .filter(
                  path -> {
                    try {
                      return Files.readString(path, StandardCharsets.UTF_8).contains(".markGone(");
                    } catch (IOException exception) {
                      throw new AssertionError(exception);
                    }
                  })
              .toList();
    }
    assertThat(users).extracting(path -> path.getFileName().toString())
        // ticket 130 给了软删第二个合法调用方：撤销登记。红线没有松动——两者都只经
        // AssetUpsertRepository.markGone 这一个写入口，"多一处绕过共表边界的地方"依然是 0。
        .containsExactlyInAnyOrder(
            "MetadataPresenceService.java", "MetadataRegistrationService.java");
  }

  @Test
  void collectionItselfNeverReachesForTheDeleteButton() throws IOException {
    String source = read(HARVEST_SERVICE);
    // 采集只登记在场；缺席一律交出去判定，免得"采完顺手清一下"这种写法回到代码里。
    assertThat(source).doesNotContain("markGone").doesNotContain("DELETE");
  }

  private String statement(String id) throws IOException {
    String xml = Files.readString(moduleFile(MAPPER_XML), StandardCharsets.UTF_8);
    Matcher matcher =
        Pattern.compile("<(select|update|insert) id=\"" + id + "\"[^>]*>(.*?)</\\1>", Pattern.DOTALL)
            .matcher(xml);
    assertThat(matcher.find()).as("mapper 里找不到语句 %s", id).isTrue();
    return stripComments(matcher.group(2)).toLowerCase();
  }

  private List<Path> mapperXmls() throws IOException {
    Path root = moduleFile(Path.of("src", "main", "resources", "mapper"));
    try (var paths = Files.walk(root)) {
      List<Path> files = paths.filter(path -> path.toString().endsWith(".xml")).toList();
      assertThat(files).as("未扫到任何 mapper XML，守卫路径写错了").isNotEmpty();
      return files;
    }
  }

  private static List<String> splitAssignments(String setClause) {
    return java.util.Arrays.stream(setClause.split(","))
        .map(part -> part.trim().split("\\s+")[0])
        .filter(part -> !part.isEmpty())
        .toList();
  }

  private static String between(String source, String from, String to) {
    int start = source.indexOf(from);
    assertThat(start).as("源码里找不到起点 %s", from).isNotNegative();
    int end = source.indexOf(to, start);
    assertThat(end).as("源码里找不到终点 %s", to).isNotNegative();
    return source.substring(start, end);
  }

  private static String stripComments(String text) {
    return text.replaceAll("(?s)<!--.*?-->", "").replaceAll("(?m)^\\s*--.*$", "");
  }

  private Path sourceRoot() {
    return moduleRoot().resolve("src/main/java");
  }

  private Path moduleFile(Path relative) {
    return moduleRoot().resolve(relative);
  }

  /** maven 与 IDE 的工作目录不同：从几个候选根依次找元数据模块根（与同包其它守卫同一口径）。 */
  private Path moduleRoot() {
    List<Path> candidates =
        List.of(
            Path.of("").toAbsolutePath().normalize(),
            Path.of("..", "data-ops-business-metadata").toAbsolutePath().normalize(),
            Path.of("data-ops-business", "data-ops-business-metadata").toAbsolutePath().normalize());
    return candidates.stream()
        .filter(path -> Files.isRegularFile(path.resolve(MAPPER_XML)))
        .findFirst()
        .orElseThrow(() -> new AssertionError("找不到元数据模块根目录，尝试过：" + candidates));
  }

  private String read(Path relative) throws IOException {
    return Files.readString(moduleFile(relative), StandardCharsets.UTF_8);
  }
}
