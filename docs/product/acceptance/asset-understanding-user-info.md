# Asset Understanding 验收案例：用户信息表

- **文档类型：** 验收证据（Golden Asset 候选）
- **状态：** DRAFT — 样本事实已核实；产品行为验收尚未执行
- **事实快照：** 2026-09-23，只读核对默认项目空间 `project_id=1`
- **关联契约：** [PD-001 — Asset Governance Hub](../decisions/PD-001-asset-governance-hub.md)、[F-001-A — Asset Section Contract](../features/F-001-A-asset-section-contract.md)

本案例记录一个真实资产及待验证的用户问题，作为产品验收证据，不自动成为 Product Truth、Feature 实施指令或新的 API / Contract 设计。文中的字段比较是候选验收问题；执行前必须遵守下方记录的现行契约适用性。

## 样本身份

| 项目 | 已核实事实 | 来源 |
| --- | --- | --- |
| 项目空间 | 默认空间，`project_id=1` | 只读项目数据查询 |
| 治理资产 | ID `8`，`用户信息表`，`asset_key=modeling:model:49`，类型 `TABLE`，来源类型 `MODEL`，状态 `PENDING` | Asset Registry `yak_asset_item` |
| Asset owner | `root` | Asset Registry `yak_asset_item.owner` |
| 模型资产 | Model ID `49`，模型名 `用户信息表`，模型表名 `ods_cust_info` | Modeling `yak_modeling_model` |
| 来源映射 | 数据源 ID `3`，`crm_db.crm_customer` | Model `source_datasource_id` / `source_database` / `source_table` |
| 模型字段 | 14 个 | Modeling `yak_modeling_model_column` |
| 采集元数据 | 14 条：1 条表记录及 13 个不同字段记录；最近采集时间为 `2026-09-21` | Metadata `yak_metadata_asset`，项目空间、数据源、库和表均与上述来源映射匹配 |
| Metadata owner | 未提供（表及字段记录的 `owner_user` 为空） | Metadata `yak_metadata_asset.owner_user` |

未查询物理来源表的业务行；本案例只使用 Asset、模型结构和已采集的元数据事实。

## 现行契约适用性

该治理资产的 `source_type` 是 `MODEL`。F-001-A §14「场景 B：Model」规定 Model Asset 的 Technical Metadata 和 Quality 为 `NOT_APPLICABLE`。因此：

- 下列模型结构与采集字段的比较是待验证的跨域用户问题，不能据此要求当前 Technical Metadata Section 在 Model Asset 上返回物理元数据。
- Q1 的身份链可用于检查 Asset、Model 与模型来源映射是否能被用户理解；各事实仍由各自拥有域负责。
- 在产品契约没有明确调整前，当前 Model Asset 的 Technical Metadata 预期仍为 `NOT_APPLICABLE`。
- 本案例不新增 Section 状态。F-001-A 当前定义 `OK`、`EMPTY`、`NOT_APPLICABLE`、`UNAVAILABLE`、`PERMISSION_DENIED`，没有 `MISSING`。单个 owner 未提供应作为字段级未知事实呈现并保留来源，不把它扩展成新的 Section 状态。

## 用户问题与预期证据

### Q1：这个资产是什么？

**预期可核验的回答：**

```text
治理资产：用户信息表（Asset 8）
模型资产：用户信息表（Model 49），模型表名 ods_cust_info
来源映射：数据源 3 / crm_db.crm_customer
```

**证据来源：** Asset Registry identity、Model 元数据、Model source mapping。回答应区分模型表名与来源表名，不把映射写成同一张物理表。

### Q2：模型字段与采集字段是否对应？

**观测结果：**

```yaml
model_fields: 14
metadata_fields: 13
matched_fields: 12
model_fields_without_metadata:
  - process_time
  - event_time
metadata_fields_without_model_match:
  - etl_time
```

这是字段名对应关系的比较，不是数据完整性、质量或可信度评分。验收必须能指出比较双方及差异，不能把结果改写成“字段完整率”而隐藏额外字段或口径。

**证据来源：** Model Column 列表、Metadata 已采集 Column 列表、两份列表的确定性比较结果。

**契约状态：** 候选跨域问题；受上方 Model Asset 的 Technical Metadata 适用性规则约束，当前尚未验收为 Section 行为。

### Q3：谁负责？

**预期可核验的回答：**

```yaml
asset_owner:
  value: root
  source: Asset Registry
metadata_owner:
  value: unknown
  source: Metadata collector record
```

`root` 是 Asset Registry 的治理联系人事实；它不能覆盖 Metadata 记录中 owner 为空的事实，也不能被描述成 Metadata owner。

### Q4：有哪些已知风险线索？

