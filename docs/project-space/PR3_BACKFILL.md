# Project Space PR3 回填与验收

PR3 只扩展 DataSource、Resource、Dataset 三个聚合根。数据库列先保持 nullable，接口先进入 `PROJECT_OPTIONAL`，完成备份、回填和双项目隔离验证后，后续 Contract PR 才切换 `PROJECT_REQUIRED` 与 `NOT NULL`。

## 回填前提

1. Yak Security 中已经存在用于承接历史数据的默认 Project；
2. 默认 Project 已配置负责人和现有用户成员关系；
3. 已记录默认 Project 的真实 ID，禁止假设它等于 `1`；
4. 已备份业务库与 Resource 物理存储目录。

以下 SQL 中的 `:default_project_id` 必须由部署人员替换为真实 ID：

```sql
UPDATE yak_ops_data_source
SET project_id = :default_project_id
WHERE project_id IS NULL;

UPDATE yak_ops_resource
SET project_id = :default_project_id
WHERE project_id IS NULL;

UPDATE yak_dataset
SET project_id = :default_project_id
WHERE project_id IS NULL;
```

## Resource 物理路径

新建的项目资源使用 `projects/{projectId}/...` 物理命名空间，逻辑路径仍保持 `/目录/文件`。历史 Resource 回填 `project_id` 后，其已有 `storage_path` 不应只靠 SQL 批量改写；需要根据实际存储插件执行文件移动，并在同一维护窗口更新 `storage_path`。

建议流程：

```text
停止 Resource 写入
  -> 导出 id/project_id/storage_type/storage_path/full_path
  -> 按存储插件移动到 projects/{projectId}/...
  -> 更新 storage_path
  -> 校验 checksum 与下载
  -> 恢复写入
```

## 验收 SQL

```sql
SELECT COUNT(*) AS unowned_datasource
FROM yak_ops_data_source
WHERE project_id IS NULL;

SELECT COUNT(*) AS unowned_resource
FROM yak_ops_resource
WHERE project_id IS NULL;

SELECT COUNT(*) AS unowned_dataset
FROM yak_dataset
WHERE project_id IS NULL;

SELECT project_id, name, COUNT(*) AS duplicate_count
FROM yak_ops_data_source
GROUP BY project_id, name
HAVING COUNT(*) > 1;

SELECT project_id, parent_id, name, COUNT(*) AS duplicate_count
FROM yak_ops_resource
GROUP BY project_id, parent_id, name
HAVING COUNT(*) > 1;
```

## 双项目接口验收

使用 Project A 和 Project B 分别创建同名数据源、同路径资源与数据集，验证：

- 列表、详情、编辑、删除、连接测试和下载只看到当前项目；
- 直接替换 URL 中的其他项目对象 ID 返回 `PROJECT_NOT_FOUND`；
- Dataset 不能读取其他项目的数据源或 TaskAsset；
- Dataset 查询性能诊断不会返回其他项目的 trace；
- 不在 rollout 表中的 Workflow、Sync、Quality 等接口仍保持旧语义。

所有 null 归属、物理路径和双项目用例通过后，才允许进入 Contract 收口。
