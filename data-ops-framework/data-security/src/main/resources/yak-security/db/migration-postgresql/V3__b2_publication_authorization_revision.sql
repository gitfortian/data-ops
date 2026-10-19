-- R14-B2 T3: durable Security-owned authorization revision foundation.
-- Security commits are independent of Modeling's business database commits.
CREATE TABLE IF NOT EXISTS yak_security_publication_auth_revision (
  app_name varchar(64) NOT NULL,
  scope_kind varchar(16) NOT NULL,
  scope_id bigint NOT NULL,
  revision_no bigint NOT NULL,
  update_time timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (app_name, scope_kind, scope_id)
);
