# 主数据管理（MDM）—— 模块设计说明

> 模块：`data-ops-business-mdm`
> 版本：v1.0
> 状态：设计基线
> 核心原则：**能复用就复用，只新建主数据特有的**

---

## 一、模块定位

**主数据管理**解决"**跨系统实体统一**"，是数据治理的核心拼图之一。

**职责**：
- 统一客户、商品、供应商等核心实体
- 从业务库采集、清洗、合并、分发
- 提供主数据服务

**不职责**：
- 不做数仓建模（复用 modeling）
- 不做数据集成（复用 sync）
- 不做质量引擎（复用 quality）
- 不做标准定义（复用 semantic）

---

## 二、模块依赖

```
data-ops-business-mdm
├── 依赖 datasource（数据源接入）
├── 依赖 sync（数据采集）
├── 依赖 quality（质量规则）
├── 依赖 data-service（API 能力）
├── 依赖 semantic（数据标准）
├── 依赖 lineage（血缘）
├── 依赖 security（权限）
├── 依赖 dataset（资产）
└── 被 modeling 依赖（数仓维表引用主数据）
```

**依赖方向**：
```
mdm ──依赖──► datasource
mdm ──依赖──► sync
mdm ──依赖──► quality
mdm ──依赖──► data-service
mdm ──依赖──► semantic
mdm ──依赖──► lineage
mdm ──依赖──► security
mdm ──依赖──► dataset
modeling ──依赖──► mdm（引用主数据）
```

**不允许反向依赖。**

---

## 三、复用 + 新建清单

### 3.1 主数据建模

| 能力 | 复用 | 新建 |
|------|------|------|
| 数据标准引用 | ✅ semantic | — |
| 实体定义 | ❌ | ✅ 主数据实体 |
| 属性定义 | ⚠️ 参考 modeling 字段 | ✅ 主数据属性 |
| 关系定义 | ⚠️ 参考 modeling 关系 | ✅ 主数据关系 |

**复用**：数据标准。
**新建**：实体、属性、关系。

### 3.2 主数据识别

| 能力 | 复用 | 新建 |
|------|------|------|
| 数据源接入 | ✅ datasource | — |
| 元数据读取 | ✅ datasource | — |
| 候选识别 | ❌ | ✅ 识别规则 |
| 用户确认 | ❌ | ✅ 确认界面 |

**复用**：数据源接入、元数据读取。
**新建**：识别规则、确认界面。

### 3.3 主数据采集

| 能力 | 复用 | 新建 |
|------|------|------|
| 数据源接入 | ✅ datasource | — |
| 同步任务 | ✅ sync | — |
| 字段映射 | ✅ sync | — |
| 调度 | ✅ sync | — |
| 采集配置 | ⚠️ 参考 sync | ✅ 主数据采集配置 |

**复用**：数据源、同步、映射、调度。
**新建**：采集配置（主数据特有）。

### 3.4 主数据清洗

| 能力 | 复用 | 新建 |
|------|------|------|
| 质量规则 | ✅ quality | — |
| 规则引擎 | ✅ quality | — |
| 去重 | ⚠️ 参考 quality | ✅ 主数据去重 |
| 合并 | ❌ | ✅ 主数据合并 |
| 标准化 | ⚠️ 参考 quality | ✅ 主数据标准化 |
| 补全 | ❌ | ✅ 主数据补全 |

**复用**：质量规则、规则引擎。
**新建**：去重、合并、标准化、补全。

### 3.5 主数据审批

| 能力 | 复用 | 新建 |
|------|------|------|
| 审批流 | ❌ | ✅ 全部新建 |
| 版本管理 | ⚠️ 参考 modeling 版本 | ✅ 主数据版本 |

**复用**：无。
**新建**：审批流、版本管理。

### 3.6 主数据分发

| 能力 | 复用 | 新建 |
|------|------|------|
| API 能力 | ✅ data-service | — |
| 消息 | ⚠️ 参考 alert | ✅ 主数据分发 |
| 文件 | ⚠️ 参考 storage | ✅ 主数据分发 |
| 分发配置 | ❌ | ✅ 主数据分发配置 |

**复用**：API、消息、文件。
**新建**：分发配置。

### 3.7 主数据服务

| 能力 | 复用 | 新建 |
|------|------|------|
| API 管理 | ✅ data-service | — |
| 订阅 | ⚠️ 参考 data-service | ✅ 主数据订阅 |
| 缓存 | ✅ data-service | — |

**复用**：API 管理、缓存。
**新建**：订阅（主数据特有）。

### 3.8 主数据治理

| 能力 | 复用 | 新建 |
|------|------|------|
| 数据标准 | ✅ semantic | — |
| 质量检查 | ✅ quality | — |
| 血缘 | ✅ lineage | — |
| 权限 | ✅ security | — |
| 主数据特有治理 | ❌ | ✅ 少量 |

**复用**：标准、质量、血缘、权限。
**新建**：少量主数据特有治理。

### 3.9 主数据分析

| 能力 | 复用 | 新建 |
|------|------|------|
| 资产目录 | ✅ dataset | — |
| 统计 | ✅ dataset | — |
| 主数据特有分析 | ❌ | ✅ 少量 |

**复用**：资产目录、统计。
**新建**：少量主数据特有分析。

---

## 四、汇总

| 功能 | 复用 | 新建 | 复用率 |
|------|------|------|--------|
| 建模 | semantic | 实体、属性、关系 | 30% |
| 识别 | datasource | 规则、确认 | 70% |
| 采集 | sync | 配置 | 80% |
| 清洗 | quality | 去重、合并、标准化、补全 | 40% |
| 审批 | — | 全部 | 0% |
| 分发 | data-service | 配置 | 70% |
| 服务 | data-service | 订阅 | 80% |
| 治理 | semantic/quality/lineage/security | 少量 | 90% |
| 分析 | dataset | 少量 | 90% |

