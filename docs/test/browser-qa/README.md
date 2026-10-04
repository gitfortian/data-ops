# data-ops 浏览器功能测试 · 总索引

- 测试角色：资深测试专家（独立走查，不做代码评审代替实测）
- 测试周期：2026-10-01 11:50 – 13:30（+08:00），共 3 轮补测（R1–R8）
- 被测环境：前端 `http://localhost:8000`（`MOCK=none`，真实调用后端）；后端 `http://localhost:8080`（`yak-ops`，本地 MySQL）；分支 `main`
- 覆盖对象：**104 条路由**（77 条可直接导航 + 27 条带参数的详情/编辑路由），来自唯一真相源 `data-ops-ui/src/config/navigation.ts`
- 静态前提校验：102 个带 `component:` 的菜单项全部能解析到真实页面文件，**不存在配置层死路由**
- 登录凭据：由需求方提供的本地超管账号。**账号与口令一律不写入本目录任何文件**（`00-RUBRIC.md` 明文规定），报告里只允许出现「按下发凭据登录」这类描述。

## 1. 阅读顺序建议

| 顺序 | 文件 | 内容 |
|---|---|---|
| ① | `00-RUBRIC.md` | 评级口径（P0–P3）、三类问题定义、数据红线、CDP 工具用法 |
| ② | 本文件 §5 | 跨分区共因（工程发现 E1–E13 + C1），这是修复性价比最高的一层 |
| ③ | `01…06` 分区报告 | 每个功能分区一份，内含逐页明细、问题清单、覆盖矩阵、遗留数据 |
| ④ | 本文件 §7 | 验收判定与尚未闭环的验证限制 |

分区报告（每份单文件自洽；补测轮次正在按分区并入，尚未并入的独立补测文件在下面的「待并入」列里点名）：

| 分区 | 报告文件 | 覆盖菜单组 | 待并入的独立补测文件 |
|---|---|---|---|
| S0 | `00-登录首页与待办.md` | 登录 / 首页 / 待办 | — |
| S1 | `01-数据接入与集成.md` | `integration`：数据源、离线同步、实时同步、文件资源、SQL 执行记录 | `01b-数据接入缺口补测.md`（R8，进行中） |
| S2 | `02-标准指标与建模.md` | `standard`/`modeling`：标准、指标、语义层、建模 | 已并入 R1（指标体系）+ R2（建模与语义层） |
| S3 | `03-开发与运行.md` | `development`：数据开发、任务、工作流编排、发布中心、运行 | 已并入 R3（工作流编排） |
| S4a | `04a-资产元数据与生命周期.md` | 资产台账/目录、技术元数据、采集、生命周期 | `04e-生命周期血缘与采集细节补测.md`（R9，进行中；`06b` 为同范围的空报告，将被它替换） |
| S4b | `04b-质量安全与主数据.md` | 数据质量、数据安全（已并入 R4）、主数据 MDM | `04d-主数据与零散缺口补测.md`（R5，正在并入） |
| S5 | `05-数据消费与服务.md` | 消费目录、数据集、数据服务、调用方、仪表盘、数字大屏 | — |
| S6 | `06-平台设置与全局壳层.md` | 项目空间/用户/部门/角色/权限/配置、`/settings`、全局壳层 | `06c-壳层与窄屏补测.md`（R7，进行中） |

> 术语说明：首轮 5 个分区（S1/S2/S3/S4a/S4b）的报告由后续整理者从**测试执行日志**落盘还原，因此正文里的 `digest`／「证据 digest」指的就是**测试员在浏览器里逐条命令 + 输出 + 实时判断的执行记录**（截图与数字证据的原始出处）。这类报告的「报告性质」小节已声明它没有重新驱动浏览器；凡是 digest 里没证据的页面，都写在各报告的「走查未覆盖、需要补测的页面」一节，并由补测轮次（R1–R9）用真实浏览器补齐后并入。

