# 07: 新建向导方言选择开放

**对应需求:** 模型工作台盘点 §6 缺失能力（P2）| 阶段: P2

**What to build:** 平台已宣称并实现六方言（类型目录/校验/DDL 全配套），但新建向导硬编码 `dialect:'MYSQL'`（`ModelCreateWizardDrawer.tsx` handleNext），其余五方言只能建完进详情"编辑"再改，与设计台账的方言主张不一致。向导开放方言下拉（`ModelDialect` 六枚举）；走逆向导入路径时按数据源 dbType 经 `constants.ts` 的 `dialectOfDbType` 自动推导并允许改。

**模块归属:** modeling（纯前端小单）

**Blocked by:** 无

**Status:** 已实现（2026-09-22，代码与 tsc 已过，实机验证待 dev server 启动后补做）

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md)；后端 `ModelDialect` 枚举零改动；切换方言后进入详情时类型目录/校验自动按新方言生效（现有能力，勿在向导内重复实现校验）。

- [x] 向导表单增"方言"选择，默认 MYSQL，六枚举齐全
- [x] 逆向导入创建的模型按来源 dbType 推导方言
- [x] 编辑已有草稿模型方言时给出结构校验重跑提示（类型可能不兼容）
- [x] 派生建模创建的草稿继承上游/目标分层方言的现状回归确认

**实现注记（纯前端，后端零改动）:**
- `components/ModelCreateWizardDrawer.tsx`:新增「目标方言」必填下拉（`MODELING_DIALECT_OPTIONS` 六枚举，initialValues 默认 MYSQL），`handleNext` 由硬编码 `'MYSQL'` 改为 `values.dialect`。
- `components/ModelEditModal.tsx`:方言值与原值不一致时表单项下出警示文案"保存后请到「表结构」重新执行「校验」"（Form.useWatch，不新增弹窗）。
- `components/ReverseImportDrawer.tsx`:dbType→方言推导原本已实现（含改选），本单删除其本地 `DIALECT_OPTIONS`/`dialectOfDbType` 副本、统一引用 `constants.ts`，消除双份方言清单漂移风险。
- 派生方言现状确认:派生预览 `previewModelingDerive`/指标反推 `getModelingMetricDraft` 的方言参数均由详情页 `structure?.dialect` 传入（跟随模型自身方言），前端无硬编码；模型方言本身自 07 起可由向导/编辑/逆向导入三入口决定。
- 验证:`npx tsc --noEmit` 182 错误(≤183 基线)、四个改动文件零错误;向导建非 MySQL 模型→类型目录/DDL 按新方言生效等实机项待 :8000 启动后补做。
