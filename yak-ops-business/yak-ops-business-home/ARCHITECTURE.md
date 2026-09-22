# Home Architecture

> `yak-ops-business-home` 是 Yak Ops 首页只读组合子系统。仓库级工程规则以根目录 `CODE_STYLE.md` 为准。

## 1. 模块定位

Home 只负责把其他业务子系统已经拥有的事实投影成首页 read model，并通过 `/api/v1/home/**` 提供稳定读取接口。

Home **不是**新的业务真相中心：

- 不拥有数据源、任务、工作流、质量、血缘或调度生命周期；
- 不写业务数据库，不拥有 Flyway、PO、DAO、Mapper 或 Repository；
- 不绕过各业务模块已经定义的权限和 Project Scope；
- 不为了首页展示重新实现 sibling module 的业务规则。

## 2. Package Is Architecture

```text
io.yak.ops.business.home
├── controller/v1        # HTTP inbound boundary；API 路径保持 /api/v1/home/**
├── cockpit              # 顶部摘要 read model
├── datacenter           # 运行趋势、最近任务与调度摘要
├── asset                # Dataset + Lineage 首页投影
├── quality              # Quality 首页投影
└── schedule             # 统一调度日历投影
```

生产代码不创建 `service / serviceImpl / common / helper / utils / base` 业务桶。

## 3. Role Vocabulary

Home 的内部业务角色统一使用 `Reader`：

- `HomeCockpitReader`
- `HomeDataCenterReader`
- `HomeAssetOverviewReader`
- `HomeQualityOverviewReader`
- `HomeScheduleCenterReader`

这些类是 read-side 专业角色，因此使用 `@Component`，不使用 `@Service`。Controller 只负责 HTTP 参数、权限/Project 边界和稳定返回，不拥有聚合规则。

## 4. 依赖方向

```text
Controller
   ↓
Home Reader
   ↓
Sibling stable Reader / Query / Facade / Gateway
   ↓
Sibling-owned Domain / Persistence / Runtime
```

Home 可以依赖 sibling module 的稳定 read-side 或 facade，但不能直接依赖 sibling DAO、Mapper、PO、Repository implementation。

当前允许的读取边界包括：

- Datasource `DataSourceReader`
- Dataset `DatasetService.overview(...)`
- Lineage `LineageQueryService`
- Offline Sync `OfflineExecutionOverviewReader`
- Workflow `WorkflowExecutionOverviewReader`
- Quality `QualityOverviewReader` / `QualityExecutionOverviewReader`
- Yak Schedule `ScheduleManager`，通过 `YakScheduleGateway` 读取统一快照

## 5. HTTP 与 Project Scope

现有外部 API 地址保持不变：

```text
GET /api/v1/home/cockpit
GET /api/v1/home/data-center/overview
GET /api/v1/home/data-center/recent
GET /api/v1/home/data-center/schedule
GET /api/v1/home/assets/overview
GET /api/v1/home/quality/overview
GET /api/v1/home/schedule-center/calendar
```

所有 Home Controller 必须保留 `@ProjectScope`，因此读取结果始终绑定当前工作空间。质量接口继续保留原有 `quality:execution:read` 权限边界。

## 6. 失败与可用性

Home 负责“区域可用性”，不把读取失败解释成 sibling 业务失败。各 Reader 可以独立容错，但必须遵循 `REQUIREMENTS.md` 的指标语义：无法证明的指标不能伪造成业务事实。

## 7. 修改规则

修改 Home 前必须回答：

1. 数据事实真正属于哪个 sibling subsystem？
2. Home 是否只读取稳定 read-side contract？
3. 是否新增了对 DAO / Mapper / PO / Repository implementation 的穿透依赖？
4. 是否保持现有 HTTP 地址、权限与 Project Scope？
5. 是否存在无界 list、N+1 或 JVM 全量聚合？
6. 是否补充 behavior test 与 architecture/contract test？
