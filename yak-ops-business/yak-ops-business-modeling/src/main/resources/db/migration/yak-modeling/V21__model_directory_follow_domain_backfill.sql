-- 目录跟随业务域(V19)之后新建/编辑的模型才会自动落目录，此前建的存量模型即使已有
-- domain_id 也仍停在未分类(0)，左侧目录树按域看不到它们。按域已绑定的目录回填一次归属：
-- 同域多条目录取最小 ID(与 findByDomainId 的 orderByAsc(id) LIMIT 1 一致)；域还没有绑定
-- 目录的保持未分类，等下一次保存该模型时由 ensureDirectoryForDomain 补目录。
-- 命中条件带 directory_id = 0，重复执行只会扫到仍未归位的行，天然幂等。

UPDATE yak_modeling_model m
    JOIN (
        SELECT d.project_id, d.domain_id, MIN(d.id) AS directory_id
        FROM yak_modeling_directory d
        WHERE d.domain_id IS NOT NULL
        GROUP BY d.project_id, d.domain_id
    ) bound ON bound.project_id = m.project_id AND bound.domain_id = m.domain_id
   SET m.directory_id = bound.directory_id
 WHERE m.deleted = 0
   AND m.directory_id = 0;
