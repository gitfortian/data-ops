# 治理 Skill 评测材料

## 页面发现与登记

智能助手 → 技能管理现提供八份已有材料的模板入口（五份场景模板、三份治理方法候选）。上方“已注册技能”仍只显示当前项目的数据库配置；下方模板可查看正文，管理员可带入原注册表单，核对或调整后明确保存。保存沿用注册即启用的原接口语义；已经注册的同标识模板不会覆盖现有配置，启停与编辑仍在原列表操作。页面展示不代表已加载，也不代表真实模型验收通过。

前端只读展示副本由 `node scripts/ai/skill-template-catalog.mjs --write` 从本目录的正文与现有 `register.json` 生成。修改材料后同步生成，架构测试会检查漂移；运行时仍只消费 DB 注册的 Skill。

## 治理方法候选

以下三份是管理员维护 Skill 的候选正文。尚未注册、未默认启用、未经过真实模型验收；不要把文件存在视为框架已经加载。运行时仍以现有 DB Skill 管理为准，没有新增项目私有技能范围、资源文件执行或工具自动激活。

使用现有技能页面把名称、说明、正文提交到固定测试环境，在相同问题/部署模型下对照无 Skill 与当前 Skill。记录实际 execution-contract 的 skillPromptHash / loadedSkillHash、Skill 管理版本及启停审计。至少执行题集中的 asset-read / asset-query-injection / quality-history / quality-rules / skill-scope，并人工核验原源域证据。停用、删除与冷启动效果有工程回归，真实语义与 T6/T9 仍待验收。

- asset-interpretation/SKILL.md：资产解读与证据缺口。
- quality-explanation/SKILL.md：固定历史执行事实、假设/缺口、人工检查与受控回源；第四版候选正文，仍未注册/默认启用。
- candidate-review/SKILL.md：规则/描述候选校验与原编辑器交接。

正式开放前按真实评测逐份决定是否启用；不因全量默认启用这些材料而扩大本批产品范围。

V15 首个受控场景材料见 [standard-match](./standard-match/README.md)。它按当前场景合同绑定 Skill，仍需管理员明确登记，部署不自动启用。

V16 来源字段场景材料见 [model-field-mapping](./model-field-mapping/README.md)，沿同一 DB 管理和管理员登记规则。


[指标口径解释](metric-caliber-explanation/README.md)：固定版本事实与业务说明草稿，原编辑器人工保存。

## F-028 指标定义草稿

metric-definition-draft 提供有界类型草稿；人工导入/启用并复用原 Skill 版本核对。标准批量复用 standard-match；历史解释复用 metric-caliber-explanation，历史查看只读。

[F-032 指标发布前版本变更核对](metric-change-review/README.md)：固定版本对，只读交付，按原管理员登记规则启用。
