# F-001-A — 资产详情分区契约

Status: APPROVED  
状态说明：已批准，可进入技术设计  
Feature ID: F-001-A  
父 Feature：F-001 — 数据资产治理枢纽收敛  
关联产品决策：PD-001 — Asset Governance Hub（已接受）  
负责人：Product  
创建日期：2026-09-22  
实施状态：未开始

> 本 Feature 只定义 Asset Detail 的产品契约，不直接规定最终 Java 类名、数据库表或前端组件结构。  
> 后续技术设计必须遵守这里的事实归属、适用矩阵和状态语义。

## 1. 目标

建立统一的 Asset Section Contract，让 Asset 可以聚合不同治理域的事实，同时不把自己演化成新的“万能真相中心”。

用户看到的是：

~~~text
资产详情
├─ 概览
├─ 技术元数据
├─ 数据质量
├─ 数据安全
├─ 数据血缘
├─ 使用情况
├─ 生命周期
└─ 治理信息
~~~

而不是让用户理解：

~~~text
Metadata Module
Quality Module
Security Module
Lineage Module
Lifecycle Module
~~~

## 2. 核心原则

### 2.1 Asset 负责聚合，不负责复制 Truth

Asset 可以展示：

- 摘要；
- 状态；
- 更新时间；
- 来源；
- 下一步操作；
- 专业域回链。

Asset 不得为了展示方便复制：

- Metadata 的表/字段事实；
- Quality 的规则和执行结果本体；
- Security 的策略和定级事实；
- Lineage 的边关系；
- Lifecycle 的 TTL / retention policy；
- Dataset / Metric / Model 的业务定义。

### 2.2 每个 Section 必须声明事实归属

每个 Section 必须至少明确：

- 谁拥有 Truth；
- 当前资产类型是否适用；
- 当前状态；
- 摘要内容；
- 更新时间；
- 专业处理入口；
- 失败原因。

### 2.3 “没有数据”与“系统不知道”必须区分

统一 Section 状态：

| 状态 | 中文含义 | 使用场景 |
|---|---|---|
| `OK` | 有可用数据 | 已成功获取并可展示 |
| `EMPTY` | 确认没有数据 | 适用，但当前没有记录或尚未配置 |
| `NOT_APPLICABLE` | 当前资产类型不适用 | 例如 Metric 不适用 TTL |
| `UNAVAILABLE` | 暂时无法获取 | 依赖服务未装配、超时、查询失败 |
| `PERMISSION_DENIED` | 无权限查看 | 事实存在，但当前用户无权访问 |

禁止使用 `null`、空对象或空字符串同时表达上述不同状态。

## 3. 分区清单

### 3.1 概览

Owner：Asset

负责：

- Asset identity；
- 名称 / 描述；
- Owner；
- 目录 / 标签；
- 上架状态；
- sourceType / sourceId；
- 健康度摘要；
- 基础更新时间。

### 3.2 技术元数据

Owner：Metadata

负责展示：

- 数据源；
- database / schema / table；
- 字段入口；
- 技术属性；
- Metadata entity 状态；
- 专业元数据详情入口。

### 3.3 数据质量

Owner：Quality

负责展示：

- 是否已纳入质量管理；
- 是否存在 Monitor；
- 最近执行状态；
- 最近执行结论；
- 最近问题数量；
- 最近执行时间；
- 进入质量专业页的操作。

### 3.4 数据安全

Owner：Security

负责展示：

- 当前分类 / 分级；
- 是否存在访问或脱敏相关策略摘要；
- 当前用户是否可查看安全详情；
- 进入安全专业页的操作。

### 3.5 数据血缘

Owner：Lineage

负责展示：

- 当前 Asset identity；
- 上游摘要；
- 下游摘要；
- 一跳关系；
- 完整血缘 / 影响分析入口。

### 3.6 使用情况

Owner：联邦归属，不设单一新 Truth Owner

分成三类事实：

1. Asset 自己拥有页面访问 activity；
2. Lineage 拥有结构依赖；
3. Dashboard / Data Service / Agent / Dataset 等消费域拥有真实业务消费事实。

Asset Usage Section 负责聚合，不新建统一 Usage 真相库。

### 3.7 生命周期

Owner：Lifecycle

MVP 复用现有 `AssetStatusTtlFacts`：

- `policyApplied`
- `policyCode`
- `bindingSource`
- `state`

当前仅 MODEL source 适用。

### 3.8 治理信息

Owner：Asset

负责：

- 上架 / 下架 / 忽略；
- Owner；
- 目录；
- Asset tags；
- 治理预检查；
- 治理活动记录。

## 4. MVP 资产类型适用矩阵

说明：

- “必须”表示该类型的 MVP 必须具备该 Section 契约；
- “可适用”表示产品允许，但由 Source Domain 当前覆盖能力决定；
- “不适用”表示当前 MVP 明确返回 `NOT_APPLICABLE`，不能伪装成 EMPTY。