## 2. 方法与证据口径

不是「看一眼截图写感受」。每一轮的判定都必须能被机器复查：

- 驱动方式：Edge `--headless=new` + 原生 CDP（WebSocket），每个分区一个专属端口与专属 profile，真实 `Input.dispatchMouseEvent` 点击（不用 `element.click()` 糊弄 hover/disabled 语义）。
- 视口口径：标称 1440×900，**实际内容视口 1414×807**；所有「溢出/裁切」判定以 1414 为准，窄屏用 `vwqa.mjs` 在同一个 CDP 会话内 `setDeviceMetricsOverride → nav → diag → shot → clear`（`vw` 跨进程不生效，这是本轮踩过的工具坑，已在报告里注明）。
- 每页必采：`getBoundingClientRect` 的 right/bottom 边界、`scrollWidth` vs `clientWidth`、被裁切元素计数、console error 列表、4xx/5xx 请求列表、页面标题/关键文案、全页截图。
- 报告里禁止出现「有点挤」「不太好看」这类无数字表述；写成「表头 right=1500 > 视口 1414，操作列 3 个按钮在屏外」。
- 三类问题必须分开记录：**功能 bug** / **交互不好** / **布局不合适**，并允许同时写「正面结论」（哪些做得好），避免报告只会被用来挑刺、不会被用来对照。

## 3. 评级口径（与 `00-RUBRIC.md` 一致）

- **P0**：白屏/崩溃、接口 5xx、核心操作无法完成、数据错误展示。
- **P1**：明显功能缺陷（保存丢失、筛选不生效、路由不可达）、正常视口下信息被裁切不可读、操作不可达。
- **P2**：可用但别扭、次要布局问题、口径不一致、成功/失败无反馈。
- **P3**：体验、视觉一致性、可访问性优化。

「跳登录页」在本轮**默认不计入产品缺陷**：后端 Sa-Token `max-login-count: 5` + `is-concurrent: true` + `is-share: false`，多个 QA 浏览器共用同一超管账号会静默互踢，属环境干扰（其带来的产品侧改进建议见 E6）。

## 4. 数据红线（已执行）

- 未重启/重编译任何服务，未修改 `data-ops-ui` 与 Java 源码，无任何 git 写操作。
- 只新增 `QA<分区>-` / `QAR<n>-` 前缀数据并自删；已存在的业务数据、用户、角色、部门、项目空间一律不改不删。
- 清空 / 重置 / 同步全量 / 发布 / 上线 / 审批通过 等按钮**只打开确认框后取消**，不真实执行。
- 例外：`/data-security` 的「连接测试/试算」类只读探针允许执行。
- 遗留数据登记见本文件 §8。

## 5. 跨分区共因（工程发现）

单页问题只有归并成仓库级规则才会被真正修掉。以下每条都有源码或日志坐标。

### E1（P0，dev 环境可用性）tailwind 插件把 dev server 弄死
`node_modules/@umijs/plugins/dist/tailwindcss.js` 拉起 tailwind CLI 后轮询 `src/.umi/plugin-tailwindcss/tailwind.css`，而 Umi 会在轮询开始后清空 `src/.umi`，子进程永远写不出该文件，插件直接 `process.exit(1)`，`max dev` 随之退出（日志停在 `tailwindcss service started`）。手工跑 tailwind 4.3s 就成功。本轮的绕行办法：起 dev 后等约 30s，再 `mkdir -p src/.umi/plugin-tailwindcss && cp <产物> src/.umi/plugin-tailwindcss/tailwind.css`。**后果**：新同事/新 agent 按 README 起步即失败，属上手阻断级。

### E2 + E11（P1，后端）业务态被抛成 HTTP 500，且 11 个业务模块根本没有异常处理器
后端其实已经产出了正确的中文业务语义，但用户看不到：

