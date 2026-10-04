-- PostgreSQL fresh-deployment baseline. Existing MySQL migration checksums are unchanged.

-- Mirrors the consolidated MySQL domain baseline; forward changes require migrations for both vendors.

CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE "yak_workflow_definition" (
  "id" citext NOT NULL CHECK (length("id") <= 80),
  "project_id" bigint NOT NULL,
  "name" citext NOT NULL CHECK (length("name") <= 200),
  "description" citext CHECK (length("description") <= 1000),
  "status" citext NOT NULL CHECK (length("status") <= 32),
  "draft_revision" bigint DEFAULT 1 NOT NULL,
  "latest_version_no" integer DEFAULT 0 NOT NULL,
  "active_version_id" citext CHECK (length("active_version_id") <= 80),
  "draft_json" text NOT NULL,
  "latest_execution_id" citext CHECK (length("latest_execution_id") <= 80),
  "latest_execution_status" citext CHECK (length("latest_execution_status") <= 32),
  "create_time" timestamp(3) without time zone NOT NULL,
  "update_time" timestamp(3) without time zone NOT NULL,
  PRIMARY KEY ("id")
);

CREATE INDEX "yak_workflow_definition_idx_yak_workflow_definition_pr_04a79351" ON "yak_workflow_definition" ("project_id", "status", "update_time");

CREATE INDEX "yak_workflow_definition_idx_yak_workflow_definition_status" ON "yak_workflow_definition" ("status");

CREATE INDEX "yak_workflow_definition_idx_yak_workflow_definition_update_time" ON "yak_workflow_definition" ("update_time");

CREATE TABLE "yak_workflow_version" (
  "id" citext NOT NULL CHECK (length("id") <= 80),
  "project_id" bigint NOT NULL,
  "workflow_id" citext CHECK (length("workflow_id") <= 80),
  "version_no" integer,
  "version_kind" citext DEFAULT 'PUBLISHED' NOT NULL CHECK (length("version_kind") <= 16),
  "draft_revision" bigint,
  "run_request_json" text,
  "editor_meta_json" text,
  "task_versions_json" text,
  "engine_definition_json" text,
  "runtime_metadata_json" text,
  "create_time" timestamp(3) without time zone NOT NULL,
  PRIMARY KEY ("id")
);

CREATE INDEX "yak_workflow_version_idx_yak_workflow_version_project_workflow" ON "yak_workflow_version" ("project_id", "workflow_id", "create_time");

CREATE INDEX "yak_workflow_version_idx_yak_workflow_version_workflow" ON "yak_workflow_version" ("workflow_id", "create_time");

CREATE UNIQUE INDEX "yak_workflow_version_uk_yak_workflow_version_no" ON "yak_workflow_version" ("workflow_id", "version_no");

CREATE TABLE "yak_workflow_execution" (
  "id" citext NOT NULL CHECK (length("id") <= 80),
  "project_id" bigint NOT NULL,
  "definition_id" citext NOT NULL CHECK (length("definition_id") <= 80),
  "source_execution_id" citext CHECK (length("source_execution_id") <= 80),
  "status" citext NOT NULL CHECK (length("status") <= 32),
  "input_json" text NOT NULL,
  "scheduling_stopped" smallint DEFAULT 0 NOT NULL,
  "run_started_at" timestamp(3) without time zone,
  "paused_at" timestamp(3) without time zone,
  "paused_duration_ms" bigint DEFAULT 0 NOT NULL,
  "created_at" timestamp(3) without time zone NOT NULL,
  "updated_at" timestamp(3) without time zone NOT NULL,
  "ended_at" timestamp(3) without time zone,
  "workflow_name" citext CHECK (length("workflow_name") <= 200),
  "workflow_version_id" citext CHECK (length("workflow_version_id") <= 80),
  "workflow_version_no" integer,
  "test_run" smallint DEFAULT 0 NOT NULL,
  "edge_count" integer DEFAULT 0 NOT NULL,
  "workflow_timeout_seconds" bigint DEFAULT 0 NOT NULL,
  "failure_strategy" citext CHECK (length("failure_strategy") <= 64),
  "runtime_metadata_json" text,
  "audit_carrier_json" text,
  PRIMARY KEY ("id")
);

