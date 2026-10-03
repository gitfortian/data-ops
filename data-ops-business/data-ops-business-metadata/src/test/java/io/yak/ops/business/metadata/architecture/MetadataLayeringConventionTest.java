package io.yak.ops.business.metadata.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 元数据模块的架构守卫（plan §10 测试 1/13，§9 的 T2/T3/T6/T13/T18 各一条）。
 *
 * <p>这些规则全部走**扫源码**而不是运行时断言：本模块的坑几乎都是"编译得过、结构已腐"那一类
 * ——import 了别模块的内部包、错误码被兜成 999、属性绕过 registry 直写。
 * 只有 CI 在编译前读到源码文本才能拦住。
 */
class MetadataLayeringConventionTest {

  private static final String MODULE_PACKAGE = "io.yak.ops.business.metadata";

  /** 模块内允许的包方向（新增包必须在此显式登记，否则本测试红）。 */
  // asset 登记后正好越过 Map.of 的 10 对上限，改用 ofEntries；语义不变。
  private static final Map<String, Set<String>> ALLOWED_INTERNAL =
      Map.ofEntries(
          Map.entry("config", Set.of()),
          // Existing read-only overview assembly depends only on its persistence condition.
          Map.entry("stat", Set.of("config")),
          // ticket 116 起 controller 还读采集任务的读写服务（它们与守护的执行体同住 harvest）。
          // ticket 117 起还读 query：搜索的应用服务住在 query 自己包里，controller 只做参数拆装。
          Map.entry("controller",
              Set.of("metamodel", "exception", "harvest", "query", "detail", "governance", "api")),
          Map.entry("dao", Set.of()),
          Map.entry("exception", Set.of()),
          Map.entry("repository", Set.of("dao")),
          // api 是源域唯一允许 import 的包（plan §0.3 的"本模块 api"）：它反向什么都不读，
          // 否则"接口零依赖"当场失效——RegisterCommand 必须是个谁都敢 new 的裸 DTO。
          Map.entry("api", Set.of()),
          // 登记主通道（ticket 130）：DTO 来自 api、共表写入借 harvest 的 AssetUpsertRepository、
          // 类型与键的解释借 metamodel、属性袋只经 harvest 的 codec。没有一条向下的新暗道。
          Map.entry("register", Set.of("api", "dao", "exception", "harvest", "metamodel")),
          // controller→dto 方向上"服务接 DTO"是本仓库既成形状（asset 的 AssetAppService 同此），
          // 守卫把它显式记下来，而不是留成一条随时被人"顺手"扩到 service→controller 的暗道。
          Map.entry("metamodel", Set.of("controller", "dao", "exception")),
          // 采集层读元模型（类型/键/指纹）、写共表行走本模块 dao、失败抛本模块异常。
          // 三个方向都是"采集在上、模型与持久化在下"，反向依赖一律不放行。
          // harvest→schedule 只有 ticket 116 这一条边：任务改了 cron 或开关，闹钟必须跟着改，
          // 而"闹钟长什么样"的判据全在桥接层那一个类里，不在这里另写一份。
          Map.entry("harvest", Set.of("metamodel", "dao", "exception", "schedule")),
          // 检索侧读元模型解释可搜性（§4.5 的"零代码分支"就靠这条边），异常走本模块的 49xxx；
          // 它不碰 dao——共表读路径经 NamedParameterJdbcTemplate 的接缝自己渲染，
          // 也不反向被 metamodel 读。
          // query→api 只有 ticket 118 这一条用途：MetadataQueryApiImpl 实现自己包的接口。
          // 方向仍是"下向上"——api 那边 Set.of() 零依赖，所以这不构成环。
          Map.entry("query", Set.of("metamodel", "exception", "api")),
          // 详情聚合（ticket 118）：一块一个来源，本包自己一条 SQL 都不写——目录行经 query、
          // 治理侧表经 governance、源域实时经 api 的 EntityProvider，类型含义问 metamodel。
          // 不给它 dao：想绕过读路径直查表，这里就是第一道闸。
          Map.entry("detail",
              Set.of("api", "exception", "governance", "metamodel", "query")),
          // 治理侧表（yak_md_change / yak_md_label）的读侧：只向下经 dao，谁都不反向读。
          Map.entry("governance", Set.of("dao")),
          // 资产供给与技术元数据分区：经 dao/config 装配，并通过本模块 api 读取目录事实。
          // 跨模块 asset.api 依赖由下面的点名放行守。
          Map.entry("asset", Set.of("config", "dao", "api")),
          // schedule→harvest 是 handler 调那一轮触发入口，schedule→dao 是启动时按业务表补齐闹钟。
          // 内存存储重启即空，业务表才是事实源，所以这条读边是它的核心职责，不是顺手查一下。
          Map.entry("schedule", Set.of("harvest", "dao")));