- `Temp\be-app.log` 12:54–13:04 窗口内：`IllegalStateException: 请先配置至少一个任务节点`（12:54:54、12:55:20）、`IllegalStateException: 开始节点至少需要连接一个任务节点`（13:00:28）、`IllegalArgumentException: Cron 表达式需包含 5 到 7 个字段，且不能超过 160 个字符`（12:56:09）、`IllegalArgumentException: 工作流定义不存在：workflow-…`（13:04:06、13:04:16），全部由 `dispatcherServlet` 记为 “Servlet.service() … threw exception”，即**没有任何 `@ExceptionHandler` 接住**→ Spring 原生错误体 + HTTP 500 → 前端只能显示一句 `http error`。
- 数字大屏同型：`GET /api/v1/digital-screens/{id}/published` 对「尚未发布」这种正常状态抛裸 `IllegalArgumentException`（抛出点 `DigitalScreenVersionReader.java:35`，链路 `DigitalScreenController.java:58` → `DigitalScreenApplicationService.java:54`），也是 500。
- 原因是**作用域**而非缺少设计：`YakSecurityExceptionHandler` 声明为 `@RestControllerAdvice(basePackages = "io.yak.framework.security.controller")`，仓库的实际约定是「每个业务模块自带一个 advice」，而扫描 `data-ops-business/*/src/main/java`（含至少一个 `*Controller.java` 的模块）结果是 **有 advice 15 个 / 无 advice 11 个**：analysis(1 ctrl)、consumption(4)、dashboard(2)、data-service(8)、dataset(2)、digital-screen(1)、home(6)、job(2)、lineage(1)、task-catalog(1)、workflow(6)。
- 工作流链路已证到底：`WorkflowDefinitionController.java:140-141`（`POST /{id}/test-run`）→ `WorkflowDefinitionManager.testRun/testRunDraft`（:402/:412）→ `WorkflowLauncher.testRunDraft`（:156）→ `WorkflowStartGraphCompiler.java:54` / `WorkflowDefinitionManager.java:581` 抛裸异常 → 模块无 advice → 500。
- 修复方向：在 `data-ops-boot` 放一个 `@RestControllerAdvice(basePackages = "io.yak.ops")`，把 `IllegalArgumentException`/`IllegalStateException` 映射成语义化业务码并透传 message，模块级 advice 再做特化。这一条能同时消掉 R3 的 P0、S5-01 的 P1 和多处「保存失败只看到 http error」。
- **反方向补充（同一根因的第二种表现）**：有 advice 也不等于说清了什么。数据安全模块的 `SecurityExceptionHandler.java:70-74` 用 `@ExceptionHandler(Exception.class)` 兜底，返回 `Result.fail("数据安全操作失败,请稍后重试")`，即 **HTTP 200 + code 999**（`CommonErrorCode.COMMON_FAIL(999)`）+ 一句泛化文案，把 `MaskingEngine` 抛出的 `IllegalArgumentException: Masking parameters are invalid JSON` / `Unsupported masking algorithm` 全部吃掉；对比同模块 `createAlgorithm`/`resolve` 会把异常包成 45065 专用码（`MaskingService.java:325-331`），说明这是**兜底分支漏了 `IllegalArgumentException` 特化**，而不是整体没有设计。所以 E11 的修复要同时覆盖两类：裸 500 和「200 + 999 + 通用文案」，后者更难排查因为它看起来是成功响应。