模型字段注释包含手机号、身份证待脱敏，以及性别、注册来源、会员等级、状态等枚举不统一的描述；表注释指出数据存在脏数据、逻辑外键无物理约束。

验收回答必须把这些内容归因于模型/元数据注释，并与检测结论分开。没有运行安全策略检查或质量规则时，不得声称“未检测到脱敏策略”“存在已确认质量问题”或给出置信度分数。

## 缺失、空结果与依赖失败

- Metadata owner 为空时，owner 值应明确为未知，并保留 Asset owner 与 Metadata owner 的不同来源；这不是 Section 级 `MISSING` 状态。
- 对适用的 Section，只有在查询成功并确认没有记录时才使用 `EMPTY`；查询依赖失败时使用 `UNAVAILABLE`，说明原因，并让其它 Section 继续可用。
- 对本案例当前的 Model Asset，Technical Metadata 遵守 F-001-A 的 `NOT_APPLICABLE` 约定。若将来要在此资产展示来源物理元数据，先完成产品契约的适用性决策，再把该行为纳入验收。

## 验收证据与未完成项

1. 静态事实已核对：Asset、Model、source mapping、字段列表及 Metadata 采集记录均限定在 `project_id=1`。
2. 需要通过已授权的产品路径验证 Q1、Q3、Q4 如何展示，并确认每项结论能回溯到对应事实来源。
3. Q2 当前作为 Contract Gap Analysis 输入；在 F-001-A 的 Model 适用性规则未解决前，不将其判作 Technical Metadata Section 的通过条件。
4. 需分别验证适用 Section 的确认空结果与依赖不可用行为；不能用同一个空响应代表两者。
5. 端到端产品验收尚未执行，因此本案例当前不宣称通过，也不作为关闭 Issue #11 或其关联 Issue 的证据。

## D01 执行记录（2026-09-23）

**状态：部分完成；真实登录态验收被运行环境阻塞。** 本次只做只读核验，没有修改业务数据，也没有将 Q2 契约差距记为当前失败。

### 基线与环境

- 执行前 worktree 为 `31fb9ed68fc76b18998f9268eb3672d4f31b042a`，干净；按 monitor 要求切换到本地 `data-ops/main`（`git switch --detach data-ops/main`）。验收基线为 `84f979a2b48068dbe6807468635a11f808591404`，对应 `data-ops/main`，包含 #37/#38/#39/#40。
- `docker compose ps` 无法运行：环境没有 `docker` 命令。端口检查显示只有本地 MySQL 的 3306 在监听，前端 9001、后端 9527 均无监听；访问 `http://localhost:9001` 超时。
- MySQL 客户端位于 `C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe`。示例数据库凭据连接本机 MySQL 被拒绝；使用本机已有只读核验凭据成功执行下列查询。凭据不记录于文档，仅用于只读核验，不代表 DataOps 应用登录态。
- 本次查询没有读取 `crm_db.crm_customer` 物理业务行，只访问 `yak_security` 中 Asset、Model、Metadata 和 Security 用户/菜单记录。

### 样本事实与 Q1/Q3/Q4 证据

以下命令均在仓库根目录的 PowerShell 中执行，密码通过进程环境传给客户端；不会输出密码：

```powershell
$env:MYSQL_PWD='<本地只读凭据占位符>'
$mysql='C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
& $mysql --protocol=TCP --host=localhost --user=root --database=yak_security --default-character-set=utf8mb4 --batch --skip-column-names --execute="SELECT id,project_id,asset_key,source_type,source_id,asset_type,name,owner,status FROM yak_asset_item WHERE id=8; SELECT id,project_id,model_name,table_name,source_datasource_id,source_database,source_table FROM yak_modeling_model WHERE id=49; SELECT COUNT(*) FROM yak_modeling_model_column WHERE model_id=49; SELECT id,asset_type,name,source_id,data_source_id,database_name,table_name,column_name,owner_user,update_time FROM yak_metadata_asset WHERE project_id=1 AND data_source_id='3' AND database_name='crm_db' AND table_name='crm_customer' ORDER BY id; SELECT column_name,column_comment FROM yak_modeling_model_column WHERE model_id=49 AND column_comment<>'' ORDER BY id;"
```

