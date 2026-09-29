-- MDM module baseline.
--
-- Ticket 50 establishes the self-owned Flyway boundary only:
--   location: classpath:db/migration/yak-mdm
--   history:  flyway_schema_history_mdm
-- Domain tables are introduced by their owning tickets (entity: 51, attribute:
-- 52, source: 53/54, record: 55, clean rule: 56/57, distribution: 58, change:
-- 60) and must never be edited after merge. See ARCHITECTURE.md for the
-- planned table ownership.
SELECT 1;