### C1 + E13（P0/P1 源头）宽表没有 `scroll.x` + 操作列没固定，而应用外壳把溢出裁掉了
- `data-ops-ui/src/layouts/SiteLayout/index.tsx:418` 是 `h-screen overflow-hidden`，`:425` 是 `flex flex-col overflow-hidden`。外壳本身不横向滚动，所以任何比内容区宽的表格，只要自己没声明 `scroll={{x}}`，其超出部分**在 DOM 里存在但没有任何手段可达**（用户既看不到滚动条也拖不动）。
- 静态计数（`data-ops-ui/src/pages/**/*.tsx`，排除测试）：渲染 `title: '操作'` 的文件 **52**；未使用 `fixed: 'right'` 的 **32**；完全没有 `scroll=` 的 **31**；**两者都没有的 28** —— 后者就是会变成 P0 的那一批：ai-agent/{ConfigPanel,ReportsTab,SkillListTable}、approval/{flows,todo}、data-asset/inventory、data-security/{audit,classification,compliance,masking}、mdm/{approval,identification,modeling + AttributeTab/ChangeHistoryTab/CollectStatusTab/DistributionTab/SubscriptionTab}、metric/service、modeling/{index,layer-mapping,mainline,mapping}、semantic/{fields,layers,processes/index,processes/edit,standards}。
- 浏览器实测完全对得上：`/semantic/layers` 操作列 right=1500、`/semantic/fields` right=1500、`/modeling` right=1801/1863（视口 1414），行内 编辑/删除/停用 不可点（R2-01/09/14，P0）；`/data-security/audit` 的「资源」列被渲染成 **width:0**（R4-03，P1）；`/system/projects` right=1508（S6-02，P1）；`/data-quality/table-config` 内容宽 1815（S4b-01，P2）。
- 结论：加一条 lint / code review 硬规则「有操作列的表格必须同时声明 `fixed:'right'` 与 `scroll.x`」，能一次性关掉多个分区里占比最大的 P0/P1/P2。因为外壳是刻意的不滚动布局，**不要**改成全局 `overflow-x:auto` 来「修」。

### E10（P1，前端权限）产品灰度开关只守菜单，不守路由
`src/config/productFeatures.ts:9-11` 声明 `resourceAuthorization:false`、`systemConfig:false`，`navigation.ts:189-191` 只把它们写成 `hidden: !productFeatures.x`；而 `hidden` 仅在建菜单时被过滤（`navigation.ts:252` `&& !route.hidden`），路由守卫 `canAccessNavigationRoute`（`navigation.ts:222-244`）只比对 `permissionCodes` + `menuCodes`，`RouteAccessBoundary/index.tsx:73-84` 直接采信其结果。**效果**：未发布能力对任何持有对应 `security:*:read` 权限码的角色都能 URL 直达并执行写操作（实测 `/system/configs`、`/system/resource-permissions`、`/system/permissions` 三页全功能渲染，S6-01）。RBAC 本身没错——不带该权限码的角色会被送去 `ForbiddenPage`；错的是「发布闸门」语义只实现了菜单可见性，和 `productFeatures.ts:3-6` 注释承诺的 “removing unfinished entry points” 不一致。修复点明确：让 `canAccessNavigationRoute`（或 wrapper）也消费 feature gate。

### E8（P1，跨域）Snowflake Long ID 未统一序列化成字符串
仓库已有约定 `@JsonSerialize(using = ToStringSerializer.class)`，但它是**逐字段**贴的，属于结构性易漏：`DevelopmentTaskExecutionSummary.java:7-8`、`DevelopmentTaskExecutionDetail.java:8-9,15` 没贴，而同模块的 `DevelopmentDataServiceDraft.java:9`、`...Revision.java:9-10`、`...Definition.java:9-10,20` 贴了。浏览器侧实测：真实 `nodeId 2105511064154697729` 被渲染成尾数 `…700`（> 2^53 精度丢失，S3-01）。**建议**：改成全局 `ObjectMapper` 对 `long/Long` 的规则，而不是继续靠注解逐个补。

### E12（P1，质量门禁）后端单测在 main 上是红的（已实跑证明）
`mvn -o -pl data-ops-business/data-ops-business-security -am -Dtest=MaskingEngineTest test` → `Tests run: 10, Failures: 0, Errors: 2`：
- `MaskingEngineTest.blankAlgoKeepsOriginal:28` 期望未知/空算法原样返回，`MaskingEngine.java:33` 实为抛 `IllegalArgumentException`；
- `MaskingEngineTest.malformedParamsFallBackToDefaults:71` 期望非法 JSON 回落默认值，实际在 `MaskingEngine.readParams(:100)` 抛错。