| 资产类型 | 技术元数据 | 质量 | 安全 | 血缘 | 生命周期 / TTL | 使用情况 | 治理 |
|---|---|---|---|---|---|---|---|
| Physical Table | 必须 | 必须 | 可适用 | 必须 | 不适用 | 必须 | 必须 |
| Model | 不适用 | 不适用 | 可适用 | 必须 | 必须 | 必须 | 必须 |
| Metric | 不适用 | 不适用 | 可适用 | 必须 | 不适用 | 必须 | 必须 |
| Dataset | 不适用 | 不适用 | 可适用 | 必须 | 不适用 | 必须 | 必须 |

### 4.1 为什么 Quality 当前只对 Physical Table 生效

现有 Quality 架构以注册物理质量目标、Monitor、Rule、Execution 为主，当前没有 Model / Metric / Dataset 对应的同等稳定 Truth。

因此：

- 不为了 Asset 360 强行扩张 Quality Domain；
- 其它类型返回 `NOT_APPLICABLE`；
- 将来如果 Quality 正式支持新的对象类型，再通过独立 Feature 修改矩阵。

### 4.2 为什么 Lifecycle 当前只对 Model 生效

现有 `AssetStatusTtlFactsAdapter` 明确通过 Model ID 解析 TTL binding。

因此：

- MODEL：适用；
- TABLE / METRIC / DATASET：当前 MVP 明确 `NOT_APPLICABLE`；
- 表存储量或存储统计不等于 Lifecycle TTL policy，不能混为一谈。

### 4.3 为什么 Security 使用“可适用”

Security 当前可以通过对象键查询分类，但不同资产类型的对象键映射和安全覆盖并不完全一致。

因此：

- 产品允许显示 Security Section；
- 有事实则 `OK`；
- 明确无事实则 `EMPTY`；
- 服务异常则 `UNAVAILABLE`；
- 无权限则 `PERMISSION_DENIED`。

不得把“未覆盖”直接解释为“安全”。

## 5. Quality 最小摘要契约

Quality 继续拥有所有质量 Truth。

Asset 需要的最小只读摘要：

| 字段语义 | 说明 |
|---|---|
| 是否适用 | 当前 Asset 是否属于 Quality 支持对象 |
| 是否已纳入质量管理 | 是否注册为质量目标 |
| Monitor 状态 | 是否存在启用中的监控 |
| 最近执行状态 | 最近 Execution 的生命周期状态 |
| 最近执行结论 | 通过 / 未通过 / 错误等结果摘要 |
| 最近问题数量 | 最近一次或产品定义窗口内的问题数 |
| 最近执行时间 | 最近一条质量证据时间 |
| 专业页入口 | 跳转到 Quality 对应对象 |

约束：

- Asset 不保存 Quality rule / monitor / execution Truth；
- 首选由 Quality read-side 提供对象级 Summary Query；
- 如果暂时没有稳定 Query API，第一阶段允许 Adapter 封装现有 Reader，但不能把 DAO / Mapper 暴露给 Asset。

## 6. Lifecycle 最小摘要契约

MVP 直接以现有 `AssetStatusTtlFacts` 为事实来源。

字段：

| 字段 | 含义 |
|---|---|
| `policyApplied` | 是否命中有效策略 |
| `policyCode` | 当前策略编码 |
| `bindingSource` | 策略绑定来源 |
| `state` | 生命周期状态 |

适用范围：

- MODEL：适用；
- 其它 MVP 资产类型：`NOT_APPLICABLE`。

当 Lifecycle 服务未装配或查询失败时：

- 返回 `UNAVAILABLE`；
- 不能解释成“没有 TTL”。

## 7. Usage 事实归属

### 7.1 Asset Page Activity

Owner：Asset

例如：

- 资产详情页访问次数；
- 最近访问趋势。

这些是“页面活动”，不是业务消费。

### 7.2 结构依赖

Owner：Lineage

例如：

- 下游 Dataset；
- 下游 Dashboard；
- 上下游依赖关系。

### 7.3 真实业务消费

Owner：各消费域

例如：

- Dashboard 引用；
- Data Service API 调用；
- Agent 查询；
- Dataset 下游使用。

### 7.4 Asset 的职责

Asset 可以把上述事实聚合成 Usage Section，但不得建立第二套“统一 Usage Truth”。

如果未来为了查询性能引入缓存：

- 必须是 derived cache；
- 必须可重建；
- 必须标明来源域；
- 不得成为新的 Truth Owner。

## 8. Section 产品数据契约

概念模型：

~~~text
AssetSection
├─ sectionType
├─ status
├─ ownerDomain
├─ summary
├─ reason
├─ updatedAt
└─ actions
~~~

说明：

