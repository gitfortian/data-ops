# Data Development Requirements

本文件定义 Data Development 的长期需求语义。它回答“这个模块需要提供什么能力”，不承担代码分层说明。

领域硬规则看 `DOMAIN.md`，代码角色看 `ARCHITECTURE.md`，依赖方向看 `DEPENDENCIES.md`，Review 标准看 `REVIEW.md`。

## 模块定位

Data Development 是数据开发工作台的 authoring context。它负责组织开发节点、维护可编辑定义、发布不可变版本，并把可执行任务交给共享 Task Runtime，把发布状态投影到 Task Catalog，把 SQL 血缘证据交给 Lineage 子系统。

它不是调度引擎、数据目录、血缘图谱或 Task Runtime 的替代实现。

## 核心能力

### 开发节点

- `DevelopmentNode` 是工作区中的长期身份，负责名称、类型、项目/目录位置和配置状态。
- 节点类型必须显式声明能力；不能通过 `PROCESSING / OUTPUT` 分类推断是否允许进入任务生命周期。
- SQL / SHELL / HTTP / PYTHON / JAVA 可以进入 Task Draft -> Revision 生命周期。
- DATASET / DATA_SERVICE 是输出型节点，不复用 Task Draft -> Revision 生命周期。

### Task Authoring

可执行节点必须支持：

```text
DevelopmentNode
    -> mutable DevelopmentTaskDraft
    -> immutable DevelopmentTaskRevision
```

要求：

- Draft 使用独立的 `draftRevision` 做并发控制；
- 保存 Draft 必须校验 Node Type 与 TaskDefinition Type 一致；
- `configJson` 在进入持久化前规范化为 JSON Object；
- Publish 必须基于明确的 Draft Revision；
- Publish 必须执行对应 Task Plugin 的校验；
- 相同 Draft Revision + 相同 Definition Digest 重复发布时复用已有 Revision；
- Published Revision 不可被后续编辑覆盖。

### Editor Run

编辑器运行当前内容时，不要求先 Publish。

```text
current editor definition
    -> TaskVersionSnapshot(version = 0)
    -> shared Task Runtime
    -> DevelopmentTaskExecution history
```

Editor Run 与 Published Revision 是两个概念。运行当前编辑器内容不能隐式创建 Published Revision，也不能修改 Task Catalog 的发布指针。

### Release Projection

发布后的 Data Development Task 通过 Task Catalog 暴露上线、下线和历史版本切换能力。

Task Catalog 是发布状态和跨模块任务资产入口；`DevelopmentTaskRevision` 仍然是 Data Development 内部不可变版本事实。

### Dataset

DATASET 是输出节点。新编辑器直接拥有 datasource + SQL + field contract；历史 TaskAsset source binding 只保留兼容读取/保存能力。

Dataset 的配置事实不能重新塞进 executable Task Draft / Revision，也不能因为共享 SQL 能力而伪装成 SQL Task。

### Data Service

DATA_SERVICE 使用独立的 Draft / Revision / Runtime contract，不得伪装成通用 Task Node。

Data Service 发布前至少要保证：

- Runtime 支持的 HTTP method；
- 有明确 DataSource；
- SQL 非空；
- Response Contract 已确认；
- Request Contract 满足当前 Runtime 能力。

### Lineage

SQL Task Revision 发布后可以异步生成表级和字段级 lineage evidence。

Data Development 只拥有“哪个 Revision 产生了什么解析证据”的责任；Lineage Asset / Relation 的最终事实由 `yak-ops-business-lineage` 持有。

Lineage 解析或写入失败不能回滚已经成功提交的业务发布；可靠处理通过 durable outbox / worker 完成。

## 兼容要求

纯结构重构必须保持：

- `/api/v1/data-development/**` REST contract；
- 现有数据库表和 Flyway migration；
- Draft optimistic revision 语义；
- Revision number / checksum / append-reuse 语义；
- Task Plugin validation 语义；
- Task Catalog publish / online / offline / activate 行为；
- Editor Run 走共享 Task Runtime 且使用 version-0 snapshot；
- Dataset / Data Service 现有 API 与运行 contract；
- SQL lineage outbox 与 latest-revision replacement 语义。

如果需要改变这些行为，应作为明确的 Requirement / Domain change 单独设计，不能藏在 package move 或类拆分里。

## 架构治理要求

- package 按业务子系统和专业角色组织，不用通用 `service/common/helper/utils` 代替设计；
- `Service` 可以作为稳定 Application Entry，但不是默认类名；
- Parser / Resolver / Validator / Normalizer / Publisher / Reader / Provider / Worker / Outbox 等角色应按真实职责命名；
- Controller 只进入稳定应用入口，不直接访问 Repository / DAO；
- Domain 不拥有 Task Runtime、Task Catalog、Lineage、Spring JDBC、MyBatis 等外部实现；
- 当前 `service` 目录是冻结 legacy island，只允许 `ARCHITECTURE.md` 声明的固定类型集合；
- 新功能不得向 legacy island 新增文件；
- 架构 contract 必须由自动化测试保护，规则变化时同步修改文档与护栏。

## 已知迁移债务

当前仍保留两类刻意未与结构治理混做的债务：

```text
Data Service 大实现
SQL Lineage Parser / Analyzer 大算法
```

它们位于 frozen legacy island。后续迁移应独立进行，并与 SQL parsing / Data Service 行为变化分开。

Execution history、Editor settings、Lineage Outbox 等历史边界仍存在直接 JDBC 使用；新增持久化能力默认优先建立 Repository contract。
