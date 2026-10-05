# Data Security Domain

## 核心概念

### 安全等级(Security Level)—— 分级地基

敏感度的**有序**字典(如 L1 公开 → L4 核心),`rank_no` 越大越敏感。项目内 `level_code` 唯一、创建后不可改;`status` ∈ DRAFT/ACTIVE/DISABLED。可引用 semantic 的 SECURITY 标准(`std_security_id`,松散 ID)。**下游一切判断(脱敏、裁决、合规)都以等级 rank 为准,不各自定义敏感度**。

### 数据分类(Data Category)—— 业务归类树

按业务域归类的树(客户/健康/财务/位置…),`category_code` 项目内唯一,`parent_code` 指向父级(空=根)。等级 × 分类共同决定一个资产标签,分类只描述"是什么",不携带敏感度。

### 资产分级标签(Classification)—— 对象↔等级/分类

把某个数据对象(列/表)绑定到等级与分类,是模块的核心事实表。物理列自然键为 `COLUMN:datasourceId:database:table:column`(缺段以 `-` 占位,规范化后可幂等 upsert)。`status` ∈ CANDIDATE(机器发现待确认)/ACTIVE(人工确认)/REJECTED(人工驳回)。`source` 使用 MANUAL/DISCOVERED,`confidence` 0~100,`discovery_rule_id` 回溯命中规则。自动发现只能新建或更新 CANDIDATE,不得覆盖 ACTIVE 或 REJECTED。

### 敏感发现(Discovery Rule)

从数据源目录字段元数据识别候选标签的规则:`match_type` ∈ NAME/COMMENT/CONTENT/REGEX,`pattern` 匹配列名/注释(REGEX 需可编译)。CONTENT 是历史名称,实际也只匹配列名或注释,不读取数据行。扫描 `scan(fields)` 命中后经 ClassificationService 幂等落 CANDIDATE,多规则命中取等级 rank 最高者为最佳匹配。发现只产生候选,不自动生效。

### 访问策略与裁决(Access Policy / Decision)

- 策略:主体(USER/ROLE)对范围(DATASOURCE/DATABASE/TABLE/COLUMN/LEVEL/ALL)的动作(READ/EXPORT/WRITE),`effect` 仅 ALLOW/DENY;新建即 PENDING,必须提交既有 ACCESS_GRANT 审批流程,批准后 APPROVED 才参与裁决;DENY 优先。DATABASE/TABLE/COLUMN 必须绑定 datasourceId。
- 裁决 `decide(actor, roles, objectKey, action)`:按项目+动作取 APPROVED 策略 → DENY 短路 → CANDIDATE 需人工确认 → 命中 ALLOW → 否则默认(已确认敏感 rank≥3 转 NEED_APPROVAL,不然 ALLOW)。READ 放行时返回 `maskingRequired` 与算法编码;裁决本身不写消费审计,真实消费方在得到执行结果后显式记录。

### 脱敏(Algorithm / Policy)

- 算法:当前引擎仅支持 MASK_PARTIAL/HASH/FULL_MASK/NULLIFY/REPLACE/KEEP_FORMAT,参数 JSON。不能执行的算法编码或非法参数必须报错,禁止回退明文。
- 策略:按 levelId/categoryId/columnPattern(`*` 通配)匹配对象,`priority` 最大者优先,解析出 `MaskingDirective(mask, algoCode, algoParams)`。内置算法只读不可删。

### 访问审计(Access Log)

消费方明确记录一次真实访问尝试或已完成消费(actor、resourceKey、action、levelCode、decision、masked、algoCode、accessTime),只追加不回改。`masked` 表示消费结果已实际执行脱敏;访问裁决试算不写此流水。Dataset 查询按已解析的物理投影逐列裁决、对返回值执行脱敏并记录完成态。Data Service 当前保留 API Key/consumer 授权上下文,尚未与 USER/ROLE 策略建立已批准的映射。

### 合规(Compliance Rule / Finding)

体检规则类型:`SENSITIVE_MUST_MASKED`(高敏感 ACTIVE 资产必须有生效脱敏策略)、`SENSITIVE_MUST_CONFIRM`(高敏感 CANDIDATE 待确认)、`CLASSIFY_COVERAGE`(分级覆盖率)。`run` 批量产出 Finding(带 batchId、passed、severity),`latestSummary` 供总览。

## 不变量

1. **等级 rank 唯一口径**:敏感度判断一律经等级 `rank_no`,脱敏阈值/裁决默认/合规阈值共享同一 rank,禁止旁路。
2. **编码唯一**:`(project_id, level_code)`、`(project_id, category_code)`、`(project_id, rule_code)`、`(project_id, algo_code)`、`(project_id, object_key)` 唯一,DB 唯一键兜底;编码创建后不可改。
3. **项目空间归属**:所有业务行带 `project_id`,只取服务端 `CurrentProject`,不建物理外键(与 modeling/mdm 一致)。
4. **发现只落候选**:自动扫描生成 CANDIDATE,须经人工确认转 ACTIVE 才参与脱敏/合规口径。
5. **策略审批门禁**:仅 APPROVED 策略参与裁决;DENY 优先于 ALLOW;PENDING/REJECTED 不放行。
6. **删除 = 引用校验后物理删除**:等级被分级标签引用不可删;分类有子级或被引用不可删;内置算法不可删。删除靠审计留痕。
7. **审计 fail-open**:业务审计异常不得影响主流程(`SecurityAudit.tx` 记录后原样抛出业务异常)。
8. **总览服务端聚合**:统计禁止无界 list() 后内存汇总;独立容错,"查不到"≠异常(home-overview-contract)。

## 对外引用契约(消费方视角)

- Dataset 查询经 Dataset-owned Security Gateway 消费 Security SPI,并以投影血缘映射到物理列键;消费方不直读 `yak_dsec_*` 表。Data Service 的 API Key/consumer 身份不可伪装成 USER/ROLE,待其身份映射经产品决策后再接入。
- 展示名与等级 rank 由本模块 SPI 解析,消费方不直读 `yak_dsec_*` 表。
Approval payloads are immutable review snapshots. ACCESS_GRANT decisions lock the project-scoped
policy row and reject approval or rejection if any editable policy fact differs from that snapshot.
The policy remains the source of truth; its current record stays PENDING until the callback succeeds.
