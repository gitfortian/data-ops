# Data Security Requirements(按 ticket)

## 70 模块骨架 + 菜单

- 建立 `data-ops-business-security` 模块并接入 bom / business 聚合 / boot 依赖。
- V1 基线立历史表,V2 建 10 张 `yak_dsec_*`;共享 datasource 数据源池。
- 菜单组 `data-security`(sort 8)+ 6 页(总览/分级分类/权限/脱敏/审计/合规),全部 `data-security:read`,V2030 幂等注册并授予根角色。

## 71 安全等级

- `create`:编码正则 `^[A-Za-z0-9_]{1,32}$`,名称必填,rank≥1;项目内编码唯一;初始 DRAFT;审计 RESOURCE_CREATED。
- `update` 编码不可改;`changeStatus` DRAFT↔ACTIVE↔DISABLED(已生效/停用不可回退草稿,`INVALID_STATUS`)。
- `delete` 前校验是否被分级标签引用(`LEVEL_REFERENCED` 阻断)。
- 读:`get`(不存在 `LEVEL_NOT_FOUND`)/`findAllActive`(ACTIVE 按 rank 升序)/`page`/`countAll`。

## 72 数据分类

- `create`:编码合法且唯一;`parent_code` 指定时父级必须存在(`CATEGORY_PARENT_NOT_FOUND`)。
- `delete`:有子分类(`CATEGORY_HAS_CHILDREN`)或被分级标签引用(`CATEGORY_REFERENCED`)时阻断。
- 读:`get`/`findByCode`/`listAll`(树/下拉)/`page`。

## 73 资产分级标签

- `objectKey(type,dsId,db,table,column)` 规范化(`:` 分段,缺段 `-`)。
- `upsert`:objectType 与 levelId 必填;等级须存在(`CLASSIFICATION_INVALID_LEVEL`);分类若给须存在;按 objectKey 幂等。发现来源只能新建/更新 CANDIDATE,既有 ACTIVE 或 REJECTED 不被扫描覆盖。
- `changeStatus` 支持 CANDIDATE/ACTIVE/REJECTED;`delete`;`get`/`page`。
- SPI:`find`/`findMany`/`findByTable` → `ClassificationView`(带 levelCode/levelName/rank 与 category 码名)。

## 74 敏感发现

- 规则 `create/update`:matchType ∈ NAME/COMMENT/CONTENT/REGEX,REGEX pattern 须可编译(`DISCOVERY_INVALID_PATTERN`);编码唯一。CONTENT 是兼容名称,只匹配字段名/注释元数据,不扫描行数据。
- `matches(rule, field)` 纯函数:NAME 比列名、COMMENT 比注释、CONTENT 任一、REGEX 对列名/注释 find(大小写不敏感)。
- `scan(fields, operator)`:逐字段取最佳命中(rank 最高),经 ClassificationService 幂等落 CANDIDATE(带 source=DISCOVERED、confidence、discoveryRuleId),返回命中数;查询等级时限定当前项目。

## 75 访问策略与裁决

- 策略 `create`:subject/scope/action/effect 合法(否则 `ACCESS_INVALID_*`),初始 PENDING;DATASOURCE/DATABASE/TABLE/COLUMN 范围必须提交物理数据源 ID,并按库/表/列逐级校验;`decideApproval` 仅供 ACCESS_GRANT 流程终态回调使用;UI 仅能提交审批;`disable`/`delete`/`get`/`listApproved`/`page`/`countEnabled`。
- 裁决 `decide(actor, roles, objectKey, action)`:取项目内 APPROVED 且动作匹配策略 → DENY 优先 → 候选要求人工确认 → ALLOW → 默认(rank≥3 敏感转 NEED_APPROVAL 否则 ALLOW);READ 返回脱敏要求/算法编码但不写访问流水;仅消费方在访问执行后调用 `recordAccess` 记实际 outcome。

## 76 脱敏算法与策略

- `MaskingEngine.mask(value, algoCode, paramsJson)` 纯函数:FULL_MASK(≤6 掩码符)、NULLIFY(空)、REPLACE(整长)、KEEP_FORMAT(保非字母数字)、HASH(SHA-256 截断)、MASK_PARTIAL(留左右各 keepLeft/keepRight);只允许引擎支持的算法编码,非法参数和未知算法报错,绝不回退明文。算法目录不能创建不可执行的自定义编码。
- 算法 `createAlgorithm`/`deleteAlgorithm`(内置 `MASKING_ALGO_BUILTIN_READONLY` 阻断)/`getAlgorithm`/`listAlgorithms`。
- 策略 `createPolicy`(name 与 algoId 必填)/`deletePolicy`/`getPolicy`/`pagePolicies`/`countPolicies`。
- `resolve(objectKey)`:按 levelId/categoryId/columnPattern(`*` 通配)匹配 ACTIVE 策略,priority 最大者 → `MaskingDirective`;无命中返回 `none()`。

## 77 访问审计

- `record(...)`:只追加一行访问日志;`masked` 仅表示实际执行脱敏,访问试算不产生流水。
- 查询 `page(actor/decision/resourceKey/时间范围)`;统计 `countByDecision`/`countMasked`/`countSince`/`topActors`(group by 服务端聚合)。

## 78 合规体检

- 规则 `createRule`(ruleType ∈ RULE_TYPES 否则 `COMPLIANCE_INVALID_RULE_TYPE`,编码唯一)/`deleteRule`/`getRule`/`pageRules`。
- `run(ruleId, operator)`:生成 batchId,按类型评估(SENSITIVE_MUST_MASKED / SENSITIVE_MUST_CONFIRM / CLASSIFY_COVERAGE),逐资产落 Finding(passed/severity/detail),返回 `ComplianceRunResult(checked,passed,failed)`。
- `pageFindings(batchId/passed)`/`latestSummary`(总览用)。每次规则体检至少写入一条通过或失败结果;没有可识别历史批次时汇总返回 UNKNOWN/null,页面不显示为“暂无风险”。

## 79 总览聚合

- `SecurityOverviewService.overview()`:拼装等级数、分类数、已分级总数/ACTIVE/CANDIDATE、等级分布(LevelCount)、启用策略数、脱敏策略数、拒绝/脱敏计数、热点主体、合规汇总;只读、独立容错("查不到"≠异常)。

## Asset Security Section

- Security 作为事实 Owner 实现只读 `SectionProvider`，通过既有 `SecurityClassificationQueryApi` 查询 Asset source identity 对应的分级证据；物理表按 database/table 坐标读取表及列级记录。
- 输出当前活动分级证据、匹配对象坐标或分级的已审批 READ 规则数，以及逐个分级对象是否匹配启用且算法可用的脱敏策略。READ 规则数是策略配置摘要，不计算主体、拒绝优先级等最终访问裁决。
- 合规检查结论按批次归属；本分区明确返回对象级合规状态 `UNAVAILABLE`，不得将项目或批次结果解释为单对象合规结论。
- 缺少准确对象坐标或摘要读失败时，访问策略摘要标记 `UNAVAILABLE`；脱敏摘要独立标记状态。未发现分级记录返回 `EMPTY`；分类读侧失败返回 `UNAVAILABLE`。输出保留来源与分类生命周期状态限制。
- 该摘要是治理证据，不代表访问许可，也不把 Asset 快照提升为 Security Truth。
ACCESS_GRANT completion must compare the entire editable policy snapshot while holding a row lock;
stale submissions fail with `ACCESS_APPROVAL_SNAPSHOT_STALE` and remain retryable after resubmission.
