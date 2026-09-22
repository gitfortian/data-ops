# Ticket 123：前端·统一搜索、浏览与实体详情

**对应需求：** 平台级发现层 | **阶段：** P4 | **模块：** yak-ops-ui

**What to build：** 一个搜索框横跨物理与逻辑实体，结果混排展示；类型切换是 **facet 而不是多入口**；实体详情按类型出不同面板；变更历史时间线。

**Blocked by：** 117、118

**验收清单**
- [x] 搜索结果按类型**混排**展示，每行用 `type_def.display_name / icon / color` 渲染——**不是"表专区 + 模型专区"两块**
- [x] 类型切换只改 `index` 参数、不换接口；facet 计数来自服务端同一次聚合
- [x] `tableColumn` 默认不进结果行，以"命中 N 列"聚合形式露出，点进去是该表列的过滤视图（plan §4.6）；显式选 `tableColumn` 时正常出行
- [x] 深分页用 `searchAfter` 游标，不做 offset 深翻；总数不精确时显示"1000+"而不是假装精确
- [ ] 详情页：物理侧带列/历史/标签/血缘入口，投影侧带"实时读源域"标记与跳转源域编辑的入口
- [ ] 变更历史时间线读 `yak_md_change`（append-only，plan §2.6）
- [x] `q` 单字时前端不拦，但按 `explain` 显示"已降级为模糊匹配"（plan §4.3）
- [ ] 存储量块显示快照日期 + "近似"标注；来源为 lifecycle 只读

**实施记录（2026-09-21）**

- 统一搜索页与目录浏览页**共用一颗核** `pages/data-metadata/components/AssetExplorer.tsx`：两页只差一个
  `searchable` 开关（是否给 q 输入框）。这不是省事——浏览与检索若各写一套过滤逻辑，两页的口径迟早会分叉，
  而它们本来就是同一条接口的两种参数。
- 类型清单、类型徽标色、属性筛选控件全部读自 `GET /metadata/types`：控件只给 `field_def.filterable=1` 的字段，
  形状按 `match_type` 出（range 给起止两个输入，拼成 `from..to`）。新增一类实体不必改这一页。
- 「命中 N 列」下钻 = `index=tableColumn` + `queryFilter.parentAssetId=<表 id>`，**不开第二条接口**。
  为此给工单 117 的原生筛选键白名单加了 `parentAssetId`（与列命中 rollup 的 `GROUP BY parent_asset_id` 同列），
  BIGINT 键按数字绑参，避免 MySQL 隐式转换丢索引。
- 分页分治：有游标走 `searchAfter`（进入下一页时记住上一轮回的游标），无游标（relevance 排序不提供游标）
  走 offset，且 offset 预算与服务端 `MAX_OFFSET=5000` 同值、到顶即禁用下一页并说明原因，不做静默截断。
- 单字 q：前端不拦（后端会降级 LIKE），按 `explain.degradedToLike` 显示"已降级为模糊匹配，结果可能偏宽"。
  `explain` 勾选后悬浮可见后端名、布尔查询串、SQL 条数与 notes。
- 详情抽屉只渲染统一搜索**已经给回**的事实（目录固有列 + `md_attributes` 属性袋，属性标签取 field_def.displayName），
  并在抽屉里显式写明：列清单/变更历史/标签/血缘/存储统计属于工单 118、126 的聚合接口，未交付不摆假数据。
  投影侧的"实时读源域"标记与跳转已接（`dataModel`/`metric` 走详情页深链，`standardField`/`domain` 当前无按 id 的
  路由，落到对应列表页而不是编一个假详情地址）。
- 校验边界：`tsc` 仍为 199 基线。

**真机走查（2026-09-21，后端重启后）**

- 造数走的是产品自己的通道：`采集与对账` 建 `demo-crm`（数据源 3 / `crm_db` / `collectColumns`）→ 预演 37 →
  正式运行 37，库里于是有了 `databaseService`1 + `database`1 + `table`3 + `tableColumn`32 的在场行
  （此前 276 条 lineage 遗留行 `type_id` 为 NULL，按检索面定义本就不该被搜到——那不是 bug）。
- 走查结果：q=客户 混排 4 行（3 表 + 1 数据源服务）、facet 计数与 total 同源、每表带"命中 N 列"（1/2/2，
  随 q 收窄）；点下钻进 13 列视图并可退出；选 `物理列` 后 32 行走游标翻页（第 2 页无重叠）；
  单字 q 出"已降级为模糊匹配"；`explain` 回 `backend=mysql / sqlCount=4 / degradedToLike / 两条 notes`；
  `getHierarchy` 给回父实体（列 → `crm_db`）。抽屉属性袋标签取到 field_def 中文名。
- **走查逼出工单 117 的一个执行期缺陷**：三条聚合语句写成"一参 + `return null`"的 lambda，
  静默绑到 `ResultSetExtractor` 而不是逐行回调，于是整段只对停在首行之前的空游标跑一次，
  取值即抛 SQLException → 被 advice 收敛成 999，**整个搜索端点全不可用**。已改为显式
  `(RowCallbackHandler)`，并补 `MysqlMetadataSearchBackendTest` 钉住"逐行回调真的被调到"
  （把旧写法放回去该测试即红）。这类形状只有真发一次请求才暴露，单测面此前完全没覆盖执行侧。
- 顺带两处修正：`datasourceId` 不再按数字绑（`data_source_id` 是 varchar，压成数字反而废索引），
  只有 `parentAssetId` 按 BIGINT 绑；元模型里与原生筛选条同名的字段不再重复出控件
  （此前"分层"同时给两个输入框、写两个键）。查询失败也不再显示成"目录里还没有数据"，
  改为"检索失败 + 重试"，免得把服务端的错记到数据账上。
