package io.yak.ops.business.mdm.processing;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.mdm.processing.MdmMasterSqlGenerator.AttributeSpec;
import io.yak.ops.business.mdm.processing.MdmMasterSqlGenerator.LandingSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 主数据加工 SQL 生成器单元测试(55a + R0 + R2 落地表口径):
 * 同库限定/表名/field_mapping/upsert 口径/安全标识符防线。
 */
class MdmMasterSqlGeneratorTest {

  @Test
  void masterIdDerivedFromEntityCodeAndPkValue() {
    String sql = generate();
    // MD5(CONCAT('customer', ':', `cust_id`)) —— 同一 PK 值跨来源一致(D3)
    assertTrue(sql.contains("MD5(CONCAT('customer', ':', `cust_id`))"));
  }

  @Test
  void attributesJsonUsesAttrCodesAsKeys() {
    String sql = generate();
    assertTrue(sql.contains("JSON_OBJECT('cust_id', `cust_id`, 'cust_name', `cust_name`)"));
  }

  @Test
  void sourceIdsRecordsDatasourceIdWithPkValue() {
    String sql = generate();
    // source_ids = JSON_OBJECT('9', `cust_id`) —— 记录各系统原始 ID(D4)
    assertTrue(sql.contains("JSON_OBJECT('9', `cust_id`)"));
  }

  @Test
  void upsertsIntoPlatformQualifiedRecordTable() {
    String sql = generate();
    // R2:目标表按平台库限定(执行数据源默认库可能不同),表名 yak_mdm_record(P0-1.2)
    assertTrue(sql.contains("INSERT INTO `yak_security`.`yak_mdm_record`"));
    assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
    assertTrue(sql.contains("`yak_security`.`yak_mdm_record`.version + 1"));
    assertTrue(
        sql.contains(
            "JSON_MERGE_PRESERVE(`yak_security`.`yak_mdm_record`.source_ids, VALUES(source_ids))"));
    assertTrue(!sql.contains("`mdm_record`"));
  }

  @Test
  void readsPlatformLandingTableNotRawSourceTable() {
    String sql = generate();
    // R2 核心口径:FROM 平台库落地表,不再直连业务源表
    assertTrue(sql.contains("FROM `yak_security`.`mdm_landing_customer_1`"));
    assertTrue(!sql.contains("`crm`"));
  }

  @Test
  void fieldMappingSelectsLandingColumns() {
    String sql =
        MdmMasterSqlGenerator.generate(
            1L,
            1L,
            "customer",
            List.of(
                new AttributeSpec("cust_id", true),
                new AttributeSpec("cust_name", false)),
            new LandingSpec("yak_security", "mdm_landing_customer_1", 9L),
            Map.of("cust_id", "CUST_NO", "cust_name", "NAME"));
    // 落地表按源列名建列:PK 与属性列都取 field_mapping 的源列
    assertTrue(sql.contains("MD5(CONCAT('customer', ':', `CUST_NO`))"));
    assertTrue(sql.contains("JSON_OBJECT('cust_id', `CUST_NO`, 'cust_name', `NAME`)"));
    assertTrue(sql.contains("JSON_OBJECT('9', `CUST_NO`)"));
  }

  @Test
  void partialFieldMappingFallsBackToSameName() {
    String sql =
        MdmMasterSqlGenerator.generate(
            1L,
            1L,
            "customer",
            List.of(
                new AttributeSpec("cust_id", true),
                new AttributeSpec("cust_name", false)),
            new LandingSpec("yak_security", "mdm_landing_customer_1", 9L),
            Map.of("cust_name", "NAME"));
    assertTrue(sql.contains("JSON_OBJECT('cust_id', `cust_id`, 'cust_name', `NAME`)"));
  }

  @Test
  void rejectsUnsafeMappedColumn() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            MdmMasterSqlGenerator.generate(
                1L,
                1L,
                "customer",
                List.of(new AttributeSpec("cust_id", true)),
                new LandingSpec("yak_security", "mdm_landing_customer_1", 9L),
                Map.of("cust_id", "id; DROP TABLE x--")));
  }

  @Test
  void rejectsUnsafeLandingTableOrDatabase() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            MdmMasterSqlGenerator.generate(
                1L,
                1L,
                "customer",
                List.of(new AttributeSpec("cust_id", true)),
                new LandingSpec("yak_security", "landing`; DROP TABLE x--", 9L),
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            MdmMasterSqlGenerator.generate(
                1L,
                1L,
                "customer",
                List.of(new AttributeSpec("cust_id", true)),
                new LandingSpec("db name", "mdm_landing_customer_1", 9L),
                null));
  }

  @Test
  void rejectsWithoutPkAttribute() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            MdmMasterSqlGenerator.generate(
                1L, 1L, "customer",
                List.of(new AttributeSpec("cust_name", false)),
                new LandingSpec("yak_security", "mdm_landing_customer_1", 9L),
                null));
  }

  @Test
  void rejectsIncompleteLandingBinding() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            MdmMasterSqlGenerator.generate(
                1L, 1L, "customer",
                List.of(new AttributeSpec("cust_id", true)),
                new LandingSpec("yak_security", null, 9L),
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            MdmMasterSqlGenerator.generate(
                1L, 1L, "customer",
                List.of(new AttributeSpec("cust_id", true)),
                new LandingSpec(null, "mdm_landing_customer_1", 9L),
                null));
  }

  private String generate() {
    return MdmMasterSqlGenerator.generate(
        1L,
        1L,
        "customer",
        List.of(
            new AttributeSpec("cust_id", true),
            new AttributeSpec("cust_name", false)),
        new LandingSpec("yak_security", "mdm_landing_customer_1", 9L),
        null);
  }
}
