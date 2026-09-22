# Ticket 117：跨类型统一搜索（MySQL 后端 + ngram + 类型 facet）

**对应需求：** 统一检索 | **阶段：** P2 | **模块：** metadata

**What to build：** `GET /api/v1/metadata/search` —— 物理表 + 物理列 + 模型 + 标准字段 + 业务域 + 指标在**一次查询**里返回混合结果，类型是 facet 而不是多入口。这是本次需求修正的核心诉求，**"每类一次查询再合并"直接判本票不通过**（那正是 plan §1.1 否掉的做法）。

**Blocked by：** 114/115/130（目录里有数据）、128/129（类型与可搜性由元模型解释）

**验收清单**
- [ ] `MetadataSearchBackend` 接缝 + 唯一实现 `MysqlMetadataSearchBackend`（换 ES 时只加实现、不改源域挂钩，plan §4.4）
- [ ] 参数面照搬 OM `/v1/search/query` 的子集（plan §4.2）：`q / index / queryFilter / postFilter / includeFields / excludeFields / searchAfter / sortField / sortOrder / trackTotalHits / getHierarchy / explain`
- [ ] **命名规则写死**：API/JSON 层叫 `typeName`、落库是 `type_id`（join 元模型取名）；属性袋逻辑名 `attributes`、落库列 `md_attributes`。**出现 `entityType`/`type`/`kind` 第三个名字即视为契约违反**；**绝不按 `asset_type` 过滤或分桶**（它会把 `table` 与 `dataModel` 显示成同一类型）
- [ ] `queryFilter` **参与**聚合计数、`postFilter` **不影响**聚合计数（facet 联动的关键区分，一期必须分对，§8 P2 实测）
- [ ] `attr.<field>` 只作用于 `field_def` 声明过的字段，未声明 → 49xxx（防 `md_attributes` 变成"什么都能塞但查不动"的黑洞，plan §9 T18）
- [ ] 搜索条件**由 `field_def` 运行时生成、零代码分支**：`text`→并入 FULLTEXT 目标、`exact`→`slot = ?`、`like`→`slot LIKE`（**UI 标注慢查询**）、`range`→`BETWEEN`（plan §4.5 四条表）
- [ ] `MATCH … AGAINST(… IN BOOLEAN MODE)` 的转义**在服务层集中做**，覆盖 `+ - > < ( ) ~ * " @`，转义函数单测全枚举（这是搜索接口的注入面，plan §4.3）
- [ ] `q.length()==1` → 降级 `LIKE '%x%'` 并在 `explain` 标注降级路径；**不做前端输入长度限制**（那是在藏问题）
- [ ] 类型分布来自**同一次聚合**（`GROUP BY type_id`）；换类型只改 `index` 参数、不换接口
- [ ] `trackTotalHits` 默认 false（总数走估算或分桶，不每页 `COUNT(*)`，plan §0.11）；`searchAfter = (sortValue, id)` 游标替代 offset 深翻
- [ ] `getHierarchy=1` 时列带出其所属表，**一次自连接**，不逐行回查（N+1 守护）
- [ ] **一期就还"列淹没"这笔账**（plan §4.6）：默认检索面不含 `tableColumn`（`search_include_by_default=0`），列以"命中 N 列"的聚合形式露出；显式 `index=tableColumn` 时正常出行；排序只做 `search_default_weight` 乘性加权（表 > 列），**不承诺相关性质量**