- **Q1 身份链（数据库证据已通过，UI 未验）：** Asset 记录为 `8 / 用户信息表 / modeling:model:49 / source_type=MODEL / source_id=49 / type=TABLE / owner=root / PENDING / project_id=1`；Model 记录为 `49 / 用户信息表 / ods_cust_info / datasource=3 / crm_db.crm_customer / project_id=1`。`ods_cust_info` 是模型表名；`crm_db.crm_customer` 是来源映射，不应合并表述为同一表。前端 detail 代码取 Asset 详情后加载分区，来源属性路径受 `modeling:read` 权限控制（`AssetDiscoverService.detail`、`hasSourceAttrsPermission`）。由于没有运行应用，本次没有用户可见页面/API 响应截图。
- **Q3 责任人（数据库证据已通过，UI 未验）：** Asset Registry 的 `owner=root` 是治理联系人；Metadata TABLE 记录 ID 338 与对应 13 条 COLUMN 记录的 `owner_user` 均为 SQL `NULL`。应呈现为“Metadata owner 未知/未提供”，来源为 Metadata 采集记录；不得把 Asset owner `root` 解释为 Metadata owner。SQL 汇总为 13 条 COLUMN，另有 1 条 TABLE；采集更新时间为 `2026-09-21 10:14:27.992554`（TABLE）及约 `10:14:28.031967`（COLUMN）。
- **Q4 风险线索（静态来源事实已核验，UI/规则扫描未验）：** Model Column 注释中存在 `cust_mobile`、`cust_idcard` 待脱敏提示及 gender/birthday/reg_time/reg_source/member_level/status 等格式或枚举不统一描述；Q1 查询中的 `column_name,column_comment` 返回这些注释。它们是模型注释线索，不是已执行安全策略扫描或质量规则的结论。未运行源表行查询，未据此认定实际泄漏或已确认质量问题，也未生成风险置信分数。

### 分区状态、权限、回链与契约差距

- **`TECHNICAL_METADATA`：** 样本 `source_type=MODEL`。F-001-A §14 场景 B 明确预期 `NOT_APPLICABLE`；实现 `AssetDiscoverService.section` 对非物理表返回 `NOT_APPLICABLE`。该结论来自契约与代码检查，未通过运行 API/UI 验收。候选字段差异仍是 Q2 契约差距输入，不是当前失败。
- **五态语义：** APPROVED F-001-A 与 `AssetSection` 类型列出 `OK`、`EMPTY`、`NOT_APPLICABLE`、`UNAVAILABLE`、`PERMISSION_DENIED`。控制器测试覆盖 `PERMISSION_DENIED` 不泄漏 summary/provenance/evidence/actions、`NOT_APPLICABLE` 不声明读取来源、适用且成功查空的 `EMPTY` 保留查询 provenance/reason；源码中分区异常映射为有原因的 `UNAVAILABLE`，成功有数据使用 `OK`。这些是静态代码/已有单测证据，本次没有重跑测试，也没有注入权限不足、空结果或依赖故障做登录态验证。
- **权限与回链：** API Controller 类级要求 `data-asset:read`；技术元数据另检查 `data-metadata:read`，Q1 Model 来源属性要求 `modeling:read`。存在 root 用户记录且 `data-asset` 菜单注册记录在本地 Security DB，但没有登录/会话或权限实际判定证据。成功的 Technical Metadata/Lineage 分区动作带 `returnAssetId=8` 回链上下文；当前 Model 的 Technical Metadata 为 `NOT_APPLICABLE`，因此该 Technical Metadata 回链动作预期不出现。
- **Q2：** 仅记录现有候选对照（Model 14 列、Metadata 13 列、候选匹配 12 列）；由于 Model 的 Technical Metadata 当前不适用，不对 Section 行为判失败，也不修改契约。

### 可复现阻塞与完成边界

登录态页面路径、六个独立 Section 的真实响应、页面截图、跨域权限拒绝、适用 Section 的实际 `EMPTY/UNAVAILABLE` 响应均未完成。要继续执行，需要启动与 `yak_security` 同一产品数据集连接的 DataOps 前后端（前端 9001、后端 9527），并提供可登录账号及其 Asset/Model/Metadata read 权限；仅数据库账户不能替代应用用户登录。现有 worktree 不含 Docker，也没有 `yak-ops-ui/node_modules`，且未发现运行中应用。本记录因此不宣称 D01 通过。

## 明确不在本案例范围内

- 新增通用 Contract 字段、Section 状态或 Provider；
- 定义 Asset Understanding 聚合 API 或 Reasoning API；
- 生成 confidence 数值、质量结论或安全策略结论；
- 读取来源表业务行或修改默认项目空间数据。

## 页面测试移交清单

**执行状态：以下项目全部未执行，交由后续执行者在可用的验收环境中手动验证。** 不得据此将页面验收标记为通过。除特别说明外，样本均为默认项目空间 `project_id=1` 的 Asset 8（Model 49，来源映射 `crm_db.crm_customer`）。执行者应使用真实应用账号登录；不要将数据库登录当作产品登录。

