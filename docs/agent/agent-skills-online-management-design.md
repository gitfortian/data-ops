# Agent Skills 在线管理设计方案（SkillBox Integration）

> 状态：方案设计（实现范围见 §7）｜日期：2026-08-30｜模块：yak-ops-business-agent
> 定位：填补长期预留的缺口 —— `AgentDynamicConfigService` 登记注释已指明
> "工具注册为静态 @ConditionalOnProperty，完全热切换需 SkillBox，见 PT-307"。
> 本方案借 agentscope 框架 skill 能力（`io.agentscope.core.skill`）落地该缺口。

---

## 1. 背景与目标

### 1.1 现状缺口

- 智能体工具（`AgentToolBox`）与能力提示当前为**启动期静态装配**：工具 Bean 在 Spring 启动时注册进
  `Toolkit`，`AgentRuntime.doAssemble()` 一次性构建 ReActAgent，中间件链路固定。
- 运行时动态配置（`AgentDynamicConfigService`，per-key、调用时现读、≤1s 微过期）已覆盖
  memory/observability/llm-timeout 等开关，但**技能（skill）/工具的在线增删与启停没有入口**。
- 面对"现场给客户演示本体智能体新增一个分析技能并立即生效"这类诉求，当前必须改代码+重启。

### 1.2 目标

1. **技能在线管理**：通过平台 API 在线注册 / 查看 / 启用 / 停用 / 删除技能（skill），无需重启。
2. **热生效**：下一次推理立即看到技能变化（SkillBox 现读 + DynamicSkillMiddleware 注入系统提示）。
3. **持久化**：技能内容与启停状态落库（`yak_agent_skill`），重启后保留。
4. **权限与留痕**：新增 `SKILL_READ` / `SKILL_MANAGE` 权限码（boot migration 登记），走平台权限体系。
5. **低侵入**：不破坏既有装配与测试（AgentRuntime 构造签名不变；现有 10 参装配与全部测试不受影响）。

### 1.3 借 agentscope 的能力（确认可用）

仓库已依赖 `agentscope-core 2.0.2`，经 javap 核实内置完整 skill 能力：

| 组件 | 职责 | 关键方法 |
|---|---|---|
| `io.agentscope.core.skill.SkillBox` | **技能运行时注册表（在线管理核心）** | `registerSkill/removeSkill/setSkillActive/exists/getSkillPrompt(SkillFilter)/getAllSkillIds/isSkillActive` |
| `io.agentscope.core.skill.DynamicSkillMiddleware` | 推理中间件：每次 `onSystemPrompt` 将启用技能注入提示 | `(List<AgentSkillRepository>, Toolkit[, SkillFilter[, boolean, Path]])`；`getCurrentSkillBox()`/`rebindToolkit()` |
| `io.agentscope.core.skill.repository.AgentSkillRepository` | **技能持久化通道接口** | `getSkill/getAllSkillNames/getAllSkills/save(List,boolean)/delete/skillExists/getRepositoryInfo/setWriteable/isWriteable` |
| `io.agentscope.core.skill.AgentSkill` | 技能值对象 | `(id/name/description/metadata/content/resources)`；`Builder` |
| `io.agentscope.core.skill.SkillFilter` | 白/黑名单过滤（在线启停也可经此收敛） | `all/none/only/except/enable/disable/overlay` |
| `io.agentscope.core.tool.Toolkit` | 工具组（skill 与工具组绑定） | `createSkillToolGroup/findSkillToolGroupsByActivateOnSkill/updateToolGroups` |

**结论**：SkillBox 天然支持"在线启停 + 提示注入"，AgentSkillRepository 提供了持久化接缝 —— 这正是 yak 侧
"技能在线管理"缺的两块。本方案只补：①DB 持久化适配器（实现 agentscope 接口）；②平台管理 API/服务；
③运行时装配缝接入；④权限登记。

---

## 2. 架构与数据流

```
                    ┌───────────────────────────── yak agent 模块 ─────────────────────────────┐
 平台管理员 / 客户    │                                                                          │
   POST /api/v1/agent/skills              ┌───────────────────────────┐                          │
   PUT   .../skills/{name}/active         │ AgentSkillController(新)   │                          │
   DELETE .../skills/{name}  ───────────▶ │  (权限: SKILL_READ/MANAGE)  │                          │
                                          └──────────┬────────────────┘                          │
                                          AgentSkillManageService(新,稳定 Facade)                │
                                            ①落库 ②热切换                                        │
  ┌──────────────┐  ② SkillBox.setSkillActive /    ┌──────────────┐   ① write/read   ┌──────────┐
  │ AgentRuntime │◀── registerSkill/removeSkill ────│  SkillBox     │◀────────────────│ AgentSkill│
  │ doAssemble() │──→ DynamicSkillMiddleware ──────▶│ (agentscope)  │   AgentSkill    │ Repo DB  │
  │ middleware链 │    (每次推理 onSystemPrompt 现读)  └──────────────┘   Repository     │ Adapter  │
  └──────┬───────┘                                        │             Adapter(新)      └──────────┘
         │ ReActAgent.stream  → LLM 系统提示含启用技能                                     │ MyBatis
         ▼                                                                                ▼
    yak_agent_turn/event 留痕链（不变）                              yak_agent_skill(id, name, description,
                                                                    metadata_json, content, status ENABLED/
                                                                    DISABLED, version, create/update)
```

