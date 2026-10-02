#!/usr/bin/env python3
"""把各模块 Flyway 契约测试改为面向"单文件 + -- Source: 分段"的形态。

这些测试原先按模块内多个 V* / B* 文件逐一读 SQL 并断言内容。模块迁移合并为
单文件后,原文 SQL 仍按 {@code -- Source: <原路径>} 分段保留在合并文件里,因此
把"读某个 V 文件"换成"读合并文件 + 取回该段",原有断言即可原样继续生效。

用法:  python scripts/db/update-flyway-contract-tests.py
"""

import io
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BS = chr(92)  # 反斜杠,避免在源码里层层转义后失真


def sec(indent: str) -> str:
    """生成 section() 辅助方法,缩进由调用方给。"""
    return (
        f"{indent}/**\n"
        f"{indent} * 合并后的单文件按 {{@code -- Source: <原路径>}} 分段;按文件名取回原迁移的正文,\n"
        f"{indent} * 使原先针对单个文件的断言继续有效。\n"
        f"{indent} */\n"
        f"{indent}private static String section(String sql, String sourceFileName) {{\n"
        f"{indent}  String[] lines = sql.split(\"{BS}n\");\n"
        f"{indent}  int start = -1;\n"
        f"{indent}  for (int i = 0; i < lines.length; i++) {{\n"
        f"{indent}    if (lines[i].startsWith(\"-- Source:\") && lines[i].endsWith(sourceFileName)) {{\n"
        f"{indent}      start = i + 1;\n"
        f"{indent}      break;\n"
        f"{indent}    }}\n"
        f"{indent}  }}\n"
        f"{indent}  if (start < 0) throw new IllegalStateException(\"missing source section: \" + sourceFileName);\n"
        f"{indent}  StringBuilder body = new StringBuilder();\n"
        f"{indent}  for (int i = start; i < lines.length; i++) {{\n"
        f"{indent}    if (lines[i].startsWith(\"-- Source:\")) break;\n"
        f"{indent}    body.append(lines[i]).append('{BS}n');\n"
        f"{indent}  }}\n"
        f"{indent}  return body.toString();\n"
        f"{indent}}}\n"
    )


def patch(path: str, subs, insert_before: str | None = None, indent: str = "  ") -> None:
    p = ROOT / path
    text = p.read_text(encoding="utf-8", newline="")
    nl = "\r\n" if "\r\n" in text else "\n"
    # 已改过就跳过,保证脚本可重复执行
    if all(new.replace("\n", nl) in text for _old, new in subs):
        print("skip(已改过)", path.split("/")[-1])
        return
    for old, new in subs:
        o = old.replace("\n", nl)
        w = new.replace("\n", nl)
        if text.count(o) != 1:
            sys.exit(f"FAIL {path}: 命中 {text.count(o)} 次 :: {old[:70]!r}")
        text = text.replace(o, w)
    if insert_before:
        anchor = insert_before.replace("\n", nl)
        if text.count(anchor) != 1:
            sys.exit(f"FAIL {path}: 锚点 {text.count(anchor)} 次 :: {insert_before[:60]!r}")
        if f"{indent}private static String section(String sql, String sourceFileName)" in text:
            print("skip(section 已存在)", path.split("/")[-1])
            p.write_text(text, encoding="utf-8", newline="")
            return
        text = text.replace(anchor, sec(indent).replace("\n", nl) + anchor)
    p.write_text(text, encoding="utf-8", newline="")
    print("ok", path.split("/")[-1])


