# 治理 Skill 评测材料

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