要么是实现收紧而测试没跟上，要么是测试才是意图而实现回归了——两种读法都可行动作，且第二条直接是用户可见缺陷：脱敏试算面板允许自由输入 JSON，参数写错就是抛错而不是回默认。顺带：同一次复核确认 R4-14「试算不消费参数」的真实根因是**参数键名三套口径互不相认**（引擎只读 `keepLeft/keepRight`，见 `MaskingEngine.java:44-45`；UI 占位符写 `{"keep":3}`/`{"keep":4}`；算法字典存 `{"front":3,"end":4}`；`MaskingService.java:181` 保存字典时从不校验键名，于是永远静默走默认值）。

### E5（P3，安全）SQL 参数值被写进日志
`application.yml` 打开 `io.yak.framework.security: DEBUG`，MyBatis 因此打印 `==> Parameters:` 实参，约 40 分钟 51,436 条 DEBUG 行。本地开发可以，**不能带着上线**（敏感值落盘 + 日志量）。

### E6（认证行为，影响测试也影响用户）静默互踢 + 锁定
`max-login-count: 5`、`is-concurrent: true`、`is-share: false` → 第 6 次登录静默挤掉最旧会话，被挤掉页面直接跳 `/login?returnTo=…` 且无任何解释；`LoginAttemptGuard` 在 5 次错密码后锁号 15 分钟（code 2006）。产品层面至少应区分「会话已在别处失效」与「未登录」并给出可读文案。

### E3（P2，后端）主数据定时分发 #1 永久失败
`MdmDistributionScheduleHandler : 定时分发执行失败 distribution=1`（12:00:00），根因 `MdmException: 主数据分发失败：主数据实体未生效，不能发布或刷新供数 API`（`MdmDistributionService.java:235`）：一条持久化的分发记录指向未生效实体，调度器每轮重试每轮失败，而这件事**只存在于服务端日志**，MDM 分发界面看不见。

### E4（正面）后端整体稳定性
整轮 7 个分区并行走查约 40 分钟，`be-app.log` 51,883 行里只有 **2 条 ERROR**（都是 E2）和 1 条 WARN（E3）；401 是规范的业务信封 `{"code":2001,"message":"用户未登录"}`，不是裸 5xx。前端侧亦未出现白屏或 JS 崩溃类问题（除 E2 造成的大屏查看页整页黑）。

### E7（仓库门禁）静默吞错自检脚本当前本地 FAIL
`node scripts/backbone/check-silent-catch.mjs` → 委派全局 51 / 降级展示 29 / 良性忽略 9 / **可疑吞错 2**（`DatasetNodeEditor.tsx:278`、`MetricEditModal.tsx:215`）。两处均已读源码确认为良性的 `JSON.parse` 回落，但未在清单里声明，因此门禁在本地是失败状态；CI 要绿需要先归类而不是先忽略。

## 6. 复核与撤回（保持报告可信）

测试报告会过度归因，本节记录被源码证据推翻的条目，避免修复资源被浪费在假问题上。

