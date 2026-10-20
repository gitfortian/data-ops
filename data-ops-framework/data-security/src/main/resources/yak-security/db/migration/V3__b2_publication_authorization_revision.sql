-- R14-B2 T3: durable Security-owned authorization revision foundation.
-- Security is a separate datasource; this row is NOT a cross-datasource publish fence.
-- The app_name key prevents leaking revisions between Yak Security applications.
CREATE TABLE IF NOT EXISTS yak_security_publication_auth_revision (
  app_name VARCHAR(64) NOT NULL,
  scope_kind VARCHAR(16) NOT NULL,
  scope_id BIGINT NOT NULL,
  revision_no BIGINT NOT NULL,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (app_name, scope_kind, scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
