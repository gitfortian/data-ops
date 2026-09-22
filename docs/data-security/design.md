# 数据安全（Data Security）—— 模块设计说明

> 模块：`yak-ops-business-security`　根包：`io.yak.ops.business.security`
> 版本：v1.0　状态：设计基线
> 核心原则：**对齐而非重造（等级字典对齐 semantic）；分层而非越权（RBAC 管入口、本模块管数据对象）；以 SPI 出口让下游消费形成闭环**

---

## 一、模块定位

**职责**：数据资产的分级分类、敏感发现、数据级访问策略、脱敏裁决与算法、数据访问留痕、合规体检，并对外暴露安全 SPI。

**不职责（复用 + 跳转）**：
- 不定义"安全标准"字典本身 → 对齐 `semantic` 的 `SECURITY` 标准
- 不做菜单/接口 RBAC → 用 `yak-security` 框架（`@RequiresPermission`）
- 不做操作审计存储 → 复用 `audit`（`BusinessAuditService`）
- 不做查询 SQL 改写拦截 → 由下游在吐数处调用脱敏 SPI
- 不做列级物理加密落库 → 仅算法占位

## 二、模块依赖（单向，禁止反向）

```
modeling / data-service / datasource ──消费──► security(SPI)
security ──依赖──► semantic(optional, 对齐 level_code)
security ──依赖──► audit(操作留痕)
security ──依赖──► datasource(optional, 读数据资产目录做发现/展示)
security ──依赖──► yak-security(权限注解 + CurrentUserProvider)
```

## 三、复用 + 新建清单

| 能力 | 复用/新建 | 说明 |
|------|-----------|------|
| 安全等级字典 | 新建（对齐 semantic） | 引用 `SECURITY.level_code`，可选 |
| 数据分类字典 | 新建 | 平台无分类树 |
| 资产分级标签 | 新建 | 核心 |
| 敏感发现 | 新建（读 datasource 目录） | |
| 数据级访问策略/裁决 | 新建（叠加在 RBAC 上） | |
| 脱敏算法 | 新建（内置种子，参考 datasource `SensitiveTextMasker` 语义） | |
| 操作审计 | 复用 audit | |
| 数据访问流水 | 新建 | 数据级留痕专用 |
| 合规 | 新建（规则对元数据体检） | |

## 四、实体设计（表前缀 `yak_dsec_`；均含 `project_id/created_by/create_time/update_time`；无物理外键；逻辑外键用 ID/编码）

### 4.1 `yak_dsec_security_level` 安全等级（分级）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| project_id | BIGINT | 项目空间 |
| level_code | VARCHAR(32) | 等级编码，如 `L4`，项目内唯一 |
| level_name | VARCHAR(64) | 等级名称 |
| rank_no | INT | 序位，越大越敏感 |
| std_security_id | BIGINT | 对齐 semantic SECURITY 标准（可空） |
| description | VARCHAR(512) | 描述 |
| status | VARCHAR(16) | DRAFT/ACTIVE/DISABLED |

索引：`uk(project_id, level_code)`，`idx(project_id, rank_no)`

### 4.2 `yak_dsec_data_category` 数据分类（分类树）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | |
| project_id | BIGINT | |
| category_code | VARCHAR(64) | 编码，项目内唯一 |
| category_name | VARCHAR(128) | 名称 |
| parent_code | VARCHAR(64) | 父分类编码，根为空 |
| sort_order | INT | 排序 |
| description | VARCHAR(512) | |
| status | VARCHAR(16) | |

索引：`uk(project_id, category_code)`

### 4.3 `yak_dsec_classification` 资产分级标签
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | |
| project_id | BIGINT | |
| object_type | VARCHAR(16) | DATASOURCE/DATABASE/TABLE/COLUMN |
| object_key | VARCHAR(512) | 规范化自然键（见 §六），项目内唯一定位一个对象 |
| datasource_id | BIGINT | |
| db_name | VARCHAR(128) | |
| table_name | VARCHAR(128) | |
| column_name | VARCHAR(128) | |
| object_name | VARCHAR(256) | 展示名 |
| level_id | BIGINT | 等级 |
| category_id | BIGINT | 分类（可空） |
| source | VARCHAR(16) | MANUAL/DISCOVERED/INHERITED |
| confidence | INT | 置信度 0-100 |
| discovery_rule_id | BIGINT | 命中规则（可空） |
| status | VARCHAR(16) | CANDIDATE/ACTIVE/REJECTED |

