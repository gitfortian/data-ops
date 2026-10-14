# J1｜数据源 → ODS 完整工作台：单 PR 可手动验收交付

日期：2026-10-14。用户要求：**一个 PR 交付一个能拉最新代码、跑项目并自主验证的完整用户工作流**。不能再以小组件/按钮/单测作为“完成了原型重构”的证据。产品依据：#513 全局用户故事地图、#539 J1 黄金故事、#550 v0.8.2 定稿候选。此文档是交付清单和人工验收手册，不代替真实浏览器验收记录。

## 1. 本 PR 的可见整体改造

| 用户工作场景 | 此 PR 正式页面 | 原有后台事实和不能误报的状态 |
|---|---|---|
| 从列表进入一个数据源 | `DataSourceWorkspace` 全宽工作台：左侧 J1 任务阶段、中间连接/实时源表探查、右侧下一步和采集提醒 | DataSource Owner；最近一次连通不等于这次采集、结构或 ODS 保存 |
| 选择库、Schema、表，核对字段 | 既有 `LiveSchemaExplorer` 直接嵌入中间主区，保留搜索、分页、Schema 切换、字段/主键/注释及失败恢复 | DataSource Owner 实时 catalog；MetaData Run 不是本次实时物理列 |
| 打开或创建 ODS | 明确带精确 sourceId、database、schema、table 的导航，ODS 工作台左边固定显示这些坐标 | Modeling Owner 重查 datasource、物理来源与同源 ODS 候选 |
| 发现现有 ODS | 工作台主区按真实候选列表展示准确模型 ID、状态、打开入口 | **不能用同名表代替精确 Schema**，不覆盖原 ODS |
| 无现有 ODS | 中间分“读取真实源列/逐列映射/目标模型配置”，右侧固定下一步按钮：预览→保存空草稿→原子提交结构与映射→成功回源 | `getOdsSourceCandidates`, `previewModelingImportColumns`, `createModelingModel`, `commitOdsManualImport` 原 API；服务端是唯一事实 |
| 异常、重复点击、返回来源 | 页面显示真实错误及重新核验；持久空草稿有可恢复 ID；未提交映射离开前确认；成功回源并再次核验表 | 无 Asset Write 仍可有权建 ODS；不执行 SQL/DDL；超时不能推断失败或自动重建 |

原有 DataSource CRUD、连接测试、Metadata 任务复用和已有采集结果保留，不因单工作台改版删除专业操作。

## 2. 手工验证（请在拉取并运行本 PR 后进行）

**准备条件：** 当前项目已连接一个可查库、Schema、表列的 MySQL；账号有 `resource:data-source:read`, `modeling:read/create/update`，没有 Asset Write 也允许。示例可使用截图的“产品库 / mkt_db / mkt_coupon”，也可使用 `trade_db / public / trade_order_detail`；数据库和 Schema 按实际返回值核对，**不要人为补充缺失的 Schema**。

1. 进入 **数据源**列表，打开真实的“产品库”。应看见横向铺满的 J1 来源工作台（不是原 880px 窄抽屉），左边 4 步、中间源结构、右边下一步说明；连接状态可见且依然能点击“检测连接”“编辑连接”。
2. 切换数据库 `mkt_db`、实际 Schema，搜索并选中 `mkt_coupon`；应看见字段、类型、主键、备注。重新探查和字段刷新可用；未核验字段时“打开或创建 ODS”不得可执行。
3. 点击 **打开或创建 ODS**。进入完整 ODS 工作台，左边固定源身份、中间本次目标与字段映射、右边当前唯一主要动作，顶部四步进度；返回来源动作可用但未保存映射会给出取消/确认。
4. **如果已有精确同源 ODS**：出现候选卡片（真实 `modelId/code/status`），优先打开，不额外创建第二张 ODS；可在模型详情保存后回到原数据源。
5. **如果没有候选**：点 **读取当前物理列并核对**，逐列看到源列→目标列、原主键、类型/长度/可空；修改一个目标字段名，检查不合法或重复名称被拒绝提交。
6. 填写 ODS 目标名称、编码、方言，点 **保存可恢复的空 ODS 草稿**。必须显示 Modeling Owner 真实草稿 ID；不误报成完整 ODS。
7. 点 **确认并原子保存结构与映射**。必须收到 `SAVED_AND_VERIFIED`、模型 ID 与映射数核验；看到成功态、**保存并返回来源**、**打开 ODS** 两个不同去向。返回应打开同一 `sourceId/database/schema/table`，源表重新读取核验。
8. 刷新已保存 ODS；核对字段和来源映射存在。返回源表后再次点击打开 ODS，应命中现有精确候选，不能重复创建空草稿。

### 故障与角色负例（至少做一遍）

- **同名不同 Schema：** 指定 Schema 的 ODS 不能误指向另一个 Schema 的同名表；已存在候选必须按精确来源匹配。
- **撤权/跨项目：** 切换另一个 Project 或撤销 source read/Model create 时，拒绝继续并给出权限/来源核验反馈；不能泄露原项目结构。
- **网络故障：** 源库读取、OTS 候选查询或原子提交超时后不得显示成功，提示核验已有模型；重试要先核对是否已有持久 ID。
- **无 Asset Write：** 仍可走已授权的源表与 ODS 路径；资产纳管是并行任务。
- **320/390/768/1024/1366/1440 视口：** 侧栏依次折叠，内容区可滚动，不遮挡主要 CTA，不出现水平页面溢出（字段映射表允许其内部横向滚动）；键盘 Tab 和按钮聚焦可见。

## 3. 自动化和真实验收区别

本 PR 新增 **真实 UI 结构断言**和 **mock 实际接口的完整模型保存回执回归**，原有 DataSource/Metadata/Modeling 服务测试继续执行。必须以最终 HEAD 对应的 Product Guard/Architecture/前端 Jest/TypeScript/构建工作流结论报告；**仅 Jest 或静态类型绿灯不等于真人已在部署环境走通**。

视觉方向借鉴 v0.8.2 的工作台分区、业务阶段与主次动作，保留当前正式 React / Ant Design 组件。人工验收应对照 [v0.8.2 最终候选](https://github.com/gitfortian/data-ops/blob/docs/ia550-two-domain-workbench-design-20261013/docs/product/experience/prototypes/IA-550-task-flows-v0.8.2-final-candidate.html)。若真实视觉或功能仍不能接受，**继续在本 PR 内整改直到可签收；不再以“下一轮 PR”逃避本工作台的缺口**。

明确本轮**不包含**：DWD/DIM 的正式逻辑→物理映射持久合同（#556 DR-06）、DataDev TaskRevision 创建合同（#557 DR-07）、自动上线/执行、源表全量 ETL 时间列生成合同；这些不是来源→ODS 交付的隐含动作。