**两条生效路径（双写一致）**：
- **持久路径**：管理 API → `AgentSkillManageService` → DB adapter（落库，重启保留）；
- **热路径**：管理 API → `AgentSkillManageService` → `AgentRuntime.getSkillBox()` → `SkillBox.setSkillActive/registerSkill/removeSkill` → 下一次推理 `onSystemPrompt` 现读 → 提示注入生效（无重启、无缓存）。

**设计纪律对齐**（与既有架构一致）：
- Controller 只进入稳定 Facade（`AgentSkillManageService`），不触碰内部角色；
- SkillBox 状态是**运行时真相**（热生效），DB 是**持久真相**（重启还原）；双写时 DB 失败即返回错误并回滚热操作；
- 技能内容按 Markdown 语义（name/description/instructions）经 `MarkdownSkillParser` 兼容解析，AgentSkill 承载。

---

## 3. 持久化设计（`yak_agent_skill`）

```sql
-- V13__agent_skill.sql（yak-agent 命名空间）
CREATE TABLE IF NOT EXISTS `yak_agent_skill` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `skill_id`      VARCHAR(64)  NOT NULL COMMENT '技能标识（全局唯一，逻辑名）',
  `name`          VARCHAR(128) NOT NULL COMMENT '技能名（展示名）',
  `description`   VARCHAR(512) NOT NULL COMMENT '技能一句话描述（注入提示用）',
  `metadata_json` TEXT         NULL     COMMENT '技能元数据（能力标签等，JSON）',
  `content`       LONGTEXT     NOT NULL COMMENT '技能正文（instructions 等，注入 System Prompt）',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED（在线启停持久态）',
  `version`       INT          NOT NULL DEFAULT 1     COMMENT '乐观版本（并发编辑防覆盖）',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_id` (`skill_id`)
) ENGINE=InnoDB COMMENT='智能体技能（skills 在线管理持久化）';
```

- `skill_id` 全局唯一逻辑名（API 路径主键，匹配 agentscope `getSkill(name)` 语义）；
- `status` 为**在线启停持久态**（ENABLED/DISABLED），重启时 DB adapter 读出，SkillBox 按此还原；
- `version` 乐观锁：并发编辑（两个管理员同时改同一技能）后者失败，避免静默覆盖；
- 更新为**全字段覆写 + version+1**（技能为小文档，不以 diff 存储）；`metadata_json` 保留能力标签供筛选。

---

## 4. 组件设计

### 4.1 DB 持久化适配器 `AgentSkillRepositoryAdapter`（新）

实现 `io.agentscope.core.skill.repository.AgentSkillRepository`（直接接 agentscope），内部落库 `yak_agent_skill`：

- `getSkill(name)` → `AgentSkill`（DB 行 → agentscope 值对象；`metadata` 携带 `status`）；
- `getAllSkillNames()/getAllSkills()` → 全量（仅 ENABLED 进入运行时提示，DISABLED 由 SkillBox 状态管理）；
- `save(List<AgentSkill>, boolean overwrite)` → upsert（`overwrite=false` 且存在时报冲突）；
- `delete(name)` / `skillExists(name)` / `getSource()`="db:yak_agent_skill" / `isWriteable()`=true；
- 并发保护：`version` 乐观锁；启停经事务更新 `status`。

### 4.2 管理服务 `AgentSkillManageService`（新，稳定 Facade）

领域方法（全部带权限语义，由 Controller 注解强制）：

| 方法 | 行为 |
|---|---|
| `register(SkillInput)` | 校验 name/description/content 非空 → 落库（status=ENABLED）→ 热注册 `SkillBox.registerSkill` → 返回 skill_id |
| `update(skillId, SkillInput)` | 乐观版本校验 → 覆写落库 → 热更新（移除+重注册） |
| `list()` / `detail(skillId)` | DB 真相（含状态） |
| `setActive(skillId, boolean)` | 落库 status → `SkillBox.setSkillActive(skillId, active)`（热切换） |
| `remove(skillId)` | 落库删除 → `SkillBox.removeSkill`（热卸） |

- 输入约束与既有工具装配同标准：skill 名/内容不得为空、长度上限（防提示注入失控）；
- `SkillInput` 为 `record(name, description, metadata, content)`（Controller 直接映射）。

### 4.3 运行时装配缝（AgentRuntime 低侵入改造）

- `AgentRuntime` **构造签名不变**；新增 `volatile AgentSkillRepository skillsRepository`，
  经 `@Autowired(required=false)` 方法注入（测试 new 时不注入，行为与现状完全一致）；
- `doAssemble()` 中若注入非空：
  1. `DynamicSkillMiddleware skillMiddleware = new DynamicSkillMiddleware(List.of(skillsRepository), toolkit);`
  2. `builder.middleware(skillMiddleware)`（追加至中间件链，**放在提示组装之后**：技能提示晚于能力路由贡献者叠加，语义为"技能=会话内可调用的专业方法库"）；
  3. 暴露 `getSkillBox()`（从 middleware `getCurrentSkillBox()`，供管理服务热切换）；
- 懒组装保持：首次 `stream()` 触发 `doAssemble()`，技能仓库在首次推理时装载 → 注册后的技能即时可见。

### 4.4 Controller `AgentSkillController`（新）

`@RequestMapping("/api/v1/agent/skills")`（模块启用条件化，与 AgentController 一致）：

| 端点 | 权限 | 说明 |
|---|---|---|
| `POST /api/v1/agent/skills` | `SKILL_MANAGE` | 在线注册技能（body=SkillInput） |
| `GET /api/v1/agent/skills` | `SKILL_READ` | 技能列表（含 ENABLED/DISABLED 状态） |
| `GET /api/v1/agent/skills/{skillId}` | `SKILL_READ` | 技能详情 |
| `PUT /api/v1/agent/skills/{skillId}` | `SKILL_MANAGE` | 更新技能（乐观版本） |
| `PUT /api/v1/agent/skills/{skillId}/active` | `SKILL_MANAGE` | 启用/停用（body={active:true\|false}）——**热生效** |
| `DELETE /api/v1/agent/skills/{skillId}` | `SKILL_MANAGE` | 删除技能（热卸） |

错误语义沿用 AgentExceptionHandler：非法参数 400、冲突（乐观版本/已存在）409、不存在 404。

### 4.5 权限码

`AgentPermissionCode` 新增：

```java
public static final String SKILL_READ = "agent:skill:read";
public static final String SKILL_MANAGE = "agent:skill:manage";
```

boot migration `V2013__register_agent_skill_permissions.sql`（对齐 V2004/V2012 登记模式）。

---

## 5. 在线启停热生效时序（核心场景）

```
管理员: PUT /api/v1/agent/skills/asset-yoy  {active:false}
  1. AgentSkillManageService.setActive("asset-yoy", false)
  2. 事务: UPDATE yak_agent_skill SET status='DISABLED' WHERE skill_id=?
  3. SkillBox.setSkillActive("asset-yoy", false)   ← 运行时立即切换
  4. 下一次用户提问 → stream() → DynamicSkillMiddleware.onSystemPrompt
     → SkillBox.getSkillPrompt(filter) → 不包含 asset-yoy → LLM 系统提示无该技能 ✓
     （对正在推理的轮次不中断——断连/取消语义不变，下一次轮次生效）
