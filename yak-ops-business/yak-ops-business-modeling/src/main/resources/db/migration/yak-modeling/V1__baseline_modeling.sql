-- Modeling module baseline.
--
-- Ticket 01 establishes the self-owned Flyway boundary only:
--   location: classpath:db/migration/yak-modeling
--   history:  flyway_schema_history_modeling
-- Domain tables are introduced by their owning tickets (model CRUD: ticket 02)
-- and must never be edited after merge. See ARCHITECTURE.md for the planned
-- table ownership.
SELECT 1;