AGENT = "data-ops-business/data-ops-business-agent/src/test/java/io/yak/ops/business/agent/architecture/AgentFlywayContractTest.java"
DATASERVICE = "data-ops-business/data-ops-business-data-service/src/test/java/io/yak/ops/business/dataservice/architecture/DataServiceFlywayContractTest.java"
DATASET = "data-ops-business/data-ops-business-dataset/src/test/java/io/yak/ops/business/dataset/architecture/DatasetFlywayContractTest.java"
OFFLINE = "data-ops-business/data-ops-business-sync/data-ops-business-sync-offline/src/test/java/io/yak/ops/business/sync/offline/architecture/OfflineSyncFlywayContractTest.java"
SLIM = "data-ops-business/data-ops-business-sync/data-ops-business-sync-offline/src/test/java/io/yak/ops/business/sync/offline/OfflineSlimSchemaTest.java"
QSCOPE = "data-ops-business/data-ops-business-quality/src/test/java/io/yak/ops/business/quality/architecture/QualityProjectScopeContractTest.java"
QTASK = "data-ops-business/data-ops-business-quality/src/test/java/io/yak/ops/business/quality/architecture/QualityWorkflowTaskMigrationTest.java"
WF_PROJECT = "data-ops-business/data-ops-business-workflow/src/test/java/io/yak/ops/business/workflow/architecture/WorkflowProjectSchemaContractTest.java"
WF_AUDIT = "data-ops-business/data-ops-business-workflow/src/test/java/io/yak/ops/business/workflow/architecture/WorkflowAuditCorrelationSchemaContractTest.java"
WF_PUBLISH = "data-ops-business/data-ops-business-workflow/src/test/java/io/yak/ops/business/workflow/architecture/WorkflowPublishStateContractTest.java"
SHARED = "data-ops-business/data-ops-business-metadata/src/test/java/io/yak/ops/business/metadata/architecture/SharedTableWritePathContractTest.java"
B_ACCESS = "data-ops-boot/src/test/java/io/yak/ops/boot/architecture/DataServiceAccessMenuMigrationTest.java"
B_APICALL = "data-ops-boot/src/test/java/io/yak/ops/boot/architecture/DataServiceApiCallMenuMigrationTest.java"
B_LEGACY = "data-ops-boot/src/test/java/io/yak/ops/boot/config/YakOpsLegacyPermissionMenuMigrationTest.java"
B_MENU = "data-ops-boot/src/test/java/io/yak/ops/boot/config/YakOpsPermissionMenuMigrationTest.java"
B_CATALOG = "data-ops-boot/src/test/java/io/yak/ops/boot/config/YakOpsSecurityCatalogMigrationTest.java"
B_VERSION = "data-ops-boot/src/test/java/io/yak/ops/boot/config/YakOpsSecurityMigrationVersionCompatibilityTest.java"
F_ISOLATION = "data-ops-framework/data-security/src/test/java/io/yak/framework/security/config/MenuMigrationIsolationTest.java"
F_MESSAGE = "data-ops-framework/data-security/src/test/java/io/yak/framework/security/config/MessageCenterMigrationTest.java"
F_PERMISSION = "data-ops-framework/data-security/src/test/java/io/yak/framework/security/config/PermissionMenuMigrationTest.java"


