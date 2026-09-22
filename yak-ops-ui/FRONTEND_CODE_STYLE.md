# Yak Ops UI Frontend Engineering Standard

本文件定义 `yak-ops-ui` 的长期前端工程规范。

目标不是为了目录整齐而目录整齐，而是让开发者看到一个文件时，可以快速回答：

> 它属于哪个业务？它是什么角色？它应该放在哪里？

核心原则：**业务模块内聚，全局基础能力集中；页面负责组装，组件负责展示与交互，Hook 负责状态与行为，Service 负责后端通信，Yak Components 负责统一视觉。**

> 本规范从新增代码开始执行。历史代码按模块逐步迁移，不要求一次性大搬家。

## 1. Ownership First

文件放哪里，先看谁拥有它。

- 只被一个页面使用：留在页面模块。
- 被同一业务的多个页面使用：放业务模块公共目录。
- 被多个业务稳定复用：才提升到全局 `components / hooks / utils / assets`。
- 不因为“以后可能复用”提前抽全局公共代码。

推荐目标结构：

```text
src/
├── assets/                     # 全局静态资源
├── components/
│   ├── ui/                     # Yak Design System 公共入口
│   └── shared/                 # 跨业务稳定复用组件
├── services/                   # 所有后端 API
│   └── <domain>/
├── pages/
│   └── <module>/
│       ├── index.tsx
│       ├── components/
│       ├── hooks/
│       ├── constants.ts
│       ├── types.ts
│       ├── utils.ts
│       └── assets/
├── hooks/                      # 真正跨业务 Hook
├── constants/                  # 真正全局常量
├── utils/                      # 无业务语义工具
└── styles/                     # 全局主题 / Token
```

目录不要求机械补齐。模块没有对应角色时，不创建空目录。

## 2. Page

`index.tsx` 是页面入口，不是业务垃圾桶。

页面主要负责：

```text
读取路由 / 上下文
        ↓
调用业务 Hook
        ↓
组合页面 Components
        ↓
连接用户事件
```

下面内容明显变复杂时，应拆出页面入口：

- 大段表格 columns；
- 状态映射和业务常量；
- 复杂数据转换；
- 轮询、订阅和异步加载；
- 大块 Modal / Drawer JSX；
- 多步骤业务判断；
- API URL、HTTP 请求和后端响应解析。

拆分依据是**角色**，不是单纯按行数拆文件。

## 3. Components

组件分三层：

| 类型 | 位置 | 示例 |
| --- | --- | --- |
| Yak 基础组件 | `components/ui` | `YakButton`、`YakTab`、`YakEmpty` |
| 跨业务组件 | `components/shared` | `SqlCodeEditor` |
| 业务组件 | `pages/<module>/components` | `WorkflowInstanceTable` |

组件使用 PascalCase，并表达真实角色：

```text
WorkflowInstanceTable.tsx
WorkflowInstanceFilter.tsx
CreateTaskModal.tsx
DevelopmentTreePane.tsx
```

避免模糊名称：

```text
CommonTable.tsx
BaseModal.tsx
CommonComponent.tsx
UtilsComponent.tsx
```

组件 Props 默认与组件放在一起。只有多个文件共享且语义稳定时才提升到模块 `types.ts`。

## 4. Yak Components

Yak Components 是 Yak Ops UI 的统一视觉入口。

如果已有 Yak 封装，业务页面默认使用 Yak Component，而不是直接使用对应 Ant Design 组件：

```text
Button -> YakButton
Tabs   -> YakTab
Empty  -> YakEmpty
```

当前阶段通过 `@/components/ui` 建立稳定公共入口；历史的 `@/components/YakButton` 等路径继续兼容，后续按模块逐步迁移。

Yak Component 保持薄封装：

```text
Ant Design 能力
      +
Yak Ops 统一视觉
      +
少量稳定通用交互语义
```

不要把具体业务逻辑放进 `YakButton / YakTab / YakEmpty`。

只有 Yak Component 无法表达需求时才直接使用原始组件，并优先判断公共组件是否应该补充能力。

## 5. API / Service

所有后端接口统一进入：

```text
src/services/<domain>/
```

页面、组件、Hook 中不新增：

```ts
request('/api/...');
HttpUtils.get('/api/...');
fetch('/api/...');
```

业务代码通过明确 Service 调用后端：

```ts
import { listWorkflowInstances, retryWorkflowInstance } from '@/services/workflow';
```

Service 函数使用明确动词：

```text
getXxx
listXxx
createXxx
updateXxx
deleteXxx
saveXxx
publishXxx
retryXxx
startXxx
stopXxx
```

避免：

```text
handleXxx
doXxx
processXxx
requestXxx
xxxApi
```

API URL 只存在于 Service 边界。

### Response Boundary

新增或重构的 Service 优先直接返回业务数据：

```ts
Promise<WorkflowInstance[]>
```

页面不负责反复判断 `code`、读取 `data` 或解析统一响应 envelope。

历史 Service 仍可暂时返回 `ApiResponse<T>`；迁移时优先使用现有 `HttpUtils.getData / postData / putData / deleteData` 等数据接口，将 envelope 处理留在 HTTP / Service 边界。

## 6. Service 目录

小业务域：

```text
services/workflow/
├── api.ts
├── types.ts
└── index.ts
```

业务域变大后按资源拆：

```text
services/workflow/
├── instances.ts
├── definitions.ts
├── schedules.ts
├── types.ts
└── index.ts
```