CREATE INDEX "yak_workflow_execution_idx_yak_workflow_execution_created" ON "yak_workflow_execution" ("created_at");

CREATE INDEX "yak_workflow_execution_idx_yak_workflow_execution_defi_008113b0" ON "yak_workflow_execution" ("definition_id", "status", "created_at");

CREATE INDEX "yak_workflow_execution_idx_yak_workflow_execution_proj_2417b30c" ON "yak_workflow_execution" ("project_id", "created_at");

CREATE INDEX "yak_workflow_execution_idx_yak_workflow_execution_proj_a263496d" ON "yak_workflow_execution" ("project_id", "definition_id", "created_at");

CREATE INDEX "yak_workflow_execution_idx_yak_workflow_execution_proj_09cd5474" ON "yak_workflow_execution" ("project_id", "status", "updated_at");

CREATE INDEX "yak_workflow_execution_idx_yak_workflow_execution_status" ON "yak_workflow_execution" ("status", "updated_at");

CREATE INDEX "yak_workflow_execution_idx_yak_workflow_execution_version" ON "yak_workflow_execution" ("workflow_version_id");

CREATE TABLE "yak_workflow_node_execution" (
  "id" citext NOT NULL CHECK (length("id") <= 80),
  "workflow_execution_id" citext NOT NULL CHECK (length("workflow_execution_id") <= 80),
  "node_id" citext NOT NULL CHECK (length("node_id") <= 120),
  "failure_policy" citext NOT NULL CHECK (length("failure_policy") <= 32),
  "status" citext NOT NULL CHECK (length("status") <= 32),
  "output_json" text NOT NULL,
  "error_message" text,
  "failure_handled" smallint DEFAULT 0 NOT NULL,
  "downstream_continuation_allowed" smallint DEFAULT 0 NOT NULL,
  PRIMARY KEY ("id")
);

CREATE INDEX "yak_workflow_node_execution_idx_yak_workflow_node_status" ON "yak_workflow_node_execution" ("workflow_execution_id", "status");

CREATE UNIQUE INDEX "yak_workflow_node_execution_uk_yak_workflow_node_execution" ON "yak_workflow_node_execution" ("workflow_execution_id", "node_id");

CREATE TABLE "yak_workflow_node_attempt" (
  "id" citext NOT NULL CHECK (length("id") <= 80),
  "node_execution_id" citext NOT NULL CHECK (length("node_execution_id") <= 80),
  "workflow_execution_id" citext NOT NULL CHECK (length("workflow_execution_id") <= 80),
  "node_id" citext NOT NULL CHECK (length("node_id") <= 120),
  "attempt_no" integer NOT NULL,
  "available_at" timestamp(3) without time zone NOT NULL,
  "status" citext NOT NULL CHECK (length("status") <= 32),
  "resume_target_status" citext CHECK (length("resume_target_status") <= 32),
  "started_at" timestamp(3) without time zone,
  "paused_at" timestamp(3) without time zone,
  "paused_duration_ms" bigint DEFAULT 0 NOT NULL,
  "ended_at" timestamp(3) without time zone,
  "error_message" text,
  "failure_reason" citext CHECK (length("failure_reason") <= 64),
  "external_execution_id" citext CHECK (length("external_execution_id") <= 120),
  PRIMARY KEY ("id")
);

CREATE INDEX "yak_workflow_node_attempt_idx_yak_workflow_attempt_external" ON "yak_workflow_node_attempt" ("external_execution_id");

CREATE INDEX "yak_workflow_node_attempt_idx_yak_workflow_attempt_workflow" ON "yak_workflow_node_attempt" ("workflow_execution_id", "status");

CREATE UNIQUE INDEX "yak_workflow_node_attempt_uk_yak_workflow_attempt_no" ON "yak_workflow_node_attempt" ("node_execution_id", "attempt_no");