- **S1-01 由 P1 降为 P3（原判断不成立）**：曾断言「编辑数据源把显示掩码 `******` 当真实密码回传，会静默破坏已存凭据」。源码反驳：`DataSourceSecretCodec.java:96-97` 在提交值满足 `shouldPreserve()`（:156-161：null / 空 / 等于 `MASKED_VALUE="******"`）时用库中旧密文回填；`services/data-source/types.ts:33` 明确把掩码当作展示协议；`src/pages/data-source/utils.test.ts:20,32` 钉住了该行为。**真正残留的缺陷**是：用户清空密码框时无法真的清空（空值被旧密文静默回填），且界面没有「清除密码」这个显式动作。
- **S5-02「时间早 8 小时」不是平台时区配置问题**：`application.yml:58` 已是 `serverTimezone=Asia/Shanghai`、`:24` `time-zone: Asia/Shanghai`、宿主时区 China Standard Time，消费链路代码里唯一的 `toISOString()` 属于本地仓储（`local-screen-repository.ts:12`，不在该渲染路径）。所以这是仪表盘/大屏卡片**前端本地格式化缺失**（同分区里调用方列表时间正确，可作参照实现），修复时不要去动服务端时区。
- **S4a-09 差异摘要整列输出 `{}` 已定位到一行代码**：`src/pages/data-asset/inventory/index.tsx` 里 `const text = diff ? JSON.stringify(diff) : ''` —— 空对象 `{}` 是 truthy，于是渲染成 `{}`；正确写法是 `diff && Object.keys(diff).length ? JSON.stringify(diff) : ''`。级别维持 P2。
- **04a 两条「无二次确认」P1 的边界已写清**：`data-metadata/collect/index.tsx:265-268`「立即运行」与 `data-asset/inventory/index.tsx:515-524`「全部对账/对账此源」的 `onClick` 直接触发，外层无 `Popconfirm` 也无 `Modal.confirm`（同页其它危险动作都有）。但这几个是**异步受理的非破坏性读侧采集/对账**，不删改数据；真实缺陷是「系统级触发缺二次确认 + 缺执行中/已受理反馈」，不是数据风险。

## 7. 验收判定与验证限制

（待全部补测轮次并入后由 §9 汇总数字与逐分区结论支撑；此处先说明结构性限制。）

- **消费链路无法端到端验收**：本地库中**没有任何已发布的 Dataset / Data Service 投影**，因此「消费目录 → 数据集详情/预览 → API 详情 → 调试」这条 S5 主链路只能验证到列表与空态。发布/上线属数据红线，不允许在他人库上执行。要验收必须先由业务方准备一份已发布数据集与一个已上线 API。
- **依赖外部 Worker 的链路未验证**：实时同步详情、单表同步「选源表→字段映射→目标建表→保存」全链路需要 LinkUp Worker 在线；本环境不可达，属环境限制而非产品缺陷。
- **多表同步 / 脚本同步配置页**：需先创建对应类型任务才能进入路由，第一轮因此没有渲染到页面本体（`/config/script` 疑似会被弹回列表）；已在补测范围。
- **受限账号越权矩阵**：本轮只有超管一个可用账号，「持有 `security:config:read` 的非超管角色能否越过灰度闸门」只做了源码推断，未做账号实测（见 S6 补测清单第 6 条）。

## 8. 遗留测试数据登记与清理结果

清理原则：QA 轮次自己创建的对象必须由 QA 清掉；删除前先核对名称/ID 与报告登记一致，弹框文案原文引用，删完重新导航复核。逐条证据（含截图编号）在 `Temp\qa\cleanup-result.md`。

### 8.1 已确认删除（有复核证据）

