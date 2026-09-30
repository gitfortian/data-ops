-- Keep code-set history queryable after individual value rows have been deleted.
-- The generated key is derived from the immutable pre-change snapshot payload.
ALTER TABLE yak_semantic_standard_version
    ADD COLUMN code_set_code VARCHAR(64)
        GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.codeSetCode'))) STORED,
    ADD KEY idx_yak_semantic_std_version_codeset (project_id, code_set_code, id);
