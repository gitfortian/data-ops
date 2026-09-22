package io.yak.ops.business.metadata.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 共表（B 案）的列归属守卫：lineage 与 metadata 写同一张 {@code yak_metadata_asset}，
 * 但<b>谁都不许碰对方的列</b>（plan §2.5、§10 测试 5 ③、ticket 133 验收清单）。
 *
 * <p>为什么在 CI 里读兄弟模块的源码与 SQL：目录列被 lineage 的一次 upsert 顺手覆写，
 * 数据库不会报任何错——{@code properties} 就是先例（它每写一次就整包覆写，§2.3 后果 2）。
 * 元数据把治理信息放进 {@code md_attributes} 之后，同样的覆写会让"搜索条件凭空失效"。
 * 这条不变式只能靠文本比对锁住，跑一次真库回归既慢又不会天天跑。
 */
class SharedTableWritePathContractTest {

  private static final String TABLE = "yak_metadata_asset";
  private static final Path V2_CATALOG =
      Path.of("yak-ops-business-lineage", "src", "main", "resources", "db", "migration",
          "yak-lineage", "V2__add_metadata_catalog_columns.sql");
  private static final Path V1_BASELINE =
      Path.of("yak-ops-business-lineage", "src", "main", "resources", "db", "migration",
          "yak-lineage", "V1__baseline_lineage.sql");
  private static final Path WRITE_MAPPER =
      Path.of("yak-ops-business-lineage", "src", "main", "resources", "mapper", "lineage",
          "LineageWriteMapper.xml");
  private static final Path ASSET_PO =
      Path.of("yak-ops-business-lineage", "src", "main", "java", "io", "yak", "ops", "business",
          "lineage", "dao", "model", "LineageAssetPO.java");

  /** 只有目录知道、且必须能用 NULL 表达"未纳入目录语义"的四列（后果 3）。 */
  private static final List<String> CATALOG_ONLY_NULLABLE =
      List.of("fully_qualified_name", "fqn_hash", "entity_status", "content_hash");
  private static final List<String> SLOTS =
      List.of("s_str_1", "s_str_2", "s_str_3", "s_num_1", "s_num_2", "s_bool_1", "s_date_1");

  @Test
  void lineageNeverWritesACatalogColumn() throws IOException {
    Set<String> catalogColumns = catalogColumns();
    Set<String> written = lineageWrittenColumns();
    assertThat(written)
        .as("lineage 的写入路径出现了目录列；下一次 lineage upsert 会把目录治理信息整包覆写")
        .doesNotContainAnyElementsOf(catalogColumns);
  }

  @Test
  void lineageStillWritesOnlyItsOwnBaselineColumns() throws IOException {
    assertThat(lineageWrittenColumns())
        .as("lineage 只写 V1 基线里它自己的列；新增了列就说明共管边界被改动，须回本测试评审")
        .isSubsetOf(baselineColumns());
  }

  @Test
  void lineageEntityDoesNotStartMappingCatalogColumns() throws IOException {
    // MyBatis-Plus 按驼峰转下划线自动生成 SQL，PO 多一个字段就等于多写一列。
    String source = read(ASSET_PO);
    Set<String> fields = new LinkedHashSet<>();
    Matcher matcher =
        Pattern.compile("private\\s+\\w+(?:<[^>]*>)?\\s+(\\w+)\\s*;").matcher(source);
    while (matcher.find()) {
      fields.add(matcher.group(1).replaceAll("([A-Z])", "_$1").toLowerCase());
    }
    assertThat(fields).as("解析 LineageAssetPO 字段失败").isNotEmpty();
    assertThat(fields)
        .as("LineageAssetPO 不得声明目录列，否则 MP 生成的语句会覆写它们")
        .doesNotContainAnyElementsOf(catalogColumns());
  }

  @Test
  void noStatementReadsTheSharedTableWithAStar() throws IOException {
    String mapper = read(WRITE_MAPPER);
    assertThat(mapper.toLowerCase())
        .as("SELECT * 会把 29 个新列带进 lineage 的结果映射，显式列清单是共表的读侧边界")
        .doesNotContain("select *");
  }

  @Test
  void catalogMigrationBuildsItsSlotsInOneAlter() throws IOException {
    List<String> statements = sqlStatements(stripComments(read(V2_CATALOG)));
    assertThat(statements)
        .as("加一个 STORED 生成列只能 ALGORITHM=COPY（§2.4.1），拆成多条就是多开一次锁窗口")
        .hasSize(1);
    String alter = statements.get(0);
    assertThat(SLOTS).allSatisfy(slot -> assertThat(alter).contains("ADD COLUMN " + slot + " "));
    assertThat(count(alter, "GENERATED ALWAYS")).isEqualTo(SLOTS.size());
    assertThat(alter).contains("ALGORITHM=COPY").contains("LOCK=SHARED");
  }

  @Test
  void catalogMigrationAddsNoUniqueKeyAndKeepsLegacyRowsHonest() throws IOException {
    String alter = sqlStatements(stripComments(read(V2_CATALOG))).get(0);
    // 身份只有 lineage 那把 uk；给派生值再建一把，换来的只有 1062（§2.3 后果 4 的实测）。
    assertThat(alter.toUpperCase()).doesNotContain("UNIQUE");
    assertThat(alter).contains("ADD KEY idx_yak_md_asset_fqn (fqn_hash)");
    for (String column : CATALOG_ONLY_NULLABLE) {
      assertThat(alter)
          .as("%s 必须可空：遗留行用 NULL 承载\"早于目录机制\"，不填假值（后果 3）", column)
          .containsPattern(
              Pattern.compile("ADD COLUMN\\s+" + column + "\\s+\\w+(\\(\\d+\\))?\\s+NULL"));
    }
    assertThat(count(alter.toUpperCase(), "UPDATE " + TABLE))
        .as("迁移里不得有回填语句：回填值天然相同，正是上一版 1062 的来源")
        .isZero();
  }