`index.ts` 是公开出口，不应长期承载大量 API、类型和实现细节。

禁止在 `pages/<module>/service.ts` 新增后端接口。历史页面 Service 后续迁移到 `src/services/<domain>`。

## 7. Types

后端 DTO / API contract：

```text
services/<domain>/types.ts
```

页面展示模型：

```text
pages/<module>/types.ts
```

组件 Props：默认与组件同文件。

类型使用 PascalCase，并表达业务语义：

```text
WorkflowInstance
WorkflowInstanceQuery
CreateWorkflowPayload
DevelopmentNode
```

避免长期使用过于宽泛的 `Data / Info / Obj / ResultData / Params`。

## 8. Hooks

Hook 适合承载：

- 异步加载；
- 一组相关 State；
- 副作用；
- 轮询 / 订阅；
- 页面业务行为。

例如：

```text
useWorkflowInstances
useDevelopmentTree
useDatasetDetail
usePolling
```

业务 Hook 默认跟业务模块走。跨多个业务稳定复用后，才提升到全局 `src/hooks`。

不要为了减少页面行数，把普通纯函数全部包装成 Hook。

## 9. Functions And Variables

用户事件：

```text
handleCreate
handleDelete
handleSearch
handleSubmit
handleTabChange
```

数据加载：

```text
loadInstances
loadDirectories
refreshData
```

格式化 / 判断 / 构造 / 转换：

```text
formatDuration
formatDateTime
isRetryable
isTerminal
canDelete
buildQuery
buildPayload
toTreeNode
```

避免长期使用：

```text
handle
dealData
process
doSomething
getData
```

Boolean 使用 `is / has / can / should`：

```text
isLoading
hasPermission
canRetry
shouldRefresh
```

数组使用复数；特殊结构体现类型：

```text
instances
selectedRows
nodeMap
statusSet
editorRef
```

避免无语义的 `data / list / info / temp / flag / value1`。

## 10. Constants

稳定模块常量放模块自己的 `constants.ts`：

```ts
export const WORKFLOW_STATUS_LABELS = { /* ... */ };
export const TERMINAL_STATUSES = new Set([/* ... */]);
export const DEFAULT_PAGE_SIZE = 20;
```

只使用一次的简单值无需机械抽取。

全局 `src/constants` 只放真正跨业务的稳定常量。

## 11. Assets And Icons

全局品牌、Logo、公共背景：

```text
src/assets/
```

页面独有资源：

```text
pages/<module>/assets/
```

业务 SVG React Component：

```text
pages/<module>/components/icons/
```

跨业务公共 Icon 才进入公共组件层。

Icon 优先级：

```text
Yak Icon
  ↓
Ant Design Icons
  ↓
Lucide
  ↓
自定义 SVG
  ↓
图片 Icon
```

同一类操作尽量保持同一套视觉语言。

资源使用语义命名：

```text
PythonIcon.tsx
JavaIcon.tsx
EmptyDataset.svg
DataDevelopmentHero.webp
```

禁止 `icon1.svg / img2.png / bg-new-final.png` 一类名称。

## 12. Styles

优先顺序：

```text
Yak Component
    ↓
Design Token
    ↓
模块 class
    ↓
必要的动态 inline style
```

颜色、圆角、阴影等稳定视觉值不要在大量页面重复复制。

同一视觉语言出现多次时，应评估进入 Yak Component 或 Design Token。

不要为了单个页面直接修改全局 Ant Design selector。

## 13. Imports

推荐顺序：

```text
React / Framework
第三方库
@/ 全局模块
./ 当前模块
样式
```

跨模块使用 `@/`：

```ts
import { YakButton } from '@/components/ui';
import { listWorkflowInstances } from '@/services/workflow';
```

模块内部使用相对路径：

```ts
import WorkflowTable from './components/WorkflowTable';
import { useWorkflowInstances } from './hooks/useWorkflowInstances';
```

不要用多层 `../../../` 跨越业务边界。

## 14. Quality Gate

前端代码统一接受项目现有质量检查：

```bash
npm run lint
npm test
```

`src/services` 与普通前端代码使用同一套 Biome 基础检查，不再作为特殊区域排除。

新增代码不通过 lint / TypeScript 检查时，不应依赖“后面统一处理”。

## 15. Migration Rule

规范落地采用渐进式迁移：

1. 新代码立即遵守本规范；
2. 修改旧模块时在当前边界内顺手收敛；
3. 选代表性模块建立样板；
4. 再按业务模块逐步迁移；
5. 不做全仓一次性目录搬迁和纯格式 churn。

推荐首批样板：

- `data-development`：收敛 Page 内 Service、types、icons 与页面职责；
- `workflow/instances`：收敛大页面、常量/工具函数、Yak Components 使用方式。

## 16. Review Checklist

提交前至少确认：

1. 文件属于哪个业务，owner 是否清楚？
2. 这是 Page、Component、Hook、Service、Type、Constant 还是 Utility？
3. 是否把业务私有代码提前放进了全局目录？
4. 是否存在页面直接访问后端？
5. 是否已有 Yak Component 却继续创建另一套视觉？
6. 函数和变量名是否表达真实业务含义？
7. 图片 / Icon 放置位置是否符合 ownership？
8. `index.tsx` 是否仍能一眼看出页面结构？
9. 半年以后，其他开发者能否根据角色快速找到这个文件？

如果一个文件不知道放哪里，优先重新确认它的职责，而不是新建 `common / helper / misc` 目录。