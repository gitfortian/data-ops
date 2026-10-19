-- R14-B2 T1: persisted, per-project Semantic publication revision foundation.
-- This does NOT enable publication, issue a revision lease, or grant target writes.
-- All Semantic modification paths must be integrated before treating this as authoritative.
CREATE TABLE IF NOT EXISTS yak_semantic_publication_revision (
    project_id BIGINT NOT NULL PRIMARY KEY,
    revision_no BIGINT NOT NULL DEFAULT 1,
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_semantic_pub_revision_positive CHECK (revision_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Historical standards have an initial revision identity; do NOT overwrite
-- revisions created by an already-applied migration or prior post-migration edits.
INSERT INTO yak_semantic_publication_revision (project_id, revision_no)
SELECT project_id, 1
FROM (
    SELECT project_id FROM yak_semantic_standard
    UNION
    SELECT project_id FROM yak_semantic_field
) AS existing_project
ON DUPLICATE KEY UPDATE revision_no = revision_no;