  /** 跨模块只允许走对方的 {@code api} 包（plan §0.3）。 */
  private static final Pattern CROSS_MODULE_IMPORT =
      Pattern.compile(
          "(?m)^import\\s+(?:static\\s+)?io\\.yak\\.ops\\.business\\.([a-z]+)\\.([A-Za-z0-9_.]+);");

  /**
   * 既有功能开关标记的精确跨模块引用。
   *
   * <p>共享数据库装配由 Boot 拥有；这里只保留现有 Datasource 功能开关标记，
   * 不允许 Metadata 导入其他领域的持久化装配或属性类。
   */
  private static final Set<String> ALLOWED_PLUMBING_IMPORTS =
      Set.of(
          "datasource.config.ConditionalOnDataSourceEnabled");

  /** Repository adapter for the datasource deletion-guard SPI. */
  private static final Set<String> ALLOWED_INTEGRATION_SPI_IMPORTS =
      Set.of("datasource.api.DataSourceReferenceProvider");

  /**
   * 物理采集端口所需的五个数据源模块类型（plan §3.1"进程内直采、不引新组件"）。
   *
   * <p>这里<b>不满足</b>"只走对方 api 包"：datasource 模块压根没有 {@code api} 包（它自己的读侧
   * 就住在 {@code query}/{@code catalog} 里），而 modeling / mdm / sync-realtime 三个模块已经在
   * 引同样这几条路径。与其为了过守卫先给邻居模块造一层 api（那是别人的模块，不在本工单授权内），
   * 不如<b>点名放行这五条</b>：{@code DataSourceCatalogHarvestSource} 是全模块唯一允许引它们的地方，
   * 端口 {@code HarvestCatalogSource} 把这条依赖压到一个类的大小——换成对方 api 包或平台 SPI 时，
   * 采集逻辑一行不用改。
   *
   * <p>刻意<b>不含</b> {@code DataSourceDefinition.connectionParams} 的任何读取路径：凭据归数据源模块，
   * 本模块只交出一个 id。
   */
  private static final Set<String> ALLOWED_CATALOG_IMPORTS =
      Set.of(
          "datasource.catalog.DataSourceCatalogReader",
          "datasource.query.DataSourceReader",
          "datasource.domain.DataSourceDefinition",
          "datasource.domain.catalog.CatalogTable",
          "datasource.domain.catalog.CatalogColumn");

  /**
   * asset 模块的供给 SPI 就住在它的 {@code api} 包里，与 plan §0.3 同一条规则（"只走对方 api 包"），
   * 点名的是包名而不是类清单：{@code AssetProvider}/{@code AssetDescriptor} 等 api 类型的增删
   * 不该每次回来改这里的守卫。modeling/metric 的 provider 引的是同一层。
   */
  private static final String ALLOWED_ASSET_API_PREFIX = "asset.api.";

  /**
   * {@code information_schema} 的逐文件豁免，每个名字都要有自己的理由（下一条测试的 ②）。
   *
   * <ul>
   *   <li>{@code MysqlMetadataStatsProvider}：SPI 目录接口只有 5+9 个字段，行数/最后 DDL 时间/是否分区
   *       这三样只能从系统视图取；一条语句读回<b>一个作用域</b>的全部表，且只选三个非字节量列。
   *   <li>{@code HarvestSystemDatabases}：这里出现这个字面量只是<b>要被跳过的引擎自带库名</b>，
   *       不读它任何东西。写全名是为了让"排除的是哪个库"可 grep，而不是拆成字符串片段
   *       去绕开本测试——那种遮掩正是这条守卫最怕的失败方式。
   * </ul>
   *
   * <p>第三个文件要进来，得连带写出"为什么非它不可"；只是想"顺手查一下"的不算理由。
   */
  private static final Set<String> ALLOWED_INFORMATION_SCHEMA_FILES =
      Set.of("MysqlMetadataStatsProvider.java", "HarvestSystemDatabases.java");

