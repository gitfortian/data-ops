# 02: SEM→DEV 边落地（或修图）

**对应需求:** 语义中心盘点 §6 缺失能力（P1）| 阶段: P1

**What to build:** 04 图声明实线 `SEM -->|"标准·业务域·过程"| DEV`,但 data-development 全模块对 `io.yak.ops.business.semantic` **零 import、pom 不依赖**——图中实线无代码实证。二选一落地,消除"图与代码不符"对图权威性的损伤:
- **方案 A(接线)**:数据开发任务编辑器接语义能力——SQL 建表/字段命名时经 `StandardRecommendApi` 做命名校验与标准推荐(提示不阻断,与 modeling 侧契约一致);
- **方案 B(修图)**:把 04 图该边从实线改虚线并标注"规划",在 05 清单记录缺口与重启条件。

**模块归属:** **跨模块**——semantic(提供 SPI,已存在) + data-development(消费方)

**Blocked by:** 无(方案 B 纯文档,可先行;方案 A 不依赖 01)

**Status:** done(2026-09-22 落地;真机 UI 复验待用户重启后端)

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md);方案 A 依赖方向单向 DEV→semantic,**DEV 只经 api 包 SPI**,禁止直读表;推荐/校验 fail-open,不阻断任务保存与运行。

## 裁决结论（A-最小，2026-09-22 用户拍板）

盘点证据修正了方案 A 的前提:DEV 的 `content` 是**自由 SQL 文本**(无字段 DDL 可挂),因此"SQL 建表/字段命名时校验"没有静态挂载点;可行入口是**解析当前 SQL 的输出字段**做命名检查。故取 **A-最小**:
- DEV 侧新增只读端点,复用既有 JSqlParser 血缘引擎抽取输出字段,逐字段调 `StandardRecommendApi` 命名校验;
- 前端 SQL 编辑器工具栏加"检查输出字段命名标准"按钮,弹**非阻断**报告;
- **不做** APPLY/BYPASS 用度回传(DEV 无字段级编辑器,套用语义动作无处安放——留待 DEV 获得字段级建模入口后再议),不接"业务域·过程"消费(04 图相应改虚线,剩余承诺由 MOD 边覆盖)。

## 落地记录（2026-09-22）

- **依赖走廊**:DEV pom 增 `yak-ops-business-semantic` `<optional>true</optional>`(与 security/mdm 同款);`DEPENDENCIES.md` 双侧登记(DEV Cross-module Corridors + Package Matrix `standard` 行;semantic 入向表 DEV 行)。
- **后端**:`standard/DevelopmentStandardCheckService`(新 `standard` 应用入口包,经 `DataDevelopmentRoleConventionTest` 白名单登记)——INSERT/CTAS/UPDATE 直接取 mapping 目标列;裸 SELECT 走合成 CTAS 包裹(同 `DevelopmentSqlProjectionLineageAnalyzer` 手法)取输出别名;大小写去重、上限 200 字段截断;每字段 `recommend(name,null,"UNKNOWN")`,任何异常/未评估降级 `evaluated=false`;解析失败整体降级为 `parseError` 报告而非 500。端点 `POST /api/v1/data-development/nodes/{nodeId}/standard-check`(类级 READ 权限,校验 SQL 节点存在,与 lineage/preview 同风格)。
- **测试**:`DevelopmentStandardCheckServiceTest` 6/6 绿(写目标列序/裸 SELECT 别名/fail-open/parseError/截断/非 SQL 拒绝);`./mvnw -o -pl` 全模块 150 用例,失败集与 HEAD 基线 worktree 完全一致(10 红全部存量,无新增);tsc 183 基线不变,触达文件零新错。
- **前端**:`api.ts checkDevelopmentSqlStandards` + types;`SqlToolbar` 血缘按钮后加 `ClipboardCheck` 按钮;报告经新 `StandardCheckModal`(符合/疑似不符/未评估 Tag + 建议标准 + 规则),zh/en 双语文案。
- **文档**:04 图 `SEM -->|③标准·业务域·过程| MTR & MOD & DEV` 拆为实线(MTR&MOD)+虚线 `SEM -.->|"命名标准符合度检查(仅提示·缺口单02)"| DEV`;语义盘点 §1/§5(DEV 行改判"已接(提示级)")/§6 行、数据开发盘点 §5/§6 P0 行/§7 总结 2/8/9 同步改口。

**遗留(明示未做)**:用度回传(APPLY/BYPASS)、发布期硬闸门、口径/类型带出——待 DEV 有字段级编辑入口或 04 图"③"实线承诺重新裁决时再启。

- [x] 裁决 A/B,结论写入 05 清单 → 记于本单"裁决结论"(04 图已同步;05 清单无需新乱象条目——本单是补线非纠偏)
- [x] 方案 A:任务编辑器命名校验入口 + 推荐候选展示(套用+`StandardUsageApi` 用度回传按裁决降级为遗留)
- [x] 方案 B(部分):04 图 DEV 边改虚线并标"命名标准符合度检查",`数据开发-现状与能力分析.md` 同步记载
- [x] 接线后 semantic 模块 §5 交互表更新,DEV 行由"未接"改判
- [x] 契约测试通过(新服务单测 + DEV 架构测试 RoleConvention/Boundary 绿;模块失败集=HEAD 基线)
