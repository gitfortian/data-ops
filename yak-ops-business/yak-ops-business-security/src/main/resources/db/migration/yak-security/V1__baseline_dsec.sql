-- Data-security baseline (ticket 70). Establishes the module-owned Flyway history
-- table without creating business tables; keeps later migrations append-only.
SELECT 1;