- `sectionType`：Section 类型；
- `status`：五态之一；
- `ownerDomain`：事实来源域；
- `summary`：只读摘要；
- `reason`：EMPTY / UNAVAILABLE / NOT_APPLICABLE / PERMISSION_DENIED 的原因；
- `updatedAt`：源事实更新时间；
- `actions`：专业详情、问题处理、影响分析等下一步。

本 Feature 不要求现在就冻结 Java DTO 字段名。

## 9. Provider 产品契约

后续技术设计可以采用 Provider SPI，但必须满足：

- 一个 Section 只有一个产品语义；
- Provider 不成为新 Truth Owner；
- Provider 只调用 owning domain 的稳定 read-side；
- Provider 失败只影响自己的 Section；
- 支持按资产类型判断适用性；
- 支持返回五态。

概念接口：

~~~java
interface AssetSectionProvider {

    SectionType type();

    boolean supports(AssetContext context);

    AssetSection query(AssetContext context);
}
~~~

接口名可在技术设计阶段调整，产品语义不得改变。

## 10. 聚合行为

Asset Detail 聚合时：

1. Asset 本体必须先成功；
2. Section 独立查询；
3. 单个 Section 失败不能导致整页 500；
4. 慢 Section 可以独立加载；
5. 明确不适用的 Section 不调用源域；
6. 无权限不能伪装成 EMPTY；
7. 所有可操作摘要都要能回链 owning domain。

## 11. 前端展示约束

前端不能把 Section 的异常统一渲染成“暂无数据”。

至少区分：

- 有数据；
- 确认无数据；
- 当前资产类型不适用；
- 服务暂不可用；
- 当前用户无权限。

用户点击专业操作后：

- 带入正确 source identity；
- 进入 owning domain；
- 能保留返回 Asset context 的路径。

## 12. MVP 范围

本 Feature 只授权“契约层”设计和后续技术实现准备。

第一轮技术实现优先验证：

1. Section 状态模型；
2. 适用矩阵；
3. Technical Metadata corridor；
4. Lineage corridor；
5. Quality Summary Query contract；
6. Lifecycle / TTL contract；
7. Usage ownership contract。

不要求第一轮一次性完成所有 UI 收敛。

## 13. 明确不做

F-001-A 不决定：

- Dataset 默认消费契约；
- Asset 上架状态是否阻断消费；
- Data Asset 与 Data Governance 顶级导航是否合并；
- Metadata 模块是否删除；
- Quality 扩展到 Metric / Dataset / Model；
- 全局 Usage Event Platform；
- Global Search；
- Agent 重构；
- Asset 健康度新评分公式。

## 14. 验收场景

### 场景 A：Physical Table

给定一张 Metadata 已采集、Asset 已登记的物理表：

- Technical Metadata：可用；
- Quality：可用或 EMPTY；
- Security：按实际覆盖返回；
- Lineage：按实际关系返回；
- Lifecycle：`NOT_APPLICABLE`；
- Usage：至少能区分页面活动与业务使用；
- Governance：可用。

### 场景 B：Model

给定一个 Model Asset：

- Technical Metadata：`NOT_APPLICABLE`；
- Quality：`NOT_APPLICABLE`；
- Lifecycle：使用 `AssetStatusTtlFacts`；
- Lineage / Usage / Governance 按实际事实呈现。

### 场景 C：Metric

给定一个 Metric Asset：

- Technical Metadata / Quality / Lifecycle：`NOT_APPLICABLE`；
- Lineage / Usage / Governance 仍然可工作；
- Security 按实际覆盖诚实返回。

### 场景 D：Quality 服务异常

Quality query 失败时：

- Quality Section = `UNAVAILABLE`；
- reason 可理解；
- 其它 Section 继续正常展示。

### 场景 E：无权限

用户无权查看 Security 详情时：

- Security Section 不返回 EMPTY；
- 使用 `PERMISSION_DENIED` 或由 Security owner 决定隐藏敏感摘要；
- Asset 本体和其它 Section 不受影响。

## 15. 成功标准

F-001-A 完成后，后续开发新增或改造一个治理 Section 时，应该首先回答：

1. 这个 Section 服务什么用户问题？
2. Truth Owner 是谁？
3. 哪些 Asset 类型适用？
4. 五态怎么表达？
5. Summary 从哪个 read-side 获取？
6. 下一步专业动作去哪？
7. 是否引入第二份 Truth？

而不是直接：

- 给 Asset 表加字段；
- 在 Asset Controller 里直接调用多个 Service；
- 复制其它域的数据模型；
- 再造一个独立详情页。

## 16. 下一步

F-001-A 合并到 `main` 后，下一阶段进入：

**F-001-B — Asset Section 技术设计**

技术设计重点：

- package / role；
- SPI；
- Query API / Gateway；
- DTO；
- 聚合器；
- 容错；
- 异步 / lazy load；
- 权限；
- 缓存；
- 测试与架构守卫。

F-001-B 仍然先设计，设计审核后才进入第一个业务实现 PR。