索引：`uk(project_id, object_key)`，`idx(project_id, level_id)`

### 4.4 `yak_dsec_discovery_rule` 敏感发现规则
| 字段 | 类型 | 说明 |
|------|------|------|
| id/project_id | | |
| rule_code | VARCHAR(64) | 唯一 |
| rule_name | VARCHAR(128) | |
| match_type | VARCHAR(16) | NAME/COMMENT/CONTENT/REGEX |
| pattern | VARCHAR(512) | 关键词或正则 |
| level_id | BIGINT | 命中定级 |
| category_id | BIGINT | 命中分类 |
| enabled | TINYINT | |
| description | VARCHAR(512) | |

### 4.5 `yak_dsec_access_policy` 数据访问策略
| 字段 | 类型 | 说明 |
|------|------|------|
| id/project_id | | |
| policy_name | VARCHAR(128) | |
| subject_type | VARCHAR(16) | USER/ROLE |
| subject_key | VARCHAR(128) | 用户名/角色码 |
| scope_type | VARCHAR(16) | DATASOURCE/DATABASE/TABLE/COLUMN/LEVEL/ALL |
| datasource_id | BIGINT | |
| db_name / table_name / column_name | VARCHAR | 作用范围 |
| level_id | BIGINT | scope=LEVEL 时 |
| access_type | VARCHAR(16) | READ/WRITE/EXPORT |
| effect | VARCHAR(16) | ALLOW/DENY |
| priority | INT | 命中优先级，大者优先 |
| valid_from / valid_to | DATETIME | 有效期 |
| status | VARCHAR(16) | PENDING/APPROVED/REJECTED/DISABLED |
| applicant / approver / reason | VARCHAR | 申请审批 |

### 4.6 `yak_dsec_masking_algorithm` 脱敏算法字典
| 字段 | 类型 | 说明 |
|------|------|------|
| id/project_id | | |
| algo_code | VARCHAR(32) | 唯一：MASK_PARTIAL/HASH/FULL_MASK/NULLIFY/REPLACE/KEEP_FORMAT |
| algo_name | VARCHAR(64) | |
| params | JSON/TEXT | 算法参数（保留位数/掩码字符等） |
| builtin | TINYINT | 内置不可删 |
| description | VARCHAR(512) | |

### 4.7 `yak_dsec_masking_policy` 脱敏策略
| 字段 | 类型 | 说明 |
|------|------|------|
| id/project_id | | |
| policy_name | VARCHAR(128) | |
| level_id | BIGINT | 按等级触发 |
| category_id | BIGINT | 按分类触发 |
| column_pattern | VARCHAR(128) | 列名匹配（可空） |
| algo_id | BIGINT | 施加算法 |
| priority | INT | 多命中取高 |
| enabled | TINYINT | |
| description | VARCHAR(512) | |

### 4.8 `yak_dsec_access_log` 数据访问流水
| 字段 | 类型 | 说明 |
|------|------|------|
| id/project_id | | |
| access_time | DATETIME(6) | 访问时间 |
| actor | VARCHAR(64) | 访问人 |
| resource_type | VARCHAR(16) | DATASOURCE/TABLE/COLUMN |
| resource_key | VARCHAR(512) | 对象自然键 |
| resource_name | VARCHAR(256) | |
| action | VARCHAR(16) | READ/WRITE/EXPORT |
| level_code | VARCHAR(32) | 命中等级（可空） |
| decision | VARCHAR(16) | ALLOW/DENY/NEED_APPROVAL |
| masked | TINYINT | 是否脱敏 |
| algo_code | VARCHAR(32) | 脱敏算法（可空） |
| source | VARCHAR(32) | 调用来源模块 |

