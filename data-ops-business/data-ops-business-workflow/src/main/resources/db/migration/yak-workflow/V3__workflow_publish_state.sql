-- 发布态枚举收敛（契约 C1）：工作流定义状态字面量 ONLINE → PUBLISHED。
-- 读取侧由 PublishState.of 宽松兼容历史值，此迁移把存量数据一次性归一。

UPDATE yak_workflow_definition SET status = 'PUBLISHED' WHERE status = 'ONLINE';