CREATE TABLE "yak_workflow_schedule" (
  "id" citext NOT NULL CHECK (length("id") <= 80),
  "project_id" bigint NOT NULL,
  "workflow_id" citext NOT NULL CHECK (length("workflow_id") <= 80),
  "name" citext NOT NULL CHECK (length("name") <= 200),
  "trigger_type" citext DEFAULT 'CRON' NOT NULL CHECK (length("trigger_type") <= 32),
  "cron_expression" citext NOT NULL CHECK (length("cron_expression") <= 160),
  "timezone" citext DEFAULT 'Asia/Shanghai' NOT NULL CHECK (length("timezone") <= 80),
  "start_time" timestamp(3) without time zone,
  "end_time" timestamp(3) without time zone,
  "status" citext DEFAULT 'OFFLINE' NOT NULL CHECK (length("status") <= 32),
  "execution_strategy" citext DEFAULT 'SERIAL_WAIT' NOT NULL CHECK (length("execution_strategy") <= 32),
  "misfire_strategy" citext DEFAULT 'FIRE_ONCE' NOT NULL CHECK (length("misfire_strategy") <= 32),
  "input_json" text NOT NULL,
  "last_fire_time" timestamp(3) without time zone,
  "next_fire_time" timestamp(3) without time zone,
  "create_time" timestamp(3) without time zone NOT NULL,
  "update_time" timestamp(3) without time zone NOT NULL,
  PRIMARY KEY ("id")
);

CREATE INDEX "yak_workflow_schedule_idx_yak_workflow_schedule_next_fire" ON "yak_workflow_schedule" ("status", "next_fire_time");

CREATE INDEX "yak_workflow_schedule_idx_yak_workflow_schedule_next_fire_only" ON "yak_workflow_schedule" ("next_fire_time");

CREATE INDEX "yak_workflow_schedule_idx_yak_workflow_schedule_projec_33dc32d7" ON "yak_workflow_schedule" ("project_id", "next_fire_time");

CREATE INDEX "yak_workflow_schedule_idx_yak_workflow_schedule_project_status" ON "yak_workflow_schedule" ("project_id", "status", "next_fire_time");

CREATE INDEX "yak_workflow_schedule_idx_yak_workflow_schedule_projec_c6d2ba02" ON "yak_workflow_schedule" ("project_id", "workflow_id", "update_time");

CREATE INDEX "yak_workflow_schedule_idx_yak_workflow_schedule_status" ON "yak_workflow_schedule" ("status", "update_time");

CREATE INDEX "yak_workflow_schedule_idx_yak_workflow_schedule_workflow" ON "yak_workflow_schedule" ("workflow_id", "update_time");

CREATE UNIQUE INDEX "yak_workflow_schedule_uk_yak_workflow_schedule_name" ON "yak_workflow_schedule" ("workflow_id", "name");

CREATE TABLE "yak_workflow_schedule_trigger" (
  "id" citext NOT NULL CHECK (length("id") <= 80),
  "project_id" bigint NOT NULL,
  "schedule_id" citext NOT NULL CHECK (length("schedule_id") <= 80),
  "workflow_id" citext NOT NULL CHECK (length("workflow_id") <= 80),
  "backfill_id" citext CHECK (length("backfill_id") <= 80),
  "trigger_id" citext NOT NULL CHECK (length("trigger_id") <= 160),
  "dedupe_key" citext NOT NULL CHECK (length("dedupe_key") <= 320),
  "trigger_source" citext DEFAULT 'CRON' NOT NULL CHECK (length("trigger_source") <= 32),
  "planned_fire_time" timestamp(3) without time zone NOT NULL,
  "actual_fire_time" timestamp(3) without time zone NOT NULL,
  "business_date" date,
  "execution_strategy" citext NOT NULL CHECK (length("execution_strategy") <= 32),
  "misfire_strategy" citext NOT NULL CHECK (length("misfire_strategy") <= 32),
  "status" citext NOT NULL CHECK (length("status") <= 32),
  "workflow_execution_id" citext CHECK (length("workflow_execution_id") <= 80),
  "execution_status" citext CHECK (length("execution_status") <= 32),
  "message" citext CHECK (length("message") <= 1000),
  "error_message" text,
  "launched_at" timestamp(3) without time zone,
  "completed_at" timestamp(3) without time zone,
  "create_time" timestamp(3) without time zone NOT NULL,
  "update_time" timestamp(3) without time zone NOT NULL,
  PRIMARY KEY ("id")
);

CREATE INDEX "yak_workflow_schedule_trigger_idx_yak_workflow_schedul_04eb9d1c" ON "yak_workflow_schedule_trigger" ("backfill_id", "status", "planned_fire_time");

