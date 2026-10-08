# V15：场景 Skill 与字段类型标准匹配

实施权威：[F-023](../product/features/F-023-skill-standard-match.md)，状态 IMPLEMENTING。工程实现不等于真实匹配质量已验收。

## 用户路径

在模型结构编辑器填写字段名、类型和业务说明，打开原标准助手，在 AI 类型标准匹配中输入可选检索词并生成。系统读取当前项目最多 20 个启用 TYPE 标准；截断会提示缩小范围。候选展示标准名称、编码、类型、版本和匹配理由；信息不足时保留待确认项，修改字段草稿或检索词后发起新轮。

点击“带入类型引用”前重新核验来源和 Skill，只修改当前字段 stdTypeId。人工保存仍走原 Modeling 校验/审计；编辑上下文的指纹通过 If-Match 进入原结构事务，过期结构拒绝覆盖。随后原页面回读保存结果。原结构读取/发布快照接口保持兼容，新增编辑上下文读写接口组合结构和条件保存指纹；保存回执在同一事务内读取，用户保存期间继续编辑时保留草稿并推进到本次保存的基线。

继续复用原持久化轮次、SSE、历史与准确停止。首期不新增等待状态：questions 是完成结果中的待确认清单，补充业务说明后重新生成。停止请求固定原 turnId，并读取实际终态；网络不明时保留会话回链，避免重复提交。

## SDK 复用与平台边界

每次场景调用使用独立 ReActAgent/Toolkit/DynamicSkillMiddleware，官方 StateStore 和原轮次身份/预算继续复用。RuntimeContext 中的 SkillFilter 只显示 standard-match；只读调用视图固定实际正文与版本/hash，加载/模型调用/交付/带入前检查源目录。在线管理表仍是唯一 Skill 定义来源。

SDK 结构化 call 提供结果对象；原生 response_format 和本次调用的 generate_response 均经过协议测试。兼容网关默认使用合成工具，原生能力的两项声明统一位于 application-ai.yaml，需按实际网关验证后开启。合成工具不在 Toolkit 中，onActing 显式检查任务范围与预算，旧任务不放宽未知工具。

方法步骤在 [standard-match Skill](./skills/standard-match/SKILL.md)，源域只读工具与确定性校验仍在代码。正文使用框架加载器，平台适配只约束范围/路径与活版本；不开放资源、脚本、Shell 或额外工具激活。请求结束关闭 SDK Agent，文本作用域使用稳定空工作目录，避免每轮创建 SDK 临时目录及关闭钩子。

最终候选在服务器完成来源校验后编码到现有消息传输块，同一 StateStore 消息回写后才发布。这个块是已有历史/SSE 协议的兼容载体，Skill 和模型不能生成它来绕过 SDK schema 或来源校验。

## 启用与验证

先配置 application-ai.yaml 的模型接入及原 Agent 权限。试点管理员按 [Skill 登记说明](./skills/standard-match/README.md)审阅后显式登记启用。代码部署不会自动登记 Skill；缺失、停用、来源失败分别阻止候选交付。

验证记录：[V15 验收](./acceptance/2026-10-07-v15/README.md)。真实模型/登录用户验收和人工效果对照按用户确认单独保留 PENDING。