  @Test
  void catalogReadSideNeverTouchesProperties() throws IOException {
    // properties 是 lineage 的整包覆写列（mapper :31/:64），目录读侧一旦用它就会静默丢数据。
    Path root = Path.of("src", "main", "java").toAbsolutePath().normalize();
    try (var paths = Files.walk(root)) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        String source = Files.readString(path, StandardCharsets.UTF_8);
        if (path.getFileName().toString().endsWith("LineageCatalogRowMapper.java")) {
          continue;
        }
        assertThat(source)
            .as("%s 直接读 properties 会踩到 lineage 的覆写", path.getFileName())
            .doesNotContain("getProperties()");
      }
    }
  }

  private Set<String> catalogColumns() throws IOException {
    Set<String> names = new LinkedHashSet<>();
    Matcher matcher = Pattern.compile("ADD COLUMN\\s+(\\w+)").matcher(read(V2_CATALOG));
    while (matcher.find()) {
      names.add(matcher.group(1));
    }
    assertThat(names).as("解析 V2 失败").hasSizeGreaterThan(20);
    return names;
  }

  /** lineage 在 {@code yak_metadata_asset} 上会<b>写入</b>的列：INSERT 列清单、ON DUPLICATE 赋值、SET 赋值。 */
  private Set<String> lineageWrittenColumns() throws IOException {
    Set<String> columns = new LinkedHashSet<>();
    Matcher block =
        Pattern.compile("<(insert|update) id=\"([^\"]+)\"[^>]*>(.*?)</\\1>", Pattern.DOTALL)
            .matcher(read(WRITE_MAPPER));
    while (block.find()) {
      String statement = block.group(3);
      if (!targetsAssetTable(statement)) {
        continue;
      }
      Matcher insert =
          Pattern.compile("INSERT INTO " + TABLE + "\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE)
              .matcher(statement);
      while (insert.find()) {
        for (String part : insert.group(1).split(",")) {
          columns.add(part.trim());
        }
      }
      Matcher onDuplicate =
          Pattern.compile("ON DUPLICATE KEY UPDATE(.*)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
              .matcher(statement);
      while (onDuplicate.find()) {
        collectAssignments(onDuplicate.group(1), columns);
      }
      Matcher set =
          Pattern.compile("SET (.*?)\\s+WHERE", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
              .matcher(statement);
      while (set.find()) {
        collectAssignments(set.group(1), columns);
      }
    }
    assertThat(columns).as("解析 mapper 失败").isNotEmpty();
    return columns;
  }

  /** 语句是否落在共享表上。JOIN 到 {@code yak_metadata_relation} 取证不算，只有以该表为写入目标才算。 */
  private boolean targetsAssetTable(String statement) {
    String normalized = statement.toLowerCase().replaceAll("\\s+", " ");
    return normalized.contains("into " + TABLE) || normalized.contains("table " + TABLE)
        || normalized.contains("update " + TABLE) || normalized.contains("from " + TABLE + " ");
  }

  private void collectAssignments(String clause, Set<String> sink) {
    Matcher matcher = Pattern.compile("([\\w.]+)\\s*=").matcher(clause);
    while (matcher.find()) {
      String name = matcher.group(1);
      sink.add(name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name);
    }
  }

  private Set<String> baselineColumns() throws IOException {
    String baseline = stripComments(read(V1_BASELINE));
    int start = baseline.indexOf("CREATE TABLE IF NOT EXISTS " + TABLE);
    assertThat(start).as("V1 里找不到 " + TABLE).isNotNegative();
    String body = baseline.substring(baseline.indexOf('(', start) + 1, baseline.indexOf(") ENGINE", start));
    Set<String> columns = new LinkedHashSet<>();
    for (String line : body.split("\n")) {
      String trimmed = line.trim();
      String first = trimmed.isEmpty() ? "" : trimmed.split("[\\s(]")[0];
      // 生成列的定义跨多行，续行的 GENERATED / COMMENT 会被误读成列名；本 schema 的列名一律小写。
      if (first.equals(first.toLowerCase())
          && !first.isEmpty()
          && !trimmed.toUpperCase().startsWith("PRIMARY KEY")
          && !trimmed.toUpperCase().startsWith("UNIQUE KEY")
          && !trimmed.toUpperCase().startsWith("KEY")) {
        columns.add(first);
      }
    }
    return columns;
  }

  private List<String> sqlStatements(String sql) {
    return java.util.Arrays.stream(sql.split(";"))
        .map(String::trim)
        .filter(statement -> !statement.isEmpty())
        .toList();
  }

  private String stripComments(String sql) {
    return sql.replaceAll("(?m)^\\s*--.*$", "");
  }

  private int count(String haystack, String needle) {
    int total = 0;
    for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + needle.length())) {
      total++;
    }
    return total;
  }

  private String read(Path relative) throws IOException {
    return Files.readString(sibling(relative), StandardCharsets.UTF_8);
  }

  /** maven 与 IDE 的工作目录不同，按几个候选根依次找（与 {@code LineageAssetTypeMirrorTest} 同法）。 */
  private Path sibling(Path relative) {
    List<Path> candidates =
        List.of(
            Path.of("..").resolve(relative),
            Path.of("yak-ops-business").resolve(relative),
            Path.of("..", "..", "yak-ops-business").resolve(relative));
    return candidates.stream()
        .map(path -> path.normalize().toAbsolutePath())
        .filter(Files::isRegularFile)
        .findFirst()
        .orElseThrow(() -> new AssertionError("找不到共表改造的文件，尝试过：" + candidates));
  }
}
