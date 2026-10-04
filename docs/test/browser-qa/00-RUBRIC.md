# Yak Ops 浏览器功能测试 · 共用测试规范（QA Rubric）

> 本文件是本次全量浏览器测试（browser QA）的统一判定标准与操作说明。所有分区测试报告都必须遵守。

## 0. 环境（已就绪，不要改动）

- 前端：`http://localhost:8000`（UmiJS Max dev server，已用 `MOCK=none` 启动，真实走后端）
- 后端：`http://localhost:8080`（Spring Boot，本地 MySQL `127.0.0.1:3306/yak_security`）
- **不要重启/重新构建任何服务，不要执行 git 写操作，不要修改 `data-ops-ui` 或 Java 源码。**
- 视口固定 1440×900（真实桌面分辨率）。

## 1. 测试工具（CDP harness）

脚本：`C:\Users\tianxy105\AppData\Local\Temp\qa\qa-cdp.mjs`

每个测试分区必须使用**自己独立的 Edge 实例**（独立端口 + 独立 user-data-dir），避免互相干扰：

```bash
# 1) 启动自己的浏览器（把 PORT 换成分配给你的端口）
"/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" \
  --headless=new --remote-debugging-port=PORT \
  --user-data-dir="C:\\Users\\tianxy105\\AppData\\Local\\Temp\\qa\\profile-PORT" \
  --window-size=1440,900 --no-first-run about:blank > /tmp/edge-PORT.log 2>&1 &

# 2) 登录（每个新实例都要做一次）
node qa-cdp.mjs PORT nav "http://localhost:8000/login" 4000
node qa-cdp.mjs PORT type "input[type=text]" "root"
node qa-cdp.mjs PORT type "input[type=password]" '<向用户索取，禁止写入任何文件>'
node qa-cdp.mjs PORT click "button[type=submit], form button"

# 3) 逐页测试
node qa-cdp.mjs PORT nav "http://localhost:8000/<路由>" 5000   # 自动返回 diag + console 错误 + 4xx/5xx 请求
node qa-cdp.mjs PORT shot /tmp/qa-PORT/page-01.png             # 截图（必须 Read 这张图做布局判断）
node qa-cdp.mjs PORT shot /tmp/qa-PORT/page-01-full.png full   # 整页截图
node qa-cdp.mjs PORT dump 6000                                 # 页面可见文本
node qa-cdp.mjs PORT eval '<js>'                               # 任意 DOM 断言
node qa-cdp.mjs PORT click '<css selector>' [nth]              # 真实鼠标点击
node qa-cdp.mjs PORT type  '<css selector>' '<text>'           # 输入
node qa-cdp.mjs PORT key Enter|Escape                          # 按键
node qa-cdp.mjs PORT vw 1280 800                               # 改视口（窄屏布局回归必测：1280 / 1024 / 992）
node qa-cdp.mjs PORT vw off                                     # 恢复默认视口
```

`nav` 返回的 `diag` 字段含义：
- `overflowX`：页面出现横向滚动（布局问题）
- `clipped`：文字被容器裁切（`w` 是容器宽度，`need` 是实际需要宽度）
- `tiny`：元素溢出视口
- `noLabel`：输入控件缺少可访问标签
- `errors` / `failed`：console 报错与 4xx/5xx 请求

## 2. 每一页的检查清单（逐项都要过）

| 维度 | 检查内容 |
|---|---|
| 加载 | 首屏是否出现骨架/加载态；接口是否全部 2xx；有无 5xx / 401 / 403；有无 console error |
| 布局 | 横向滚动、文字裁切、元素重叠、溢出视口、卡片高度失衡、大片空白/死区、表格列宽挤压、分页器位置、1440 与 1000 宽下的表现 |
| 内容 | 数字/单位/时间格式是否正确；占位符 `--`、`NaN`、`undefined`、`[object Object]`；中英文混杂；i18n 缺失（console 里 `[React Intl] Missing message`） |
| 交互 | 搜索、筛选、排序、分页、Tab 切换、树展开、行选择、批量操作、刷新；操作后是否有明确反馈（toast / 状态变化）；危险操作是否有二次确认 |
| CRUD | 打开"新增/编辑"表单 → 校验必填项（直接提交看报错）→ 填写 → 提交 → 列表是否刷新并出现新数据 → 编辑 → 删除。**测试数据一律用 `QA<分区>-` 前缀，测完删除自己创建的数据** |
| 空态/异常态 | 无数据时是否有引导（而不是空白）；切到没有数据的工作空间看空态；接口失败时是否有兜底 |
| 闭环 | 详情页能否返回列表；面包屑/标题是否对得上；跨页跳转是否丢上下文（筛选、分页、工作空间） |

## 3. 严重级别定义

- **P0 阻断**：页面白屏/崩溃、接口 5xx、核心操作无法完成、数据错误展示
- **P1 严重**：功能有明显缺陷（保存丢失、筛选不生效、权限绕过、明显裁切导致信息不可读）
- **P2 一般**：交互别扭但可用（无反馈、多余步骤、状态不记忆）、次要布局问题、文案/国际化不一致
- **P3 建议**：体验优化、视觉一致性、可访问性

## 4. 数据红线

- 只读浏览、筛选、打开弹窗后取消，是安全的。
- 允许新增以 `QA<分区>-` 开头的数据，**测完必须删除自己新增的数据**。
- **禁止**删除、修改、停用任何已存在的业务数据、用户、角色、部门、项目空间。
- **禁止**执行任何"清空 / 重置 / 同步全量 / 发布 / 上线 / 审批通过"类按钮，除非只是打开确认框然后取消。
- 涉及系统级操作（重启服务、调度全量、删除库表）一律不执行，只在报告里记录"该按钮存在且缺少二次确认"这类问题。

## 5. 报告输出格式

文件路径：`docs/test/browser-qa/<分区编号>-<分区名>.md`（用 Write 工具直接写，不要改别人的文件）

```markdown
# <分区名> 浏览器测试报告

- 测试人/分区：S<n>
- 测试时间：<YYYY-MM-DD HH:MM>
- 视口：1440x900（Edge headless + CDP）
- 覆盖页面：<n> 个（<x> 个已测，<y> 个未能覆盖及原因）

## 结论摘要
- P0 <n> / P1 <n> / P2 <n> / P3 <n>
- 一句话结论：<这个分区整体可用 / 有阻断 / 需要修复后再验收>

## 问题清单（按严重级别排序）
| ID | 级别 | 页面 | 问题 | 复现步骤 | 期望 | 实际 |
|---|---|---|---|---|---|---|
| S<n>-01 | P1 | /xxx | ... | 1. ... 2. ... | ... | ... |

## 逐页明细
### <页面中文名> `<路由>`
- 状态：通过 / 有问题 / 未覆盖
- 接口：全部 200 / 列出异常
- 布局：<具体观察，引用截图>
- 交互：<具体观察>
- 问题：<引用上表 ID>
- 截图：`qa-shots/<分区>/<page>.png`

## 交互与共性问题
- <这个分区里反复出现的交互/一致性问题，抽象总结>

## 未覆盖项
- <路由 + 未测原因（需要外部依赖、数据不具备、风险操作）>
```

要求：**每条问题必须可复现**（写清步骤），布局/交互类问题必须给出具体观察（哪个元素、什么现象），不要写"体验不佳"这类空话。截图存到 `C:\Users\...\Temp\qa-shots\<分区>\`，报告里引用文件名即可。
