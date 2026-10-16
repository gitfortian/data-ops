package io.yak.ops.business.modeling.mapping;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** V6 mapping schema must be additive and equivalent across database dialects. */
class DwdMappingMigrationContractTest {
  private static Path migration(String suffix) {
    Path local = Path.of("src", "main", "resources", "db", suffix,
        "yak-modeling", "V6__r12_dwd_mapping_draft.sql");
    if (Files.exists(local)) return local;
    return Path.of("data-ops-business", "data-ops-business-modeling", "src",
        "main", "resources", "db", suffix, "yak-modeling",
        "V6__r12_dwd_mapping_draft.sql");
  }

  @Test void bothDialectsProvideProjectScopedDraftAndImmutableRevision() throws Exception {
    String mysql = Files.readString(migration("migration"));
    String pg = Files.readString(migration("migration-postgresql"));
    for (String ddl : new String[] { mysql, pg }) {
      assertTrue(ddl.contains("CREATE TABLE yak_modeling_mapping_set"));
      assertTrue(ddl.contains("CREATE TABLE yak_modeling_mapping_revision"));
      assertTrue(ddl.contains("project_id BIGINT NOT NULL"));
      assertTrue(ddl.contains("physical_model_id BIGINT NOT NULL"));
      assertTrue(ddl.contains("logical_snapshot_sha CHAR(64) NOT NULL"));
      assertTrue(ddl.contains("physical_structure_sha CHAR(64) NOT NULL"));
      assertTrue(ddl.contains("mapping_json"));
      assertTrue(ddl.contains("frozen_json"));
      assertTrue(ddl.contains("uk_r12_mapping_physical"));
      assertTrue(ddl.contains("uk_r12_mapping_revision"));
      assertFalse(ddl.contains("DROP TABLE"));
      assertFalse(ddl.contains("DELETE FROM"));
    }
    assertTrue(mysql.contains("UNIQUE KEY uk_r12_mapping_physical"));
    assertTrue(pg.contains("CONSTRAINT uk_r12_mapping_physical UNIQUE"));
  }
}