  private static final Pattern ADVICE_BASE_PACKAGES =
      Pattern.compile("basePackages\\s*=\\s*\\{?\\s*\"([^\"]+)\"");

  @Test
  void crossModuleImportsGoThroughApiPackagesOnly() throws IOException {
    for (SourceFile file : productionSources()) {
      Matcher matcher = CROSS_MODULE_IMPORT.matcher(file.content());
      while (matcher.find()) {
        if ("metadata".equals(matcher.group(1))) {
          continue; // 本模块自己的包：方向由 internalPackageDirection 守，不在此列。
        }
        String importedPath = matcher.group(1) + "." + matcher.group(2);
        assertThat(importedPath)
            .as("%s 只能通过对方 api 包跨模块引用，不得 import 内部包", file.relativePath())
            .satisfiesAnyOf(
                path -> assertThat(path).startsWith("lineage.api."),
                path -> assertThat(path).startsWith(ALLOWED_ASSET_API_PREFIX),
                path -> assertThat(ALLOWED_PLUMBING_IMPORTS).contains(path),
                path -> assertThat(ALLOWED_INTEGRATION_SPI_IMPORTS).contains(path),
                path -> assertThat(ALLOWED_CATALOG_IMPORTS).contains(path));
      }
    }
  }

  /** 上面那条放行是"一个类"的额度，本测试把额度钉死在这里，而不是让它悄悄长成整包的通道。 */
  @Test
  void catalogImportsStayInsideTheHarvestPort() throws IOException {
    List<String> users = new ArrayList<>();
    for (SourceFile file : productionSources()) {
      Matcher matcher = CROSS_MODULE_IMPORT.matcher(file.content());
      while (matcher.find()) {
        if (ALLOWED_CATALOG_IMPORTS.contains(matcher.group(1) + "." + matcher.group(2))) {
          users.add(file.fileName());
        }
      }
    }
    assertThat(users.stream().distinct().toList())
        .as("数据源目录内部包只允许采集端口那一个类引用")
        .containsExactly("DataSourceCatalogHarvestSource.java");
  }

  @Test
  void internalPackageDirectionFollowsDeclaredGraph() throws IOException {
    for (SourceFile file : productionSources()) {
      String sourcePackage = file.topLevelPackage();
      for (String imported : internalImports(file)) {
        assertThat(ALLOWED_INTERNAL.get(sourcePackage))
            .as("%s 所在包 %s 尚未在守卫里声明允许方向", file.relativePath(), sourcePackage)
            .isNotNull();
        assertThat(ALLOWED_INTERNAL.get(sourcePackage))
            .as("%s imports %s", file.relativePath(), imported)
            .contains(imported);
      }
    }
  }

  @Test
  void controllersNeverReturnBarePageData() throws IOException {
    // 裸 PageData 会让整个模块的 */page 接口 999，读写却正常（plan §9 T2）。
    for (SourceFile file : productionSources()) {
      if (!file.relativePath().startsWith("controller/")) {
        continue;
      }
      assertThat(file.content())
          .as("%s 必须返回 PagingData，不能吐裸 PageData", file.relativePath())
          .doesNotContain("public PageData")
          .doesNotContain("Result<PageData");
    }
  }

