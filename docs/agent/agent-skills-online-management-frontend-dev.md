# Agent 技能在线管理 前端开发文档

> 后端功能已实现并通过全部测试（见 `docs/test/agent-skills-online-management-test-report.md`，23/23 全绿）。
> 本文档定义前端（yak-ops-ui）技能管理页面的开发落地规范，与后端 API/权限/异常契约一一对应。
> 目标：在 AI 分析工作台内新增「技能管理」能力，管理员可在线注册/更新/启停/删除技能，立即热生效。

---

## 1. 页面定位与入口

- **入口**：AI 分析工作台（`src/pages/ai-agent/index.tsx`）的 Tabs 中新增「技能管理」Tab（与「报告」`ReportsTab`、「审计」`AuditTable` 同级）。
- **权限**：Tab 可见性由平台权限注入：需要 `agent:skill:read` 才显示；操作按钮按 `agent:skill:manage` 控制（后端 Controller 已用 `@RequiresPermission` 强制；前端仅做 UX 收敛）。
- **路由**：不新增路由页，作为工作台内 Tab（与既有 Report/Audit 一致，保持单页工作台结构）。

## 2. 后端 API 契约（前端直接对接）

### 2.1 服务封装（`src/services/agent/api.ts` 扩展）

```ts
// 技能类型（与后端 SkillVO 1:1）
export interface AgentSkillItem {
  skillId: string;      // 技能标识（唯一，SkillBox 键的展示名来源）
  name: string;         // 展示名
  description: string;  // 一句话描述（注入提示用）
  metadata: Record<string, unknown>; // 能力标签等
  content: string;      // 技能正文（instructions，注入 System Prompt）
  enabled: boolean;     // 在线启停状态（ENABLED/DISABLED）
  version: number;      // 乐观版本（更新时随请求回传，防并发覆盖）
  createTime: string;
  updateTime: string;
}

// 注册/更新请求体（对应后端 SkillSaveRequest）
export interface AgentSkillSaveInput {
  skillId: string;      // 注册时必填；更新时路径带 skillId，body 可不重复传
  name: string;
  description?: string;
  metadata?: Record<string, unknown>;
  content: string;
}

// 在线启停请求体（对应后端 SkillActiveRequest）
export interface AgentSkillActiveInput {
  active: boolean;
}

export const agentSkillApi = {
  /** 在线注册技能（POST /api/v1/agent/skills） */
  register: (input: AgentSkillSaveInput) =>
    HttpUtils.postData<AgentSkillItem>(`${PREFIX}/skills`, input, INCLUDE_CREDENTIALS),

  /** 技能列表（GET /api/v1/agent/skills） */
  list: () => HttpUtils.getData<AgentSkillItem[]>(`${PREFIX}/skills`, INCLUDE_CREDENTIALS),

  /** 技能详情（GET /api/v1/agent/skills/{skillId}） */
  detail: (skillId: string) =>
    HttpUtils.getData<AgentSkillItem>(`${PREFIX}/skills/${encodeURIComponent(skillId)}`, INCLUDE_CREDENTIALS),

  /** 更新技能（PUT /api/v1/agent/skills/{skillId}） */
  update: (skillId: string, input: AgentSkillSaveInput) =>
    HttpUtils.putData<AgentSkillItem>(`${PREFIX}/skills/${encodeURIComponent(skillId)}`, input, INCLUDE_CREDENTIALS),

  /** 在线启停（PUT /api/v1/agent/skills/{skillId}/active），热生效：下一轮推理即按新状态注入 */
  setActive: (skillId: string, active: boolean) =>
    HttpUtils.putData<boolean>(`${PREFIX}/skills/${encodeURIComponent(skillId)}/active`, { active }, INCLUDE_CREDENTIALS),

  /** 删除技能（DELETE /api/v1/agent/skills/{skillId}） */
  remove: (skillId: string) =>
    HttpUtils.deleteData<boolean>(`${PREFIX}/skills/${encodeURIComponent(skillId)}`, INCLUDE_CREDENTIALS),
};
```

