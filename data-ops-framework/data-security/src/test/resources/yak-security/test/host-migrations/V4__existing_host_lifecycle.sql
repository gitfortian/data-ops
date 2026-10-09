-- A8.2 compatibility fixture: a host product has already applied a higher
-- migration version in a shared Flyway history table before Security V1.
CREATE TABLE a82_host_migrated (
  id INTEGER PRIMARY KEY
);
INSERT INTO a82_host_migrated (id) VALUES (17);
