# V16 验收记录

合同 F-024。状态 IMPLEMENTING；真实模型验收按用户既有确认单列待办。

| 验证 | 状态 | 证据 |
| --- | --- | --- |
| 授权前零读取、有界字段目录、截断/fresh/不可用 | 本地通过 | MappingSuggestionQueryAdapterTest |
| 未知字段、重复/超量输出、目标及源漂移 | 本地通过 | ModelMappingGatewayTest |
| 条件保存冲突前零写、fresh 校验、保留标准关联 | 本地通过 | MappingServiceTest |
| SDK 合成/原生、预算、原场景并发回退与历史 | 本地通过 | StandardMatchRuntimeTest |
| 原表单采纳、作用域变化、晚响应、手工保存 | 本地通过 | 前端场景与映射编辑测试 |
| 完整 CI / 合并 | 待完成 | GitHub 账户支付/额度限制阻止启动 runner；没有放宽 gate |

真实用例 MM-01：测试用户在实际模型单列选择源表，补充角色说明，生成并核对 sourceColumn/两侧类型/理由，带入、检查表达式、人工保存并回读映射，记录用户/项目/模型、Skill 版本/hash、定义/源目录指纹和 trace。PENDING。

MM-02：撤销 Modeling 或 Datasource 读取权限，生成/采纳失败且不泄漏目录。MM-03：其它用户改映射或目标字段后，旧候选或旧编辑上下文拒绝。MM-04：外部 DDL 删除/改类型，fresh 交付/采纳校验拒绝漂移。MM-05：切换字段/项目/源表或编辑表单时晚响应不带入。MM-06：停止轮次后重新读取真实状态，不伪报已停止。均 PENDING。

本地收口：30 模块 Maven test 成功；最终针对源授权、条件保存、候选校验与 SDK 运行的 29 项测试通过。前端 140 suites / 731 tests 通过，类型检查维持原 139 项基线，生产构建及 manifest 成功；架构扫描覆盖 77 reactor / 3174 production Java files，通过前端边界检查及 git diff --check。未把自动化结果作为真实模型、真实外部 DDL 并发或登录 E2E 通过证据。