**整体复用率约 60%。**

---

## 五、模块内部分层

```
data-ops-business-mdm
├── api/                  # 对外 API
│   ├── EntityApi
│   ├── RecordApi
│   ├── CollectApi
│   ├── CleanApi
│   ├── ApprovalApi
│   ├── DistributeApi
│   ├── ServiceApi
│   └── AnalysisApi
├── domain/               # 领域层
│   ├── entity/           # 主数据实体
│   ├── attribute/        # 主数据属性
│   ├── record/           # 主数据记录
│   ├── source/           # 主数据来源
│   ├── change/           # 主数据变更
│   └── distribution/     # 主数据分发
├── infrastructure/       # 基础设施层
│   ├── repository/       # 仓储
│   ├── datasource/       # 数据源适配（复用 datasource）
│   ├── sync/             # 同步适配（复用 sync）
│   ├── quality/          # 质量适配（复用 quality）
│   ├── data-service/     # API 适配（复用 data-service）
│   ├── semantic/         # 标准适配（复用 semantic）
│   ├── lineage/          # 血缘适配（复用 lineage）
│   ├── security/         # 权限适配（复用 security）
│   └── dataset/          # 资产适配（复用 dataset）
├── application/          # 应用层
│   ├── EntityService
│   ├── RecordService
│   ├── CollectService
│   ├── CleanService
│   ├── ApprovalService
│   ├── DistributeService
│   └── AnalysisService
└── db/migration/yak-mdm/ # Flyway
```

---

## 六、契约文件集

```
data-ops-business-mdm/
├── README.md
├── DOMAIN.md
├── ARCHITECTURE.md
├── DEPENDENCIES.md
├── REQUIREMENTS.md
└── REVIEW.md
```

### DEPENDENCIES.md（关键）

| 依赖 | 方向 | 原因 |
|------|------|------|
| datasource | mdm → datasource | 数据源接入 |
| sync | mdm → sync | 数据采集 |
| quality | mdm → quality | 质量规则 |
| data-service | mdm → data-service | API 能力 |
| semantic | mdm → semantic | 数据标准 |
| lineage | mdm → lineage | 血缘 |
| security | mdm → security | 权限 |
| dataset | mdm → dataset | 资产 |
| modeling | modeling → mdm | 数仓维表引用主数据 |

---

## 七、Flyway

```
data-ops-business-mdm/src/main/resources/db/migration/yak-mdm/
├── V1__init_mdm_tables.sql
├── V2__init_mdm_indexes.sql
└── V3__preset_mdm_templates.sql
```

**自持 Flyway，V1 起编。**

---

## 八、菜单注册

按硬性约束 0.2：

- 新增路由声明稳定 `menuCode`
- 业务菜单行进 Yak Ops 自有 Flyway
- 追加 yak-security 时取 ≥V2019（或当前最大值）
- 通过 `navigationMenuContract.test.ts`

**菜单结构**：

```
主数据管理
├── 主数据建模
├── 主数据识别
├── 主数据采集
├── 主数据清洗
├── 主数据审批
├── 主数据分发
├── 主数据服务
├── 主数据治理
└── 主数据分析
```

---

## 九、错误码

| 错误码 | 说明 |
|--------|------|
| 43001 | 主数据实体不存在 |
| 43002 | 主数据记录不存在 |
| 43003 | 主数据属性不存在 |
| 43004 | 主数据来源不存在 |
| 43005 | 主数据变更审批失败 |
| 43006 | 主数据分发失败 |

---

## 十、落地路线

| 阶段 | 内容 | 复用为主 |
|------|------|----------|
| P0 | 模块骨架 + 主数据建模 + 主数据采集 | 复用 semantic + sync |
| P1 | 主数据清洗 + 主数据分发 + 主数据服务 | 复用 quality + data-service |
| P2 | 主数据审批 + 主数据治理 + 主数据分析 | 复用 semantic + quality + dataset |
| P3 | AI 匹配 + 主数据市场 | — |

---

## 十一、和现有模块的边界

| 能力 | 归属 | 说明 |
|------|------|------|
| 数据源接入 | datasource | MDM 复用 |
| 数据采集 | sync | MDM 复用 |
| 质量规则 | quality | MDM 复用 |
| API 能力 | data-service | MDM 复用 |
| 数据标准 | semantic | MDM 复用 |
| 血缘 | lineage | MDM 复用 |
| 权限 | security | MDM 复用 |
| 资产 | dataset | MDM 复用 |
| 主数据实体 | **MDM** | MDM 新建 |
| 主数据识别 | **MDM** | MDM 新建 |
| 主数据去重合并 | **MDM** | MDM 新建 |
| 主数据审批 | **MDM** | MDM 新建 |
| 主数据分发配置 | **MDM** | MDM 新建 |
| 主数据订阅 | **MDM** | MDM 新建 |

---

## 十二、一句话总结

> **MDM 模块只做"主数据特有"的部分，其他复用**：
>
> | 功能 | 复用 | 新建 |
> |------|------|------|
> | 建模 | semantic | 实体、属性、关系 |
> | 识别 | datasource | 规则、确认 |
> | 采集 | sync | 配置 |
> | 清洗 | quality | 去重、合并、标准化、补全 |
> | 审批 | — | 全部 |
> | 分发 | data-service | 配置 |
> | 服务 | data-service | 订阅 |
> | 治理 | semantic/quality/lineage/security | 少量 |
> | 分析 | dataset | 少量 |
>
> **整体复用率约 60%。**
>
> **原则**：能复用就复用，只新建主数据特有的。

---
