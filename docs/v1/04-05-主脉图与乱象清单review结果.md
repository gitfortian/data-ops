# 对 04-主脉与模块交互图 / 05-模块交互与乱象清单（v0.1）的 review（2026-09-21）

总评：**结构与做法认可**——交互登记册（传什么/守哪条/坏了会怎样/现状）是这批文档里最实用的表；乱象清单"先发现不治理"的纪律对。抽查 18 条登记 + R-1~R-8，大多数事实声明在代码里对得上（通过项见文末）。但有 **3 处必须修**、**1 处定性商榷**，修完即可发布。

---

## 必须修

### 1.（04 图二）血缘登记虚线边画错了主语，且和自己的文字版矛盾 🔴

图中 `SYN & DEV & WFL & QUA & DST -.->|血缘登记| LIN`。实测：**sync（含 offline/realtime）、workflow、quality 三个模块主代码对 lineage 零引用**（全仓 grep `business.lineage|LineageRegistration` 无命中）——"同步/工作流/质量登记血缘"目前没有这回事。
更扎心的是真正登记血缘的两家反而没画：**建模和指标**（`ModelingLineageRegistrationService`/`MetricLineageRegistrationService`）——这正是 S6/S7 点名的、也是 M-3 刚修好的那条链。05 登记册 #8/#9 写的是对的（建模→血缘、指标→血缘），**图二与 05 自相矛盾**。
改法：虚线主语换成 `MOD & MTR & DEV & DST`（DEV 有 SqlLineage 服务、DST/dashboard/analysis 有 gateway adapter 调 registerAsset，成立）。

### 2.（04 图一）"五层表结构"是把 01 刚修掉的旧错又捡回来了 🔴

图一 MOD 节点小字"五层表结构"。01 v0.4→v0.6 已经裁定过：口径是**"分层可配置、预置 ODS/DWD/DWS/ADS 四层"**（semantic 迁移 INSERT 实测只有这四层）。同一族错误第二次复发，恰好印证 01 防复发那条"词汇进自动检查"不该再等——文档里"五层"二字应进 CI 违禁词。

### 3.（05 #15）"四类流已定义 ✅"过时，且与图二对不上 🟡

`ApprovalFlowCodes` 实测 **5 个**：MODEL_PUBLISH / STANDARD_PUBLISH / ACCESS_GRANT / MDM_CHANGE / **ASSET_PUBLISH**（今天随 M2-5 落主线：资产详情「申请上架审批」→审批通过自动上架）。04 图二 L145 自己画了 `AST -.->|上架申请| APR`，05 却还说四类——两文同步改成"五类"。建议同时注明使用前提：ASSET_PUBLISH 的流程配置需在审批中心 UI 建立，未配置前发起报 49007（属预期缺口，非 bug；MODEL_PUBLISH 先例同）。

## 定性商榷（建议随拍板记录改写，不算事实错误）

### R-2 / 图二 MET ⚠️：现象属实，但这不是失控乱象，是已拍板决策的落地

redirect 本身核实为真：路由表 `/data-metadata/catalog`、`/data-metadata/search` 双双 redirect → `/data-asset/catalog?view=entity`。但这是菜单重设计 **M2-2A"目录三聚一"** 的既定结论（V2037 退役两菜单，2026-09-21 与负责人对齐、已落库验证），"能力并入资产目录·元数据实体视图"是设计目标不是事故。
清单把"边界需要重新划清/要么恢复独立入口"列为待治理，等于把已拍板的事重新挂起。建议改写为：**乱象降级为"待办"**——只剩一个收尾动作：资产目录实体视图里显式标注"数据由元数据模块提供"（05 自己的后一个选项），恢复独立入口若有人坚持，请作 D-6 翻案议题，不进治理候选队列。

---

## 核实通过项（证据在案）

- **R-1 孤儿页判定**：`pages/metrics/` 实测 7 个 tsx（与文中"7 个"一致）+ api/types/utils/less；全 src 无任何路由注册或 import 引用；`metrics/index.tsx:94` 吞错 catch（`catch {}` + "已由全局错误提示"注释）位置分毫不差。🔴 定级偏保守但删它零风险，同意治理候选第 1 位。
- **R-3 指标挂组**：metric 各页 menuGroup 归 data-analysis 系（上轮已核）。
- **R-4**：诚实标"未核实"（D-7），不装懂，通过。
- **R-5 "新建走真域"**：MetricEditModal 实测 domainId 用 semantic 域树（`toDomainTreeData`）、processId 独立字段且按域子树过滤——一词二义在新建路径上确实已拆，存量洗数仍等 D-2，与 01 M-6 口径一致。
- **R-6**：agent pom 实测唯一业务依赖 = `data-ops-business-dataset`，"只依赖 dataset"成立；176 文件数上轮已核。
- **R-8**：semantic 组标题实测"数仓标准体系"（parentGroupId=modeling）✓；血缘挂 `/data-analysis/lineage` ✓。顺带补一条同类：血缘的 **menuGroup 却是 data-asset**——路径前缀、菜单组、模块名三方各跨，是 R-3 同族问题，可并入 R-8 对照表。
- **#4 指标↔建模"common 层 SPI 解耦"**：实测 modeling 引 `io.yak.ops.common.api.metric.MetricQueryApi`，两模块互 import 为零——表述精确。
- **#2 标准引用"只读 SPI+编译期边界测试"**、**#10 Provider 投影对账**、**图一"唯一双向箭头对"=MOD↔MTR**、`主脉图.html` 存在——均核过。

## 结论

认可：登记册形式、R 系列发现质量、"只发现不擅治"纪律都成立，05 可以作为治理候选的决策输入。修掉上面 1/2/3（图二血缘主语、五层→四层预置、四类→五类审批），R-2 改挂到既有拍板记录下——04/05 即可升 v0.2 与 01/02 同批发出。