  @Test
  void exceptionAdviceCoversTheRealControllerPackage() throws IOException {
    // basePackages 写错 → 49xxx 被兜成 999，排查方向全错（plan §9 T3）。
    SourceFile advice =
        productionSources().stream()
            .filter(file -> file.relativePath().endsWith("MetadataExceptionHandler.java"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("缺少 MetadataExceptionHandler"));
    Matcher matcher = ADVICE_BASE_PACKAGES.matcher(advice.content());
    assertThat(matcher.find()).as("advice 必须显式声明 basePackages").isTrue();
    Path declared = Path.of(matcher.group(1).replace('.', '/'));
    assertThat(Files.isDirectory(javaSourceRoot().resolve(declared)))
        .as("advice 的 basePackages=%s 指向不存在的包", matcher.group(1))
        .isTrue();
    assertThat(normalize(declared)).isEqualTo(MODULE_PACKAGE.replace('.', '/') + "/controller");
  }

  @Test
  void assetKeyHashDerivationHasExactlyOneHome() throws IOException {
    // 派生点一旦分叉，同一实体裂成两个节点且没有任何 DDL 会报错（plan §10 测试 13 ②）。
    List<String> offenders = new ArrayList<>();
    for (SourceFile file : productionSources()) {
      String content = file.content().toLowerCase();
      if (content.contains("md5") && !file.relativePath().endsWith("MetadataKeyCodec.java")) {
        offenders.add(file.relativePath());
      }
    }
    assertThat(offenders).as("md5 派生只允许出现在 MetadataKeyCodec").isEmpty();
  }

  @Test
  void catalogAttributesAreNeverWrittenBypassingTheRegistry() throws IOException {
    // 属性不进 field_def 登记，半年后目录就成了说不清字段来源的黑洞（plan §9 T18）。
    Set<String> allowlist = Set.of("MetadataAttributeCodec.java", "LineageCatalogRowMapper.java");
    List<String> offenders = new ArrayList<>();
    for (SourceFile file : productionSources()) {
      if (file.content().contains("setMdAttributes(")
          && !allowlist.contains(file.fileName())) {
        offenders.add(file.relativePath());
      }
    }
    assertThat(offenders).as("只有属性编解码器可以写 md_attributes").isEmpty();
  }

  /**
   * 仓库侧读取的四条纪律，一条一个断言（plan §1.3、§9 T16、工单 114、工单 118）。
   *
   * <p>① {@code SHOW DATA} 全模块禁：表级字节量归 lifecycle 的每日快照，两处各采一份迟早给出两个答案。
   * <p>② {@code information_schema} 只放行两个文件，且逐个写理由——它不是"统计层随便用"的口子。
   * <p>③ 即使在放行的文件里，也<b>不得</b>读字节量列：放行的是行数/时间/分区，不是存储量。
   * <p>④ 不查 {@code yak_lc_*}：存储量走 lifecycle 自己的按表快照端点（工单 118）。在这里建一个
   *       查别人表的 mapper，等于同一份字节量两个出处，还会把本模块的读路径单一性破掉。
   */
  @Test
  void thisModuleNeverReadsWarehouseStorageOrAnotherModulesTables() throws IOException {
    List<String> showData = new ArrayList<>();
    List<String> informationSchema = new ArrayList<>();
    List<String> byteSize = new ArrayList<>();
    List<String> lifecycleTables = new ArrayList<>();
    for (SourceFile file : productionSources()) {
      String content = file.content().toUpperCase();
      if (content.contains("SHOW DATA")) {
        showData.add(file.relativePath());
      }
      if (content.contains("INFORMATION_SCHEMA")
          && !ALLOWED_INFORMATION_SCHEMA_FILES.contains(file.fileName())) {
        informationSchema.add(file.relativePath());
      }
      if (content.contains("DATA_LENGTH") || content.contains("INDEX_LENGTH")) {
        byteSize.add(file.relativePath());
      }
      if (codeOnly(file.content()).toUpperCase().contains("YAK_LC_")) {
        lifecycleTables.add(file.relativePath());
      }
    }
    assertThat(showData).as("存储量读 lifecycle 快照，本模块不拼 SHOW DATA").isEmpty();
    assertThat(informationSchema)
        .as("INFORMATION_SCHEMA 只允许下面点名的两个文件使用")
        .isEmpty();
    assertThat(byteSize).as("字节量列（DATA_LENGTH/INDEX_LENGTH）一律不读").isEmpty();
    assertThat(lifecycleTables).as("lifecycle 的表（yak_lc_*）不由本模块直接查").isEmpty();
  }

  /**
   * 剥掉注释后的源码。
   *
   * <p>只有 ④ 用它：那条规则本身写在 {@code MetadataStatsProvider} 的注释里（"本模块不建 mapper 去查
   * {@code yak_lc_*}"），扫原文的守卫会把自己写的纪律当成违规，逼人把说明删掉来变绿——那是反向激励。
   * ①②③ 保持扫原文：那三条要连"注释里提到"一起拦，因为被写进注释的通常是即将成形的打算。
   */
  private static String codeOnly(String content) {
    return content.replaceAll("(?s)/\\*.*?\\*/", "\n").replaceAll("(?m)//.*$", "");
  }

  @Test
  void errorCodesStayInTheirBandAndAreUnique() {
    List<Integer> codes =
        Arrays.stream(MetadataErrorCode.values()).map(MetadataErrorCode::getCode).toList();
    assertThat(codes).doesNotHaveDuplicates();
    assertThat(codes)
        .allSatisfy(code -> assertThat(code).isBetween(49001, 49099));
  }

  private List<String> internalImports(SourceFile file) {
    List<String> result = new ArrayList<>();
    String sourcePackage = file.topLevelPackage();
    for (String line : file.content().split("\n")) {
      String trimmed = line.trim();
      if (!trimmed.startsWith("import ")) {
        continue;
      }
      String imported = trimmed.substring("import ".length()).replace(";", "").trim();
      // Registry currently returns its owning immutable-by-convention type metadata row.
      // This exception permits that type only, never a mapper or DAO operation in detail.
      if (file.relativePath().equals("detail/EntityDetailService.java")
          && imported.equals(MODULE_PACKAGE + ".dao.model.MdTypeDefPO")) continue;
      if (file.relativePath().equals("query/CatalogQueryService.java")
          && imported.equals(MODULE_PACKAGE + ".dao.model.MdFieldDefPO")) continue;
      if (file.relativePath().equals("controller/v1/MetadataOverviewController.java")
          && Set.of(MODULE_PACKAGE + ".config.ConditionalOnMetadataPersistence",
              MODULE_PACKAGE + ".stat.MetadataOverviewService",
              MODULE_PACKAGE + ".stat.MetadataOverviewService.Overview").contains(imported)) {
        continue; // Exact infrastructure annotation/read projection corridor, not a whole package allowance.
      }
      if (!imported.startsWith(MODULE_PACKAGE + ".")) {
        continue;
      }
      String remainder = imported.substring(MODULE_PACKAGE.length() + 1);
      String targetPackage = remainder.contains(".") ? remainder.substring(0, remainder.lastIndexOf('.')) : remainder;
      // 只关心一级包方向（controller / metamodel / ...），嵌套包按一级归并。
      String target = targetPackage.split("\\.")[0];
      if (!target.equals(sourcePackage)) {
        result.add(target);
      }
    }
    return result;
  }

  private List<SourceFile> productionSources() throws IOException {
    Path root = productionRoot();
    List<SourceFile> files = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        String relative = normalize(root.relativize(path));
        files.add(
            new SourceFile(
                relative,
                path.getFileName().toString(),
                relative.substring(0, relative.indexOf('/')),
                Files.readString(path, StandardCharsets.UTF_8)));
      }
    }
    assertThat(files).as("未扫描到任何源码，守卫路径写错了").isNotEmpty();
    return files;
  }

  private Path productionRoot() {
    return javaSourceRoot().resolve(MODULE_PACKAGE.replace('.', '/'));
  }

  private Path javaSourceRoot() {
    Path local = Path.of("").toAbsolutePath().normalize();
    Path packagePath = Path.of(MODULE_PACKAGE.replace('.', '/'));
    if (Files.isDirectory(local.resolve("src/main/java").resolve(packagePath))) {
      return local.resolve("src/main/java");
    }
    Path repositoryRelative =
        Path.of("data-ops-business", "data-ops-business-metadata").toAbsolutePath().normalize();
    assertThat(Files.isDirectory(repositoryRelative.resolve("src/main/java").resolve(packagePath)))
        .as("从 %s 找不到元数据模块根目录", local)
        .isTrue();
    return repositoryRelative.resolve("src/main/java");
  }

  private String normalize(Path path) {
    return path.toString().replace(java.io.File.separatorChar, '/');
  }

  private record SourceFile(String relativePath, String fileName, String topLevelPackage, String content) {}
}
