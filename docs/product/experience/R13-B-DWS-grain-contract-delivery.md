# R13-B｜DWS 手工汇总粒度：可保存、可回读、可冻结的设计合同

> 2026-10-17，单 PR 产品实现/验收合同。**不是 SQL 执行、目标数据落仓或指标消费成功证据。**

## 用户故事
建模人员选业务过程/已发布 DWD → 在现有 DWS 模型详情里完成字段和标准关联 → 保存物理结构 → 明确业务时间列、IANA 时区、汇总周期（1h/1d/1w/1m/ALL）、周期桶维度、其他维度、至少一个度量及白名单聚合函数 → 选择同过程已发布 DWD 精确版本 → **保存汇总粒度合同** → 重新打开回读 → 申请/发布 DWS **设计版本** → MetaJson 含不可变上游版本/粒度引用和 SHA。

订单黄金故事：`trade_order` → DWD `dwd_order_detail` 已发布精确 V2，事件时间 `event_time` → DWS `dws_order_daily`：`order_day` 作为必须参与分组的周期桶、`order_city` 维度、`order_count=COUNT_DISTINCT(order_id)` 和 `pay_amount=SUM(...)`（必须在上游列中找到真实依据且口径经人工确认），统计时区 `Asia/Shanghai`、周期 `1d`。**这里只冻结结构和业务合同，不自行编译聚合 SQL 或决定金额的业务口径。**

## 已实现合同
1. Modeling 自有 `yak_modeling_dws_grain_design`（V7 MySQL/PostgreSQL，V1–V6 字节不改），项目+模型唯一，内容 JSON、SHA256、结构编辑指纹、设计修订号与操作者。同事务写入，版本递增；CAS 拒绝结构/并发漂移；不属于物理表 DDL 属性，不复制 Semantic/Metric。
2. `GET/PUT /api/v1/modeling/models/{modelId}/dws-grain` 和 `GET .../upstream-candidates`，项目作用域与读/写权限独立。上游为同过程 DWD 的当前正式 ModelVersionRef (ID/No/SHA)，不能只传模型名，也不能使用跨过程、未发布/禁用或悬空快照。
3. 时间列在 **上游不可变结构快照**中必须真实存在且是日期时间类型（解析 JSON 结构，不用字符串包含猜测）。DWS 目标结构中周期桶必须实际存在并列为有标准字段的 DIMENSION，其他维度和 MEASURE 必须标准字段 ID、角色/聚合函数一致，至少一个度量。STAT_PERIOD/ZoneId/函数有限白名单。
4. 现有 `ModelVersionService` 所有 DWS 发布入口中心化检查合同、结构编辑指纹、历史合同 SHA 和**当前上游发布指针**是否仍等于冻结版本；非法拒绝发布。成功时 DWS 模型发布 checksum 绑定合同 digest，MetaJson 带合同内容、修订号及原版引用。同一结构但新合同视为新的设计版本；旧版不可变。
5. 已接入原模型详情页 DWS 原位面板；表结构 dirty 时禁止保存粒度；保存以后显示合同版本与上游精确 Vn；不新增孤立工具菜单，不自动执行任务。出错时提示重新读取、修复字段或上游。

## 本轮必须通过的负例
- 未保存表结构、结构指纹变化、外项目/非 DWS 模型、没有上游或失效 ModelVersion、跨过程/重复上游；
- 缺少/重复分组字段、周期桶不在维度中、错误业务时间列/非日期类型、无度量/聚合函数与字段不一致、没有标准字段 ID、非法时区/周期；
- 设计保存后结构或上游发布指针变化，不能以旧合同发布；
- DWS 设计发布不得触发 DataDev、Runtime、表创建/写入、指标发布；发布证据不代表数据质量或受治理消费已成功。

## 仍未交付
- 业务日期时间解析/时区 DST/迟到事件/NULL/去重指标口径由 Runtime SQL 加工和业务 Owner 明确认定，本阶段不能声称语义正确执行。
- DWS 具体 SQL/调度、真实聚合行数与订单去重对账、Metric 按正式口径绑定、受治理 Dataset/Usage；均留到 R13 后续跨域执行/消费闭环，并遵守 DR-07 #557。
- ADS 应用输出与消费方契约在 R13-C，不能拿 DWS 设计通过替代 ADS 已可消费。

## 验收证据
Product Guard + Architecture 全后端与全前端 + MySQL/PG V7 迁移检查 + DWS 服务/发布负例 + UI 保存/回读测试。只在实际运行通过后标记代码门禁 PASS。
