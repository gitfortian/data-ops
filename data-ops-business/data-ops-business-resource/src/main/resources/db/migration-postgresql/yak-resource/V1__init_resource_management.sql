-- PostgreSQL fresh-deployment baseline. Existing MySQL migration checksums are unchanged.

-- Mirrors the consolidated MySQL domain baseline; forward changes require migrations for both vendors.

CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE "yak_ops_resource" (
  "id" bigint NOT NULL,
  "project_id" bigint NOT NULL,
  "parent_id" bigint DEFAULT 0 NOT NULL,
  "name" citext NOT NULL CHECK (length("name") <= 255),
  "full_path" citext NOT NULL CHECK (length("full_path") <= 1024),
  "node_type" citext NOT NULL CHECK (length("node_type") <= 32),
  "storage_type" citext NOT NULL CHECK (length("storage_type") <= 32),
  "storage_path" citext NOT NULL CHECK (length("storage_path") <= 1024),
  "content_type" citext CHECK (length("content_type") <= 255),
  "suffix" citext CHECK (length("suffix") <= 64),
  "file_size" bigint DEFAULT 0 NOT NULL,
  "checksum" citext CHECK (length("checksum") <= 128),
  "description" citext CHECK (length("description") <= 512),
  "version" integer DEFAULT 1 NOT NULL,
  "git_sync_status" citext DEFAULT 'NONE' NOT NULL CHECK (length("git_sync_status") <= 32),
  "create_time" timestamp(3) without time zone NOT NULL,
  "update_time" timestamp(3) without time zone NOT NULL,
  PRIMARY KEY ("id")
);

CREATE INDEX "yak_ops_resource_idx_yak_resource_full_path" ON "yak_ops_resource" (left("full_path", 255));

CREATE INDEX "yak_ops_resource_idx_yak_resource_parent_type" ON "yak_ops_resource" ("parent_id", "node_type");

CREATE INDEX "yak_ops_resource_idx_yak_resource_project_parent_type" ON "yak_ops_resource" ("project_id", "parent_id", "node_type");

CREATE INDEX "yak_ops_resource_idx_yak_resource_project_path" ON "yak_ops_resource" ("project_id", left("full_path", 255));

CREATE INDEX "yak_ops_resource_idx_yak_resource_storage" ON "yak_ops_resource" ("storage_type", left("storage_path", 255));

CREATE UNIQUE INDEX "yak_ops_resource_uk_yak_resource_project_parent_name" ON "yak_ops_resource" ("project_id", "parent_id", "name");