| 对象 | 归属 | 删除路径 | 复核证据 |
|---|---|---|---|
| 目录 + 标签 `QA-S4A-*` | S4a | `/data-asset/catalog` | 删后列表不再包含该前缀 |
| 调用方 / 仪表盘 / 大屏 `QA5-*` | S5 | 各自列表页 | 行内动作删除 + 重新导航复核 |
| 用户 / 部门 / 角色 / 项目空间 `QAZL-S6-*` | S6 | `/system/*` | 机器证据（行数 0） |
| 指标 + 标签 `QAR1-*` | R1 | `/metric/*` | 删除复核 |
| 模型 `QAR2-*` | R2 | `/modeling` | 删除并清空回收站 |
| 工作流定义 + 调度 `QAR3-*` | R3 | `/workflow/*` | 删除复核 |
| 主数据实体 `QAR5_TEST`（`QAR5-测试实体`） | R5 | `/mdm/modeling` 行内删除 | 确认框 + 复核 |
| 质量监控 `QA4B-客户地址监控-改`（监控 id 3） | 04b 遗留 | `/data-quality/monitor/3` → 更多操作 → 删除质量监控 | 确认框 + 复核（R5 轮执行） |
| 离线同步任务 `QA-S1-batch`（id `1790828362125000`） | S1 遗留 | `/sync/batch-link-up` → 更多 → 删除任务 | toast `["删除成功"]`；删后 `{rows:0, hasBatch:false, emptyTxt:"暂无离线同步任务"}` |
| 文件资源目录 `QA-S1-dir` | S1 遗留 | `/resource-management` → 操作 → 删除 | 因确认框声明「递归删除全部子资源」，**先进目录核实子资源为 0 行再删**；删后 `{hasDir:false}` |
| 文本资源 `QA-S1-text.txt`（8 B） | S1 遗留（报告未登记确切文件名，按 `QA-S1` 前缀 + 8 B 唯一命中） | `/resource-management` → 操作 → 删除 | 删后 `{rows:[], hasQA:false}`，页面不再出现任何 `QA-S1` 资源 |
| 业务过程 `QA_S2_PROC1`（id 26）、码值标准 `qa_s2_status`（`QA测试码值标准`）、业务域 `QA_S2_DOM1`（id 55） | S2 遗留 | `/semantic/{processes,standards,domains}`，按「过程 → 码值标准 → 域」顺序删以免父域被子对象阻断 | toast `["已删除"]` ×3；reload 复核 `{qaS2:false, rows:0}`；顺带复现了 S2-05（`删除` 按钮 `right=1496 > 1414`，靠自动横向滚动才点得到） |

### 8.2 未能清理（已判定为产品缺陷，不是清理遗漏）

- **独立复核（第二轮清理，端口 9301）**：`GET /api/v1/data-quality/monitors/3` 在两个项目空间都返回 404「质量监控不存在：3」，监控列表总数只剩 2 条（均为 09-21 / 09-24 的既有监控）——即 `QA4B-客户地址监控-改` 在本轮清理前就已不存在，上表该行按「R5 轮已删 + 本轮 404 复核」成立。清理过程留下的唯一痕迹是一条执行记录（`QM-20261001123048754-…`），执行历史类记录系统本身不提供删除入口，不属遗留数据。
- **注册进数据表监控的表 `crm_customer_address` 无法反注册**：`/data-quality/table-config` 该行操作列只有 `新增监控 / 规则管理`，界面**没有提供反注册入口**（前端未挂载该接口）。这条同时是「注册即不可撤销」的功能缺陷；第二轮清理再次逐行确认所有入口（行内、批量、工具栏）均无该项，未绕过 UI 操作。**这是本轮 QA 结束后本地库里仍然留存的唯一一条 QA 数据**，需要治理侧从接口/DB 侧处置。
- **`/mdm/cleansing` 的 2 条 QA 清洗规则**（`会员等级码值标准化（R7 验证）` 等）：由 R7 创建后只登记未删，而清洗的「执行标准化」会对 **534 条真实生效记录**批量改写且不可回滚（见 04b 的 R5-21），属于必须消除的现实风险，已派专项清理（结果并入本节）。

### 8.3 设计上不可删

- `初始化默认分层` 按钮（无二次确认，R2-02）在「工业制造」空间创建了 `ODS/DWD/DWS/ADS` 四条 `preset=true` 分层（id 10–13），系统预置语义即不可删除。这条不是清理项，而是「危险按钮缺确认」的直接代价证据。

## 9. 问题计数汇总

口径：只数各分区报告「问题清单」表格里的独立 ID 行（同一 ID 在别处重复出现不重复计数），`~~删除线~~`（已撤回条目）不计。分级用机器统计（awk 逐行取级别列），不采用报告作者自报的汇总数——第 R5 轮就出现过自报 2 个 P0、表格实为 3 个 P0 的情况，已在 04b 内注明并以机器统计为准。