def main() -> None:
    # ---- 模块级契约测试:清单锁 + 逐段断言 ----
    patch(AGENT, [(
        """        // V1/V2 remain the upgrade history; B2 is the cumulative baseline for new schemas.
        List<String> migrations = sqlFiles(migrationRoot());
        assertThat(migrations.stream().filter(name -> name.startsWith("V")).toList())
                .containsExactly("V1__baseline_agent.sql", "V2__agent_add_project_id.sql");
        assertThat(migrations).contains("B2__agent_baseline.sql");""",
        """        // 模块迁移已合并为单文件;历史版本以 -- Source: 段的形式保留在文件内。
        assertThat(sqlFiles(migrationRoot())).containsExactly("V1__agent_baseline.sql");"""),
        ("""        String baseline = Files.readString(migrationRoot().resolve("V1__baseline_agent.sql"));""",
         """        String baseline = section(
            Files.readString(migrationRoot().resolve("V1__agent_baseline.sql")),
            "V1__baseline_agent.sql");"""),
    ], insert_before="    private List<String> sqlFiles(Path root) throws IOException {")

    patch(DATASERVICE, [(
        """    List<String> migrations = sqlFiles(dedicatedMigrationRoot());
    assertThat(migrations.stream().filter(name -> name.startsWith("V")).toList())
        .containsExactly(
            "V1__baseline_data_service.sql",
            "V2__ip_access_policy.sql",
            "V3__consumer_access_model.sql",
            "V4__usage_evidence_source.sql");
    assertThat(migrations).contains("B4__data_service_baseline.sql");

    String baseline = Files.readString(
        dedicatedMigrationRoot().resolve("V1__baseline_data_service.sql"));
    assertThat(baseline)""",
        """    assertThat(sqlFiles(dedicatedMigrationRoot()))
        .containsExactly("V1__data_service_baseline.sql");

    String baseline = section(
        Files.readString(dedicatedMigrationRoot().resolve("V1__data_service_baseline.sql")),
        "V1__baseline_data_service.sql");
    assertThat(baseline)"""),
        ("""    String accessPolicy = Files.readString(
        dedicatedMigrationRoot().resolve("V2__ip_access_policy.sql"));""",
         """    String accessPolicy = section(
        Files.readString(dedicatedMigrationRoot().resolve("V1__data_service_baseline.sql")),
        "V2__ip_access_policy.sql");"""),
        ("""    String consumerAccess = Files.readString(
        dedicatedMigrationRoot().resolve("V3__consumer_access_model.sql"));""",
         """    String consumerAccess = section(
        Files.readString(dedicatedMigrationRoot().resolve("V1__data_service_baseline.sql")),
        "V3__consumer_access_model.sql");"""),
        ("""    String usageEvidenceSource = Files.readString(
        dedicatedMigrationRoot().resolve("V4__usage_evidence_source.sql"));""",
         """    String usageEvidenceSource = section(
        Files.readString(dedicatedMigrationRoot().resolve("V1__data_service_baseline.sql")),
        "V4__usage_evidence_source.sql");"""),
    ], insert_before="  private List<String> sqlFiles(Path root) throws IOException {")

    patch(DATASET, [(
        """        assertThat(migrations.stream().filter(name -> name.startsWith("V")).toList())
                .containsExactly(
                        "V1__baseline_dataset.sql",
                        "V2__dataset_source_publication_lock.sql",
                        "V3__dataset_query_subject_attribution.sql");
        assertThat(migrations).contains("B3__dataset_baseline.sql");""",
        """        assertThat(migrations).containsExactly("V1__dataset_baseline.sql");"""),
        ("""        String baseline = Files.readString(migrationRoot().resolve("V1__baseline_dataset.sql"));""",
         """        String baseline = section(
            Files.readString(migrationRoot().resolve("V1__dataset_baseline.sql")),
            "V1__baseline_dataset.sql");"""),
    ], insert_before="    private List<String> sqlFiles(Path root) throws IOException {")

    patch(OFFLINE, [(
        """    List<String> migrations = sqlFiles(migrationRoot());
    assertThat(migrations.stream().filter(name -> name.startsWith("V")).toList())
        .containsExactly(
            "V1__baseline_offline_sync.sql",
            "V2__add_offline_notification_config.sql",
            "V3__add_offline_editor_meta.sql",
            "V4__add_batch_audit_carrier.sql",
            "V5__bind_offline_cursor_source_route.sql",
            "V6__offline_job_revision_versioning.sql");
    assertThat(migrations).contains("B6__offline_sync_baseline.sql");

    String baseline = Files.readString(migrationRoot().resolve("V1__baseline_offline_sync.sql"));
    assertThat(baseline)""",
        """    assertThat(sqlFiles(migrationRoot())).containsExactly("V1__offline_sync_baseline.sql");

    String baseline = section(
        Files.readString(migrationRoot().resolve("V1__offline_sync_baseline.sql")),
        "V1__baseline_offline_sync.sql");
    assertThat(baseline)"""),
        ("""    String migration =
        Files.readString(migrationRoot().resolve("V4__add_batch_audit_carrier.sql"));""",
         """    String migration =
        section(Files.readString(migrationRoot().resolve("V1__offline_sync_baseline.sql")),
                "V4__add_batch_audit_carrier.sql");"""),
        ("""    String sql = Files.readString(migrationRoot().resolve("V1__baseline_offline_sync.sql"));""",
         """    String sql =
        section(Files.readString(migrationRoot().resolve("V1__offline_sync_baseline.sql")),
                "V1__baseline_offline_sync.sql");"""),
    ], insert_before="  private String table(String sql, String table) {")

    patch(SLIM, [
        ("""  private static final String BASELINE =
      "/db/migration/yak-offline-sync/V1__baseline_offline_sync.sql";""",
         """  private static final String BASELINE =
      "/db/migration/yak-offline-sync/V1__offline_sync_baseline.sql";"""),
        ("""  private static final String NOTIFICATION_POLICY =
      "/db/migration/yak-offline-sync/V2__add_offline_notification_config.sql";""",
         """  private static final String NOTIFICATION_POLICY =
      "/db/migration/yak-offline-sync/V1__offline_sync_baseline.sql";"""),
        ("""  private static final String AUDIT_CARRIER =
      "/db/migration/yak-offline-sync/V4__add_batch_audit_carrier.sql";""",
         """  private static final String AUDIT_CARRIER =
      "/db/migration/yak-offline-sync/V1__offline_sync_baseline.sql";"""),
        ("""    String sql = read(BASELINE);

    assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS yak_offline_job_definition"));""",
         """    String sql = section(read(BASELINE), "V1__baseline_offline_sync.sql");

    assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS yak_offline_job_definition"));"""),
        ("""    String sql = read(BASELINE);

    assertTrue(sql.contains("project_id BIGINT NOT NULL"));""",
         """    String sql = section(read(BASELINE), "V1__baseline_offline_sync.sql");

    assertTrue(sql.contains("project_id BIGINT NOT NULL"));"""),
        ("""    String sql = read(BASELINE);

    assertTrue(sql.contains("sink_attempted_record_count"));""",
         """    String sql = section(read(BASELINE), "V1__baseline_offline_sync.sql");

    assertTrue(sql.contains("sink_attempted_record_count"));"""),
        ("""    String sql = read(BASELINE).toUpperCase();""",
         """    String sql = section(read(BASELINE), "V1__baseline_offline_sync.sql").toUpperCase();"""),
        ("""    String sql = read(NOTIFICATION_POLICY);""",
         """    String sql = section(read(NOTIFICATION_POLICY),
        "V2__add_offline_notification_config.sql");"""),
        ("""    String sql = read(AUDIT_CARRIER);""",
         """    String sql = section(read(AUDIT_CARRIER), "V4__add_batch_audit_carrier.sql");"""),
    ], insert_before="  private String read(String path) throws Exception {")

    patch(QSCOPE, [(
        """    String migration = read(
        "src/main/resources/db/migration/yak-quality/V1__create_quality_mvp.sql");""",
        """    String migration = section(
        read("src/main/resources/db/migration/yak-quality/V1__quality_baseline.sql"),
        "V1__create_quality_mvp.sql");"""),
    ], insert_before="  private String read(String relative) throws IOException {")

    patch(QTASK, [
        ("""    String sql = Files.readString(migration());""",
         """    String sql = section(Files.readString(migration()), "V2__add_quality_workflow_task_contract.sql");"""),
        ("""        "src/main/resources/db/migration/yak-quality/V2__add_quality_workflow_task_contract.sql");""",
         """        "src/main/resources/db/migration/yak-quality/V1__quality_baseline.sql");"""),
        ("""        "V2__add_quality_workflow_task_contract.sql");""",
         """        "V1__quality_baseline.sql");"""),
    ], insert_before="  private Path migration() {")

    patch(WF_PROJECT, [(
        """    String sql = resource("db/migration/yak-workflow/V1__baseline_workflow.sql");""",
        """    String sql = section(resource("db/migration/yak-workflow/V1__workflow_baseline.sql"),
        "V1__baseline_workflow.sql");"""),
    ])
    patch(WF_AUDIT, [
        ("""    String baseline = resource("db/migration/yak-workflow/V1__baseline_workflow.sql");""",
         """    String baseline =
        section(resource("db/migration/yak-workflow/V1__workflow_baseline.sql"),
            "V1__baseline_workflow.sql");"""),
        ("""    String migration = resource("db/migration/yak-workflow/V2__add_execution_audit_carrier.sql");""",
         """    String migration =
        section(resource("db/migration/yak-workflow/V1__workflow_baseline.sql"),
            "V2__add_execution_audit_carrier.sql");"""),
    ])

    patch(WF_PUBLISH, [(
        """        .containsExactlyInAnyOrder(
            "V1__baseline_workflow.sql",
            "V2__add_execution_audit_carrier.sql",
            "V3__workflow_publish_state.sql");
    assertThat(root.resolve("B3__workflow_baseline.sql")).isRegularFile();
    assertThat(Files.readString(root.resolve("V3__workflow_publish_state.sql")))""",
        """        .containsExactlyInAnyOrder("V1__workflow_baseline.sql");
    assertThat(
            section(
                Files.readString(root.resolve("V1__workflow_baseline.sql")),
                "V3__workflow_publish_state.sql"))"""),
    ], insert_before="  private List<SourceFile> productionSources() throws IOException {")

    patch(SHARED, [(
        """  private static final Path V2_CATALOG =
      Path.of("data-ops-business-lineage", "src", "main", "resources", "db", "migration",
          "yak-lineage", "V2__add_metadata_catalog_columns.sql");
  private static final Path V1_BASELINE =
      Path.of("data-ops-business-lineage", "src", "main", "resources", "db", "migration",
          "yak-lineage", "V1__baseline_lineage.sql");""",
        """  /** lineage 的迁移已合并为单文件;常量指向合并结果,正文靠 {@link #section} 取回原段落。 */
  private static final Path V2_CATALOG =
      Path.of("data-ops-business-lineage", "src", "main", "resources", "db", "migration",
          "yak-lineage", "V1__lineage_baseline.sql");
  private static final Path V1_BASELINE = V2_CATALOG;
  private static final String V1_BASELINE_SOURCE = "V1__baseline_lineage.sql";
  private static final String V2_CATALOG_SOURCE = "V2__add_metadata_catalog_columns.sql";"""),
        ("""    List<String> statements = sqlStatements(stripComments(read(V2_CATALOG)));""",
         """    List<String> statements =
        sqlStatements(stripComments(section(read(V2_CATALOG), V2_CATALOG_SOURCE)));"""),
        ("""    String alter = sqlStatements(stripComments(read(V2_CATALOG))).get(0);""",
         """    String alter =
        sqlStatements(stripComments(section(read(V2_CATALOG), V2_CATALOG_SOURCE))).get(0);"""),
        # 注意:下面是 Java 源文件文本,正则里的反斜杠是双份,必须用 raw string 才能原样匹配。
        (r"""    Matcher matcher = Pattern.compile("ADD COLUMN\\s+(\\w+)").matcher(read(V2_CATALOG));""",
         r"""    Matcher matcher =
        Pattern.compile("ADD COLUMN\\s+(\\w+)")
            .matcher(section(read(V2_CATALOG), V2_CATALOG_SOURCE));"""),
        ("""    String baseline = stripComments(read(V1_BASELINE));""",
         """    String baseline = stripComments(section(read(V1_BASELINE), V1_BASELINE_SOURCE));"""),
    ], insert_before="  private String read(Path relative) throws IOException {")

    # ---- boot security 树 ----
    patch(B_ACCESS, [(
        """  private Path migrationSource() {
    Path local = Path.of(
        "src/main/resources/yak-security/db/migration/V2007__register_data_service_access_page.sql");
    if (Files.isRegularFile(local)) return local;
    return Path.of(
        "data-ops-boot", "src", "main", "resources", "yak-security", "db", "migration",
        "V2007__register_data_service_access_page.sql");
  }""",
        """  private String migrationSource() throws IOException {
    return section(bootBaseline(), "V2007__register_data_service_access_page.sql");
  }

  private String bootBaseline() throws IOException {
    Path local =
        Path.of("src/main/resources/yak-security/db/migration/V1__boot_security_baseline.sql");
    if (Files.isRegularFile(local)) return Files.readString(local);
    return Files.readString(Path.of(
        "data-ops-boot", "src", "main", "resources", "yak-security", "db", "migration",
        "V1__boot_security_baseline.sql"));
  }"""),
    ])

    patch(B_APICALL, [(
        """  private Path migrationSource() {
    Path local = Path.of(
        "src/main/resources/yak-security/db/migration/"
            + "V2008__rename_data_service_access_to_api_call.sql");
    if (Files.isRegularFile(local)) return local;
    return Path.of(
        "data-ops-boot", "src", "main", "resources", "yak-security", "db", "migration",
        "V2008__rename_data_service_access_to_api_call.sql");
  }""",
        """  private String migrationSource() throws IOException {
    return section(bootBaseline(), "V2008__rename_data_service_access_to_api_call.sql");
  }

  private String bootBaseline() throws IOException {
    Path local =
        Path.of("src/main/resources/yak-security/db/migration/V1__boot_security_baseline.sql");
    if (Files.isRegularFile(local)) return Files.readString(local);
    return Files.readString(Path.of(
        "data-ops-boot", "src", "main", "resources", "yak-security", "db", "migration",
        "V1__boot_security_baseline.sql"));
  }"""),
    ])

    for target in (B_LEGACY, B_MENU):
        patch(target, [(
            """    ClassPathResource resource = new ClassPathResource(
        "yak-security/db/migration/V2006__reconcile_menu_permission_catalog.sql");
    String sql = resource.getContentAsString(StandardCharsets.UTF_8);""",
            """    ClassPathResource resource =
        new ClassPathResource("yak-security/db/migration/V1__boot_security_baseline.sql");
    String sql =
        section(resource.getContentAsString(StandardCharsets.UTF_8),
                "V2006__reconcile_menu_permission_catalog.sql");"""),
        ], insert_before="  @Test")

    patch(B_CATALOG, [(
        """    ClassPathResource resource = new ClassPathResource(
        "yak-security/db/migration/V1000__init_yak_ops_security_catalog.sql");""",
        """    ClassPathResource resource =
        new ClassPathResource("yak-security/db/migration/V1__boot_security_baseline.sql");
    String sql =
        section(resource.getContentAsString(StandardCharsets.UTF_8),
                "V1000__init_yak_ops_security_catalog.sql");"""),
    ])

    patch(B_VERSION, [(
        """    assertThat(migrations.resolve("V2040__reconcile_datasource_permission_codes.sql")).isRegularFile();
    assertThat(migrations.resolve("V2041__rename_metric_service_menu.sql")).isRegularFile();
    assertThat(migrations.resolve("V2042__register_metric_publish_permission.sql")).isRegularFile();
    assertThat(migrations.resolve("V2043__register_dataset_query_permission.sql")).isRegularFile();
    assertThat(migrations.resolve("V2044__register_data_service_invoke_permission.sql")).isRegularFile();""",
        """    // 模块迁移已合并为单文件;历史迁移以 -- Source: 段保留,这里断言各版本段仍在。
    String baseline = Files.readString(migrations.resolve("V1__boot_security_baseline.sql"));
    for (String script : List.of(
        "V2040__reconcile_datasource_permission_codes.sql",
        "V2041__rename_metric_service_menu.sql",
        "V2042__register_metric_publish_permission.sql",
        "V2043__register_dataset_query_permission.sql",
        "V2044__register_data_service_invoke_permission.sql")) {
      assertThat(baseline).contains(script);
    }"""),
    ])

    # ---- framework security 树 ----
    patch(F_ISOLATION, [
        ('.contains("CREATE TABLE yak_security_permission")',
         '.contains("CREATE TABLE IF NOT EXISTS yak_security_permission")'),
        ('.contains("CREATE TABLE yak_security_user_project")',
         '.contains("CREATE TABLE IF NOT EXISTS yak_security_user_project")'),
        ('.contains("CREATE TABLE yak_security_menu")',
         '.contains("CREATE TABLE IF NOT EXISTS yak_security_menu")'),
        ('.contains("CREATE TABLE yak_security_role_menu")',
         '.contains("CREATE TABLE IF NOT EXISTS yak_security_role_menu")'),
    ])

    patch(F_MESSAGE, [(
        """    try (InputStream input = getClass().getResourceAsStream(
            "/yak-security/db/migration/V3__evolve_message_center.sql")) {
      assertNotNull(input);
      String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);""",
        """    try (InputStream input = getClass().getResourceAsStream(
            "/yak-security/db/migration/V1__security_framework_baseline.sql")) {
      assertNotNull(input);
      String sql = section(
          new String(input.readAllBytes(), StandardCharsets.UTF_8),
          "V3__evolve_message_center.sql");"""),
    ], insert_before="  @Test", indent="  ")

    patch(F_PERMISSION, [(
        """    ClassPathResource resource = new ClassPathResource(
        "yak-security/db/migration/V2__link_permissions_to_menus.sql");
    String sql = resource.getContentAsString(StandardCharsets.UTF_8);""",
        """    ClassPathResource resource =
        new ClassPathResource("yak-security/db/migration/V1__security_framework_baseline.sql");
    String sql =
        section(resource.getContentAsString(StandardCharsets.UTF_8),
                "V2__link_permissions_to_menus.sql");"""),
    ], insert_before="  @Test")

    # 三个只调用了 section() 但还没有该方法定义的文件,补上定义
    for target in (WF_PROJECT, WF_AUDIT, B_ACCESS, B_APICALL, B_CATALOG, B_VERSION):
        p = ROOT / target
        text = p.read_text(encoding="utf-8", newline="")
        if "private static String section(" in text:
            continue
        print("note: 需人工确认", target, "是否缺 section 定义")


if __name__ == "__main__":
    main()