### 2.2 错误语义（前端统一处理）

| HTTP | 错误 | 前端处理 |
|---|---|---|
| 400 | 参数非法（空 skillId/name/content） | 表单校验前置拦截，后端兜底 message.error |
| 404 | `AgentSkillNotFoundException`（技能不存在） | 提示「技能不存在（可能已被删除），刷新列表」 |
| 409 | `AgentSkillConflictException`（重复注册/乐观版本冲突） | 注册：提示「技能已存在」；更新：提示「技能已被他人修改（版本冲突），请刷新后重试」 |

> 后端异常经 `AgentExceptionHandler` 统一映射为 `{code, message, data:null}` JSON；前端在 HttpUtils 的错误分支按 `code` 分流展示。

## 3. 页面组件划分（`src/pages/ai-agent/components/`）

```
SkillsTab.tsx              // Tab 容器：列表 + 操作栏 + 编辑器 Drawer/Modal
  ├─ SkillListTable.tsx    // 技能列表表格（含启停开关、操作按钮）
  ├─ SkillEditorModal.tsx  // 注册/编辑技能（skillId/name/description/metadata/content 表单）
  └─ SkillDetailDrawer.tsx // 详情展示（正文/元数据/版本历史信息）
```

### 3.1 SkillsTab 状态模型

```ts
interface SkillsTabState {
  items: AgentSkillItem[];        // 列表
  loading: boolean;
  editing: AgentSkillSaveInput | null; // null=不编辑；非 null=编辑中（含 skillId 表示更新）
  detail: AgentSkillItem | null;  // 详情 Drawer
  pendingToggle: string;          // 正在切换启停的 skillId（乐观 UI 防连点）
}
```

### 3.2 技能列表表格（SkillListTable）

| 列 | 渲染 | 说明 |
|---|---|---|
| 技能名 | `name` + Tag 标识 `skillId` | 主展示 |
| 描述 | `ellipsis` + Tooltip | 一句话描述 |
| 启停状态 | `Switch`（受控） | **热生效**：切换即调 `setActive`，成功后 `enabled` 就地更新（乐观 UI，失败回滚） |
| 版本 | `version` | 更新时间可作并发提示辅助 |
| 更新时间 | `updateTime` 格式化 | |
| 操作 | 详情 / 编辑 / 删除 | 删除需 `Popconfirm` 二次确认 |

**启停切换交互要点**：
- 调 `setActive(skillId, !enabled)` 前先 `pendingToggle` 置位防连点；
- 成功 → 本地 `items` 更新 `enabled`；失败 → `message.error` + 回滚开关；
- 切换后不影响已开始推理，**下一轮对话即按新状态生效**（可在 Tab 提示中注明「下一轮对话生效」）。

### 3.3 技能编辑器（SkillEditorModal）

表单字段（注册/更新共用）：

| 字段 | 控件 | 校验 |
|---|---|---|
| skillId | Input（注册可编辑；更新只读） | 必填、≤64、字母数字中划线 |
| name | Input | 必填、≤128 |
| description | TextArea | ≤512 |
| content | TextArea（等宽字体，建议 8 行起） | 必填；技能正文 instructions |
| metadata | JSON 编辑区（TextArea + 校验 JSON） | 可选；格式非法则拦截提交 |

**版本并发提示**：更新提交时回传列表项当前 `version`（后端乐观 CAS 用）；若 409 冲突 → 提示「已被他人修改，请刷新后重试」并自动刷新列表。

> 演示剧本参考：技能正文建议按「当用户询问 X 时，按口径 Y 输出」的指令型写法，与 E2E 技能内容（如资产同比分析）一致，便于现场演示热生效。

### 3.4 详情 Drawer（SkillDetailDrawer）

