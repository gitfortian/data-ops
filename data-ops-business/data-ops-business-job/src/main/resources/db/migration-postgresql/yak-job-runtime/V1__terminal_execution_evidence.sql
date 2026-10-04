-- PostgreSQL fresh-deployment baseline. Existing MySQL migration checksums are unchanged.

-- Mirrors the consolidated MySQL domain baseline; forward changes require migrations for both vendors.

CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE "yak_job_execution_result" (
  "execution_id" citext NOT NULL CHECK (length("execution_id") <= 128),
  "project_id" bigint NOT NULL,
  "task_type" citext NOT NULL CHECK (length("task_type") <= 32),
  "idempotency_hash" citext CHECK (length("idempotency_hash") <= 64),
  "status" citext NOT NULL CHECK (length("status") <= 32),
  "error_message" text,
  "output_json" text NOT NULL,
  "completed_at" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  PRIMARY KEY ("execution_id")
);

CREATE INDEX "yak_job_execution_result_idx_job_result_project_time" ON "yak_job_execution_result" ("project_id", "completed_at");

CREATE UNIQUE INDEX "yak_job_execution_result_uk_job_result_project_type_key" ON "yak_job_execution_result" ("project_id", "task_type", "idempotency_hash");