```

启用同理（`setSkillActive(name, true)`）。**"改技能不重启、下一问即生效"** 是演示话术落点。

---

## 6. 测试方案（与既有测试范式一致）

| 层 | 用例 | 做法 |
|---|---|---|
| 适配器 | CRUD + 乐观版本冲突 + 启停持久化 | 内存/模拟 mapper（对齐 AgentTurnRepositoryAdapterTest 范式） |
| 管理服务 | register→list→setActive(false)→skill 不再注入 | mock SkillBox + 内存 DB |
| 运行时热生效 | **E2E：注册技能→推理（fake LLM 截获系统提示含技能）→停用→再推理（不含）** | 复用 AgentResumeIntegrationTest 的 fake OpenAI HttpServer 模式，从 LLM 请求体断言提示内容 |
| Controller | 权限注解 + 参数校验（400/409/404） | MockMvc 或直接注解断言（对齐既有 controller 测试） |

留痕：`yak-ops-business-agent/target/surefire-reports/*Skill*Test.txt`。

---

## 7. 实现范围（本次交付）

1. `V13__agent_skill.sql`（migration）
2. `AgentSkillMapper` + `AgentSkillPO`（dao）
3. `AgentSkillRepositoryAdapter`（实现 agentscope 接口，落库）
4. `AgentSkillManageService`（稳定 Facade）
5. `AgentRuntime` 装配缝（构造不变 + required=false 注入 + getSkillBox 暴露）
6. `AgentSkillController` + 权限码 + `V2013` 权限迁移
7. 测试：适配器 CRUD / 管理服务启停 / 运行时热生效 E2E

**非目标（显式拒绝，保持范围）**：skill 代码执行沙箱（agentscope 的 SkillToolFactory/工作目录执行暂不启用——
MVP 只做"提示注入型技能"：instructions 进 System Prompt，不开代码执行，避免执行面扩大）；Nacos 仓库
（1.0.11 扩展不在 2.0.2 harness 依赖线内，需要时后续接）。这两个显式拒绝与「语义单线化 T3b 拒多跳 JOIN」
同一纪律：能力缺口显式说、不静默。

## 8. 验收口径

- `./mvnw -pl yak-ops-business/yak-ops-business-agent -am test -Dtest=*Skill*Test` 全绿；
- 在线注册→下一问生效、在线停用→下一问消失（E2E 断言 LLM 提示）；
- 重启后技能与启停状态保留（DB 持久化断言）；
- 既有全部测试不受影响（AgentRuntime 构造未变）。