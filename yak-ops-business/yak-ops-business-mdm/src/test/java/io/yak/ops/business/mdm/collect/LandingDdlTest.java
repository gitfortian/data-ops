package io.yak.ops.business.mdm.collect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobDefinitionDTO;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R1 落地 DDL 预建与任务定义构造单元测试。 */
class LandingDdlTest {

  @Test
  void createTableMapsJdbcTypesToPlatformMysql() {
    String ddl =
        LandingDdl.createTable(
            "yak_security",
            "mdm_landing_customer_7",
            List.of(
                col("cust_id", Types.VARCHAR, 32, 0),
                col("age", Types.INTEGER, 10, 0),
                col("score", Types.DECIMAL, 10, 2),
                col("reg_time", Types.TIMESTAMP, null, null),
                col("avatar", Types.CLOB, null, null),
                col("huge", Types.VARCHAR, 70000, null)));
    assertEquals(
        "CREATE TABLE IF NOT EXISTS `yak_security`.`mdm_landing_customer_7` ("
            + "`cust_id` VARCHAR(32), `age` INT, `score` DECIMAL(10,2), "
            + "`reg_time` DATETIME, `avatar` TEXT, `huge` TEXT"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            + " COMMENT='MDM 采集落地表'",
        ddl);
  }

  @Test
  void rejectsUnsafeIdentifiers() {
    assertThrows(
        IllegalArgumentException.class,
        () -> LandingDdl.createTable("yak_security", "bad table", List.of(col("a", Types.INTEGER, 10, 0))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            LandingDdl.createTable(
                "yak_security", "ok_table", List.of(col("a;drop", Types.INTEGER, 10, 0))));
    assertThrows(
        IllegalArgumentException.class,
        () -> LandingDdl.createTable("yak_security", "ok_table", List.of()));
    assertFalse(LandingDdl.isSafeIdentifier("客户"));
    assertTrue(LandingDdl.isSafeIdentifier("cust_id"));
  }

  @Test
  void landingJobFactoryProducesMinimalGuideSinglePayload() {
    OfflineJobDefinitionDTO dto =
        LandingJobFactory.build(
            null,
            "mdm_collect_customer_7",
            9L,
            "crm_db.crm_customer",
            11L,
            "yak_security.mdm_landing_customer_7",
            List.of(col("cust_id", Types.VARCHAR, 32, null), col("vip", Types.BIT, null, null)));
    assertEquals("mdm_collect_customer_7", dto.getBasic().getJobName());
    assertEquals("GUIDE_SINGLE", dto.getBasic().getMode());
    assertEquals("jdbc", dto.getSource().getConnectorId());
    assertEquals("9", dto.getSource().getDataSourceId());
    assertEquals("crm_db.crm_customer", dto.getSource().getConfig().path("table").asText());
    assertEquals(2, dto.getMapping().getColumns().size());
    assertEquals("cust_id", dto.getMapping().getColumns().get(0).getSource());
    assertEquals("cust_id", dto.getMapping().getColumns().get(0).getTarget());
    assertFalse(dto.getSink().getConfig().path("autoCreateTable").asBoolean(true));
    assertEquals("overwrite", dto.getSink().getConfig().path("writeMode").asText());
  }

  @Test
  void qualifiedNameSkipsBlankSegments() {
    assertEquals("crm_db.crm_customer", LandingJobFactory.qualifiedName("crm_db", null, "crm_customer"));
    assertEquals(
        "crm.dbo.customer", LandingJobFactory.qualifiedName("crm", "dbo", "customer"));
    assertEquals("customer", LandingJobFactory.qualifiedName(null, "", "customer"));
  }

  private static CatalogColumn col(String name, int jdbcType, Integer size, Integer scale) {
    return new CatalogColumn(name, null, jdbcType, size, scale, true, 1, false, null);
  }
}
