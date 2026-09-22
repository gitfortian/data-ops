# 06: 详情页定义字段补全与引用跳转

**对应需求:** 指标中心盘点 §3.4/§6(P1)| 阶段: P1

**What to build:** 旧-工单 48 要求"概览必须展示全部定义字段、引用类字段可点击跳转":`MetricController.enrichNames` 已在服务端备好业务域/模型/口径/单位**展示名**,但详情页 Descriptions 没有渲染这些引用字段,也没有跳转链接;派生指标最核心的 dimConstraint、statDimensions、unitName 全部不展示,用户须回编辑弹窗看定义。属"后端已备、前端未摆"的纯展示单。

**模块归属:** metric(纯前端,detail/index.tsx 概览区)

**Blocked by:** 无;02 落地后派生继承链展示追加在本单成果之上

**Status:** backlog(待排期)

**硬性约束(不可打破):** 遵守 [gap-backlog 批次约束](gap-backlog-2026-09.md);不改后端接口(enrich 数据已够);跳转仅到已存在页面(域→semantic、模型→modeling 详情、口径/单位→semantic),无目标页的字段只展示名不加链接。

- [x] 概览区补渲染:业务域/来源模型/DIM 模型/口径/单位/statDimensions/dimConstraint(按类型差异化)
- [x] 引用类字段可点击跳转对应模块详情
- [x] 缺失值显示"—"而非留空或报错;types.ts 若缺 enrich 字段声明一并补
- [ ] 三类型(原子/派生/复合)各造一条数据实测六区展示完整