索引：`idx(project_id, access_time)`，`idx(project_id, actor)`，`idx(project_id, decision)`

### 4.9 `yak_dsec_compliance_rule` 合规规则
| 字段 | 类型 | 说明 |
|------|------|------|
| id/project_id | | |
| rule_code | VARCHAR(64) | 唯一 |
| rule_name | VARCHAR(128) | |
| rule_type | VARCHAR(32) | SENSITIVE_MUST_CLASSIFIED/SENSITIVE_MUST_MASKED/ACCESS_MUST_LOGGED/CLASSIFY_COVERAGE |
| params | JSON/TEXT | 阈值（如 rank 门限、覆盖率%） |
| severity | VARCHAR(16) | HIGH/MEDIUM/LOW |
| enabled | TINYINT | |

### 4.10 `yak_dsec_compliance_finding` 合规检查结果
| 字段 | 类型 | 说明 |
|------|------|------|
| id/project_id | | |
| batch_id | VARCHAR(64) | 同一次体检批次 |
| rule_id | BIGINT | |
| target_key | VARCHAR(512) | 被检对象 |
| target_name | VARCHAR(256) | |
| passed | TINYINT | |
| finding | VARCHAR(512) | 缺口描述 |
| severity | VARCHAR(16) | |
| checked_time | DATETIME(6) | |

## 五、包结构（DDD，参照 mdm）

```
io.yak.ops.business.security
├── api/               对外 SPI 契约：SecurityMaskingApi / SecurityAccessDecisionApi / SecurityClassificationQueryApi（接口 + record）
├── application/       @Component 用例服务（规则唯一归属）
│   ├── SecurityLevelService / DataCategoryService / DataClassificationService
│   ├── SensitiveDiscoveryService / DataAccessPolicyService / AccessDecisionService
│   ├── MaskingService / AccessAuditLogService / ComplianceService / SecurityOverviewService
│   └── spi/           SPI 实现 @Component（实现 api 接口，委托 application 服务）
├── config/            ConditionalOnSecurityPersistence + SecurityPersistenceConfiguration(Flyway+@MapperScan)
├── controller/v1/     @RestController + converter/ + dto/ + vo/
├── dao/mapper/        @Mapper extends BaseMapper<XxxPO>
├── domain/            Java record + enum（无 Spring 注解），按能力分包：level/category/classification/discovery/access/masking/audit/compliance
├── exception/         SecurityException extends BusinessException
├── infrastructure/repository/  接口 + Adapter 对
└── support/audit/     AuditTransactions（复用同款）
```

PO 位于 `io.yak.ops.common.bean.po.security`；错误码 `io.yak.ops.common.enums.security.SecurityErrorCode`（**45001~45099**）；权限码 `io.yak.ops.common.constant.security.SecurityPermissionCode`（`data-security:read|create|update|delete`）。

## 六、关键算法

- **object_key 规范化**：`type:dsId:db.table.column`（缺段用 `-` 占位），用于唯一定位与策略/脱敏/审计对齐。
- **访问裁决**：收集匹配（主体 + 范围 + 动作 + 有效期）的策略，`DENY` 优先；无命中→默认按 `default-policy`（本期默认 ALLOW 非敏感、需审批敏感），产出 `decision` 并写 `access_log`。
- **脱敏裁决**：按字段命中的 classification（等级/分类）匹配 masking_policy（priority 取高），产出 algo_code；`mask()` 纯函数按 algo_code 施加。
- **合规体检**：分批 `batch_id` 遍历规则，join 现有元数据判断缺口，落 `compliance_finding`，返回汇总。

## 七、闭环

```
定级(资产标签) ─► 权限(访问策略) ─► 脱敏(策略+SPI) ─► 审计(访问流水) ─► 合规(体检)
      ▲                                                                    │
      └──────────────── 反哺：发现规则/合规缺口驱动再定级 ◄───────────────┘
对外出口：MaskingApi / AccessDecisionApi / ClassificationQueryApi 供 data-service·预览·建模消费
```
