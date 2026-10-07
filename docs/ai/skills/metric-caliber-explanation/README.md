# 指标口径解释 Skill

方法包不随部署自动注册。管理员在测试项目先核对内容与模型能力，再通过原 Skill 管理接口提交 register.json；接口创建后立即启用，不能把测试注册当成无影响的草稿。固定 skillId `metric-caliber-explanation`，正文与 SKILL.md 一致。

入口是原指标管理编辑框；需完整详情、未改动的已保存版本及 Metric READ/UPDATE、Agent CHAT_RUN/SESSION_READ 权限。生成只读版本事实，带入只改业务说明。没有数据行、任意 SQL 或写工具。

真实模型验收 PENDING：原子/派生/复合分别核对事实和业务说明，记录 Skill version/hash、指标版本/digest、trace，再人工保存回读；撤权、版本冲突、缺快照和切换项目需阻断。参见 F-025 与 V17 验收记录。