CREATE INDEX "yak_workflow_schedule_trigger_idx_yak_workflow_schedul_f353b3b4" ON "yak_workflow_schedule_trigger" ("workflow_id", "business_date");

CREATE INDEX "yak_workflow_schedule_trigger_idx_yak_workflow_schedul_9bff6286" ON "yak_workflow_schedule_trigger" ("workflow_execution_id");

CREATE INDEX "yak_workflow_schedule_trigger_idx_yak_workflow_schedul_1776d073" ON "yak_workflow_schedule_trigger" ("schedule_id", "create_time");

CREATE INDEX "yak_workflow_schedule_trigger_idx_yak_workflow_schedul_53735c81" ON "yak_workflow_schedule_trigger" ("workflow_id", "status", "planned_fire_time");

CREATE INDEX "yak_workflow_schedule_trigger_idx_yak_workflow_trigger_ca2dffb5" ON "yak_workflow_schedule_trigger" ("project_id", "status", "planned_fire_time");

CREATE INDEX "yak_workflow_schedule_trigger_idx_yak_workflow_trigger_5240e181" ON "yak_workflow_schedule_trigger" ("project_id", "workflow_id", "create_time");

CREATE UNIQUE INDEX "yak_workflow_schedule_trigger_uk_yak_workflow_schedule_29ecbb6c" ON "yak_workflow_schedule_trigger" ("dedupe_key");

CREATE UNIQUE INDEX "yak_workflow_schedule_trigger_uk_yak_workflow_schedule_87993387" ON "yak_workflow_schedule_trigger" ("trigger_id");

CREATE TABLE "yak_workflow_backfill" (
  "id" citext NOT NULL CHECK (length("id") <= 80),
  "project_id" bigint NOT NULL,
  "workflow_id" citext NOT NULL CHECK (length("workflow_id") <= 80),
  "workflow_version_id" citext NOT NULL CHECK (length("workflow_version_id") <= 80),
  "workflow_version_no" integer NOT NULL,
  "schedule_id" citext NOT NULL CHECK (length("schedule_id") <= 80),
  "schedule_name" citext NOT NULL CHECK (length("schedule_name") <= 100),
  "name" citext NOT NULL CHECK (length("name") <= 120),
  "status" citext NOT NULL CHECK (length("status") <= 32),
  "operation_type" citext DEFAULT 'BACKFILL' NOT NULL CHECK (length("operation_type") <= 32),
  "source_execution_id" citext CHECK (length("source_execution_id") <= 80),
  "start_business_date" date NOT NULL,
  "end_business_date" date NOT NULL,
  "cron_expression" citext NOT NULL CHECK (length("cron_expression") <= 160),
  "timezone" citext NOT NULL CHECK (length("timezone") <= 80),
  "execution_strategy" citext NOT NULL CHECK (length("execution_strategy") <= 32),
  "schedule_input_json" text,
  "input_json" text,
  "total_count" integer DEFAULT 0 NOT NULL,
  "create_time" timestamp(3) without time zone NOT NULL,
  "update_time" timestamp(3) without time zone NOT NULL,
  PRIMARY KEY ("id")
);

CREATE INDEX "yak_workflow_backfill_idx_yak_workflow_backfill_operation" ON "yak_workflow_backfill" ("operation_type", "create_time");

CREATE INDEX "yak_workflow_backfill_idx_yak_workflow_backfill_project_status" ON "yak_workflow_backfill" ("project_id", "status", "create_time");

CREATE INDEX "yak_workflow_backfill_idx_yak_workflow_backfill_projec_00908720" ON "yak_workflow_backfill" ("project_id", "workflow_id", "create_time");

CREATE INDEX "yak_workflow_backfill_idx_yak_workflow_backfill_schedule" ON "yak_workflow_backfill" ("schedule_id", "create_time");

CREATE INDEX "yak_workflow_backfill_idx_yak_workflow_backfill_source_8146227d" ON "yak_workflow_backfill" ("source_execution_id");

CREATE INDEX "yak_workflow_backfill_idx_yak_workflow_backfill_status" ON "yak_workflow_backfill" ("status", "create_time");

CREATE INDEX "yak_workflow_backfill_idx_yak_workflow_backfill_workflow" ON "yak_workflow_backfill" ("workflow_id", "create_time");
