# 01: SECURITY 标准与安全模块打通（安全等级词表归一裁决）

**对应需求:** 语义中心盘点 §6 缺失能力（P1）| 阶段: P1

**What to build:** 现状是两套"安全等级"词表并行:语义 SECURITY 标准(`yak_semantic_standard` kind=SECURITY,列 level_code/mask_rule)与安全模块自建 `yak_dsec_security_level`/`yak_dsec_masking_*` 十表体系,且 security 对 semantic **零 Java 引用**(pom 里 optional 依赖是空挂)。后果:建模列标了安全标准,数据安全页脱敏策略不认,S10"一词一义"在安全等级上未成立。本 ticket 先做**方向裁决**,再落地。

**模块归属:** **跨模块**——semantic(标准侧) + security(dsec 侧)

**Blocked by:** 无(可与 02~08 并行)

**Status:** done(已裁决并落地 2026-09-22;真机复验待用户重启后端)

## 裁决结论（B+：非整源合并，修好半截桥）

读码核实二者**不是同一事物**，原方案 A 前提不成立：
- `yak_dsec_security_level` = **项目级等级字典**（rank_no 定序、无预置种子、分类/发现/脱敏/访问四类策略按 `level_id` 消费）——真源归 dsec；
- 语义 SECURITY 标准 = **字段级分级/脱敏模板**（预置 25 条按敏感字段类别，level_code 仅 L2/L3/L4）——真源归 semantic；
- 且桥已存在半截：dsec 表预留 `std_security_id` 列+后端透传落库，分级页"关联数据标准"下拉**错绑 dsec 自身等级列表**（classification/index.tsx 原 L240）。

裁决 = 双层真源各归其位 + 把 std_security_id 桥接真、接对、校验住；mask_rule 定性为参考文案（脱敏执行口径唯一在 dsec 脱敏算法）。结论已落 05 清单 **R-9** + 治理候选表第 8 行。

## 落地记录（2026-09-22）

- 后端：`SecurityErrorCode.LEVEL_INVALID_STD_SECURITY(45016)`；`SecurityLevelService` 注入 `StandardQueryApi`，create/update 均校验引用（存在+SECURITY+ENABLED，仿 `MdmAttributeService.validateKindRef`）；update 改显式 LambdaUpdateWrapper set，使 std_security_id/描述**可清空**（原 MP 实体 patch 忽略 null）。依赖方向不新增：pom 早声明 optional semantic，接线方式与 mdm 同款。
- 前端：分级页等级表单"关联数据标准"下拉改接 `getStandardOptions(['SECURITY'])`（语义启用标准），等级表格新增"关联安全标准"列（解析名称，未知 id 显示 `#id`）；数据标准编辑弹窗"脱敏规则"加 tooltip 注记参考文案不执行。
- 语义侧：`StandardKind.SECURITY` javadoc 注记双层分工（不改行为）。
- 文档：04 图 SEC 边拆线改注；05 清单立 R-9；security/semantic 两侧 DEPENDENCIES.md 同步（semantic 入向表补 security 行）。
- 验证：`SecurityLevelServiceTest` 10/10 绿（新增缺失引用/错类别或停用/启用通过 3 例，绕开并发会话在途的 datasource 编译坏文件，单模块离线跑）；UI `tsc --noEmit` 182（基线 183~199 内，触改文件零错误）。真机链路（下拉可选/保存/回显/清空）需用户重启后端后浏览器复验。
- 遗留（登记在 R-9，另单处理）：`StandardRecommendationService` 按 level_code 下划线分段匹配与 L2/L3/L4 现实错位；`StandardQueryApi.get` 无项目作用域过滤（跨项目可查）。

## 验收清单

- [x] 裁决 A/B 并把结论写入 `docs/v1/05-模块交互与乱象清单.md`（立 R-9，盘点 §7.6 同步销账）
- [x] 建立引用并校验：dsec `std_security_id` 经 StandardQueryApi 校验；分级页下拉接对源+表格回显（无存量需 backfill——该列此前无合法值来源）
- [x] mask_rule 定性结论：参考文案，不删列（V1 不可改），`StandardKind` javadoc+前端 tooltip 双注记
- [x] 04 图 `SEM-.->SEC` 边标签与最终现实一致
- [x] 单测/类型检查通过；契约（松散 ID、只经 api 包、禁直读表）保持