| 优先级 / 场景 | 前置条件与步骤 | 预期结果 | 需采集证据 | 状态 |
| --- | --- | --- | --- | --- |
| P0 — Asset 8 登录入口与 Q1 身份链 | 前置：前后端可用；账号有 `data-asset:read` 和 `modeling:read`。登录后从“数据资产”进入目录，打开 Asset 8。 | 可到达详情；看见治理资产 `用户信息表`、Model 49、模型表名 `ods_cust_info` 及来源映射 `crm_db.crm_customer`，并清楚区分模型表名和来源表。 | 登录后入口与详情截图；页面 URL；脱敏后的 Network 请求/响应；当前用户与权限名称。 | 未执行 |
| P0 — Q3 Asset owner 与 Metadata owner 未知 | 前置：可查看 Asset 8 及 Metadata owner 字段。打开详情的治理/元数据相关展示。 | `root` 仅标为 Asset 治理联系人；Metadata owner 明确显示未知/未提供，来源归于 Metadata 采集记录；不能把两者合并。 | 展示截图；对应 Section 响应中的 owner、ownerDomain/provenance/evidence 与 reason 字段（若有）。 | 未执行 |
| P0 — Q4 注释风险线索 | 前置：可查看 Model 来源属性。检查字段注释。 | 手机号、身份证待脱敏及枚举/格式不统一内容以模型或元数据注释线索呈现；不表述为已执行的安全扫描、质量判定或实际泄漏结论。 | 字段注释截图；对应只读响应；记录展示来源和措辞。 | 未执行 |
| P0 — MODEL Technical Metadata 不适用 | 前置：Asset 8；有 `data-asset:read`。打开“技术元数据”分区。 | Section 显示 `NOT_APPLICABLE` 和可理解说明；不伪装成 `EMPTY` 或查询故障，不要求呈现物理 Metadata 字段；Q2 差异不计为当前失败。 | 页面截图；`GET /api/v1/assets/8/sections/TECHNICAL_METADATA` 响应；status/reason/capability/provenance 字段。 | 未执行 |
| P1 — 五态呈现与差异 | 前置：准备每种状态的受控样本/依赖条件；不得修改 Asset 8 的产品事实来凑状态。分别选取可证实 `OK`、成功查空 `EMPTY`、适用性不符 `NOT_APPLICABLE`、依赖故障 `UNAVAILABLE`、已登录但无事实读取权限 `PERMISSION_DENIED` 的 Section/资产。 | 每种状态与契约语义一致；只有成功确认无记录时显示 `EMPTY`；故障不得伪装为空；未适用不得伪装为空；权限拒绝不得泄漏事实。 | 每个案例的资产/Section、前置条件、页面截图、HTTP 状态及脱敏响应、用户权限；故障恢复后结果。 | 未执行 |
| P0 — 权限拒绝与数据隔离 | 前置：使用已登录但缺少 `data-asset:read` 的账号尝试 Asset 详情；另用有资产读取但缺少目标事实域权限的账号查看受保护 Section（如 `data-metadata:read`）。 | 无 Asset 权限时入口/接口受控；无事实域权限时显示 `PERMISSION_DENIED` 或契约定义的拒绝行为；不返回 summary、provenance、evidence 或回链动作等受限事实。 | 账号权限清单；菜单/页面表现；脱敏 HTTP 响应与状态；确认敏感字段未出现在响应中。 | 未执行 |
| P1 — 专业域回链 | 前置：选用 Technical Metadata 适用且 Section 为 `OK` 的物理表样本，以及有 Lineage 回链的样本；Asset 8 的 Technical Metadata 按契约不适用，不作为该回链测试对象。点击 Section 提供的专业域动作，再使用返回上下文回到 Asset。 | 跳转到正确专业域对象；回链保留原 Asset identity（例如 `returnAssetId` 或 `assetKey`）；不适用/无权/失败状态不显示虚假成功动作。 | 点击前后截图与 URL；动作目标、源 ID、返回参数；返回后 Asset 身份和页面状态。 | 未执行 |
| P1 — 单 Section 故障隔离 | 前置：选一个适用且可显示的 Asset；在测试环境只令一个 Section 依赖超时/失败，再打开详情并观察其他 Section。 | 故障分区显示有原因的 `UNAVAILABLE`；其余可用分区仍加载和展示；不把故障当 `EMPTY`，恢复依赖后可重新加载。 | 故障注入条件/时间；各 Section 脱敏请求响应与时序；故障前、故障中、恢复后截图或记录。 | 未执行 |

移交执行者需在每项执行后补录执行人、时间、环境/构建基线、实际结果与证据位置；发生失败时保留脱敏截图或响应，并区分产品失败、环境阻塞和契约差距。Q2 仍仅记录为待产品契约处理的差距，不作为本清单的当前失败判据。
