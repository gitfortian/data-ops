# Data Security Requirements(按 ticket)

## 70 模块骨架 + 菜单

- 建立 `yak-ops-business-security` 模块并接入 bom / business 聚合 / boot 依赖。
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
- `upsert`:objectType 与 levelId 必填;等级须存在(`CLASSIFICATION_INVALID_LEVEL`);分类若给须存在;按 objectKey 幂等(有则更新、无则新增),审计 CREATED/UPDATED。
- `changeStatus` 候选确认(ACTIVE)/停用;`delete`;`get`/`page`。
- SPI:`find`/`findMany`/`findByTable` → `ClassificationView`(带 levelCode/levelName/rank 与 category 码名)。

## 74 敏感发现

- 规则 `create/update`:matchType ∈ NAME/COMMENT/CONTENT/REGEX,REGEX pattern 须可编译(`DISCOVERY_INVALID_PATTERN`);编码唯一。
- `matches(rule, field)` 纯函数:NAME 比列名、COMMENT 比注释、CONTENT 任一、REGEX 对列名/注释 find(大小写不敏感)。
- `scan(fields, operator)`:逐字段取最佳命中(rank 最高),经 ClassificationService 幂等落 CANDIDATE(带 source=DISCOVERY、confidence、discoveryRuleId),返回命中数。

## 75 访问策略与裁决

- 策略 `create`:subject/scope/action/effect 合法(否则 `ACCESS_INVALID_*`),初始 PENDING;`decideApproval` 仅对 PENDING(`ACCESS_NOT_PENDING`),approve→APPROVED/驳回→REJECTED,留审批人/时间/原因;`disable`/`delete`/`get`/`listApproved`/`page`/`countEnabled`。
- 裁决 `decide(actor, roles, objectKey, action)`:取项目内 APPROVED 且动作匹配策略 → DENY 优先 → ALLOW → 默认(rank≥3 敏感转 NEED_APPROVAL 否则 ALLOW);放行且 READ → `SecurityMaskingApi.resolve` 得脱敏指令;写访问日志;返回 `AccessDecision`。

## 76 脱敏算法与策略

- `MaskingEngine.mask(value, algoCode, paramsJson)` 纯函数:FULL_MASK(≤6 掩码符)、NULLIFY(空)、REPLACE(整长)、KEEP_FORMAT(保非字母数字)、HASH(SHA-256 截断)、MASK_PARTIAL(留左右各 keepLeft/keepRight);参数解析异常回退默认;未知算法/空算法原样返回。
- 算法 `createAlgorithm`/`deleteAlgorithm`(内置 `MASKING_ALGO_BUILTIN_READONLY` 阻断)/`getAlgorithm`/`listAlgorithms`。
- 策略 `createPolicy`(name 与 algoId 必填)/`deletePolicy`/`getPolicy`/`pagePolicies`/`countPolicies`。
- `resolve(objectKey)`:按 levelId/categoryId/columnPattern(`*` 通配)匹配 ACTIVE 策略,priority 最大者 → `MaskingDirective`;无命中返回 `none()`。

## 77 访问审计

- `record(...)`:只追加一行访问日志。
- 查询 `page(actor/decision/resourceKey/时间范围)`;统计 `countByDecision`/`countMasked`/`countSince`/`topActors`(group by 服务端聚合)。

## 78 合规体检

- 规则 `createRule`(ruleType ∈ RULE_TYPES 否则 `COMPLIANCE_INVALID_RULE_TYPE`,编码唯一)/`deleteRule`/`getRule`/`pageRules`。
- `run(ruleId, operator)`:生成 batchId,按类型评估(SENSITIVE_MUST_MASKED / SENSITIVE_MUST_CONFIRM / CLASSIFY_COVERAGE),逐资产落 Finding(passed/severity/detail),返回 `ComplianceRunResult(checked,passed,failed)`。
- `pageFindings(batchId/passed)`/`latestSummary`(总览用)。

## 79 总览聚合

- `SecurityOverviewService.overview()`:拼装等级数、分类数、已分级总数/ACTIVE/CANDIDATE、等级分布(LevelCount)、启用策略数、脱敏策略数、拒绝/脱敏计数、热点主体、合规汇总;只读、独立容错("查不到"≠异常)。