| 分区 | 报告 | P0 | P1 | P2 | P3 | 合计 | 状态 |
|---|---|---|---|---|---|---|---|
| S0 登录/首页/待办 | `00-登录首页与待办.md` | 1 | 2 | 8 | 5 | 16 | 定稿 |
| S1 数据接入与集成 | `01-数据接入与集成.md` | 0 | 1 | 16 | 11 | 28 | 待并入 R8（+0/+2/+6/+7 = 15） |
| S2 标准/指标/建模 | `02-标准指标与建模.md` | 4 | 14 | 36 | 36 | 90 | 定稿（含 R1 + R2） |
| S3 开发与运行 | `03-开发与运行.md` | 1 | 7 | 18 | 11 | 37 | 定稿（含 R3） |
| S4a 资产/元数据/生命周期 | `04a-资产元数据与生命周期.md` | 0 | 2 | 14 | 19 | 35 | 待并入 R6（+0/+2/+3/+3 = 8，其原文件每条重复列了两遍，并入时去重）+ R9（进行中） |
| S4b 质量/安全/主数据 | `04b-质量安全与主数据.md` | 3 | 8 | 35 | 39 | 85 | 定稿（含 R4 + R5） |
| S5 数据消费与服务 | `05-数据消费与服务.md` | 0 | 4 | 7 | 9 | 20 | 定稿 |
| S6 平台设置与全局壳层 | `06-平台设置与全局壳层.md` | 0 | 2 | 8 | 15 | 25 | 待并入 R7（+0/+0/+8/+11 = 19，进行中） |
| **已定稿小计** | | **9** | **38** | **142** | **145** | **334** | |
| **并入后预期** | | **9** | **42** | **159** | **166** | **376** | R8/R6/R7 并入后刷新 |

- 分布形态本身就是结论：**P2 + P3 占 86%**（布局裁切、缺反馈、口径不一致、可访问性），P0/P1 只占 14%，而 P0/P1 里绝大多数由 §5 的 3 条共因（E11 异常映射、C1+E13 表格滚动与固定列、E10 灰度闸门）派生。也就是说：**先修 3 条共因，再清一屏 P2，比逐页打磨性价比高得多。**
- 覆盖度：**104 条路由中 101 条已有本分区内的直接浏览器证据**，剩下 3 条（`/ai-agent`、`/data-analysis/lineage`、`/approval/flows`）中 `/ai-agent` 与 `/approval/flows` 已由第 R5 轮在 04b 里补到证据（R5-40/41 P0、R5-42/43），`/data-analysis/lineage` 由 R6/R9 补测中。

## 10. 仓库侧安全观察（与被测功能无关，但必须报告）

- **本地超管口令以明文写在已入库的文件里**：`docs/test/semantic-standards-test-report.md:6` 一行同时给出账号与口令，该文件自 `146073c4 initial-import` 起就被 git 跟踪，任何有仓库读权限的人都能拿到。本轮 QA 新增的全部报告（`docs/test/browser-qa/*`）都不含账号名与口令，只有「按下发凭据登录」的描述；正文里出现的 `root` 一律是**页面/接口原样渲染出来的业务数据**（负责人、触发人、`createdBy`、MySQL `Access denied for user 'root'@'localhost'` 等），不是凭据披露。
- 建议动作（属仓库治理，不由测试执行）：改掉这条本地口令，并把该历史文档改写为引用环境变量/密钥；若仓库曾对外托管，需按泄露处理。
- 与之相邻的一条产品侧观察：数据源连接测试失败时把数据库原始报错（含用户名、host、`using password: YES`）整句抛给业务用户（S1-11），以及 `application.yml` 打开 MyBatis 参数日志（E5）——三者同属「凭据/敏感值出现在不该出现的位置」这一类。