- 展示 `name/skillId/enabled/version/createTime/updateTime` + 正文（Markdown 渲染，复用 `MarkdownContent.tsx`）+ metadata 只读 JSON。

## 4. 权限集成

- Tab 是否渲染：与现有工作台一致，页面初始化从当前用户权限列表取 `agent:skill:read`；
- 操作按钮（注册/编辑/启停/删除）按 `agent:skill:manage` 控制；
- 无 manage 权限时列表只读（启停 Switch 禁用、操作列仅「详情」）。

## 5. 交互流（关键路径）

### 5.1 在线注册 → 热生效（演示主路径）
```
点击「注册技能」→ 填写表单（skillId/name/description/content）→ 提交
  → agentSkillApi.register → 成功 message.success("技能已注册，下一轮对话生效")
  → 刷新列表（新技能 enabled=true 出现在列表）
  → 演示：切回对话 Tab 提问触发技能 → 下一轮系统提示注入技能内容
```

### 5.2 在线停用（热生效）
```
列表行 Switch 关闭 → setActive(skillId, false)
  → 成功就地更新 enabled=false
  → 演示：切回对话 Tab 提问 → 下一轮系统提示不再注入技能
```

### 5.3 更新技能内容（热生效）
```
编辑 → 更新 content → 提交（带 version）
  → 成功 message.success("技能已更新，下一轮对话生效")
  → 演示：切回对话 Tab 提问 → 下一轮系统提示注入新版内容
  （注：当前框架版本重建后旧内容可能残留，见后端测试报告观察项 O-1，前端提示语避免断言「完全替换」）
```

### 5.4 删除
```
删除 → Popconfirm 确认 → agentSkillApi.remove → 成功刷新列表移除
  （已开始推理不受影响；不影响已生成报告——技能与会话/报告生命周期隔离）
```

## 6. 与既有页面对齐的约定

- 服务封装：统一 `src/services/agent/api.ts` 的 `PREFIX`/`INCLUDE_CREDENTIALS` 模式（见 `SessionApi` 等既有封装）；
- 组件样式：`antd 5` + `antd-style`，复用 `components/pageStyles.ts` 的页面级样式；
- 表格/表单：`antd Table`/`Form`/`Modal`/`Drawer`/`Switch`；
- 反馈：`message`（操作结果）/ `Popconfirm`（删除）/ `Tooltip`（长文本）；
- 类型：`src/services/agent/types.ts` 新增 `AgentSkillItem/AgentSkillSaveInput/AgentSkillActiveInput`。

## 7. 验收清单（前端）

| # | 验收项 |
|---|---|
| 1 | 技能管理 Tab 按权限可见（无 read 权限不显示） |
| 2 | 注册：校验通过 → 列表新增 enabled=true；重复 skillId 请求被 409 拦截并提示 |
| 3 | 启停 Switch：乐观切换 + 失败回滚；切换后列表状态即时正确 |
| 4 | 更新：提交回传 version；模拟双开 409 → 提示「已被他人修改请刷新」并刷新 |
| 5 | 删除：Popconfirm 确认；删除后列表移除 |
| 6 | 详情 Drawer：正文 Markdown 渲染、metadata JSON 展示 |
| 7 | **热生效联动验证**（与后端 E2E 相同语义）：注册技能 → 对话 Tab 提问 → 回答反映技能；启停/更新 → 对话 Tab 再提问 → 反映新状态/新内容 |
| 8 | 无 manage 权限：列表只读、操作禁用 |

## 8. 后续迭代（不含本次范围）

- 技能分组/标签筛选（metadata 能力标签）与检索；
- 技能内容语法高亮/模板（复用 ontology 建模台编辑经验）；
- 技能执行沙箱（agentscope SkillToolFactory 工作目录执行，当前 MVP 仅提示注入型技能）；
- 前端观察项 O-1 对齐（框架升级后「更新=完全替换」再补前端文案）。