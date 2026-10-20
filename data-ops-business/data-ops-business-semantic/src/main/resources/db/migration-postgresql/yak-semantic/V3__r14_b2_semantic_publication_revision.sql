-- R14-B2 T1: persisted, per-project Semantic publication revision foundation.
-- No publication gate, token issuer, runtime target write or lease is enabled.
CREATE TABLE IF NOT EXISTS yak_semantic_publication_revision (
    project_id BIGINT PRIMARY KEY,
    revision_no BIGINT NOT NULL DEFAULT 1,
    update_time TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_semantic_pub_revision_positive CHECK (revision_no > 0)
);

-- Seed a stable initial revision for projects already holding standards/fields.
-- Mutations must advance this row in their own business transaction before the
-- revision can be used as a fenced publication witness.
INSERT INTO yak_semantic_publication_revision (project_id, revision_no)
SELECT project_id, 1
FROM (
    SELECT project_id FROM yak_semantic_standard
    UNION
    SELECT project_id FROM yak_semantic_field
) AS existing_project
ON CONFLICT (project_id) DO NOTHING;
