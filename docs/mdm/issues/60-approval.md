# 60: 主数据审批(申请/审批流/版本)

**对应需求:** requirement.md 3.5 主数据审批/design.md 3.5/menu.md 3.5|阶段:P2

**What to build:** 管理员进入"主数据管理 → 主数据审批"(menuCode `mdm-approval`):主数据变更申请(CREATE/UPDATE/MERGE/DELETE,含变更内容对比)→ 审批流(一级数据管理员、二级数据治理负责人,可配置)→ 通过后生效(改 mdm_record + version 递增)/拒绝;版本管理(记录变更历史,可回溯)。审批是主数据特有的核心能力(design.md 3.5 复用率 0%,全部新建)。

**模块归属:** data-ops-business-mdm

**Blocked by:** [55 采集执行](./55-collect-execution.md)

**Status:** ready-for-agent

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md) —— 契约先行(更新 mdm 契约:审批流为 MDM 新建,版本管理参照 modeling 版本模式,不重复造轮子);project_id 服务端可信上下文,不建物理外键;变更未审批不得生效;审批动作(通过/拒绝)留痕(申请人/审批人/时间);版本号单调递增,变更可回溯;本 ticket 注册 `mdm-approval` 菜单(V2028)。

- [ ] `mdm_change` 表 Flyway 已合入(V11,字段参照 requirement.md 2.5:project_id/entity_id/master_id/change_type(CREATE/UPDATE/MERGE/DELETE)/change_content(JSON)/applicant/approver/approval_status/approval_time/create_time)
- [ ] 注册 `mdm-approval` 菜单(V2028)与页面路由,菜单契约测试通过
- [ ] 变更申请:选实体/记录 → 变更类型 → 变更内容(属性值对比,变更前后差异展示)→ 提交审批(记录申请人)
- [ ] 审批流:一级(数据管理员)→ 二级(数据治理负责人),审批流级别可配置(1 或 2 级)
- [ ] 审批处理:通过 → 变更生效(改 mdm_record,version 递增);拒绝 → 记录审批意见,申请可撤回
- [ ] 审批列表:待我审批/我发起的/全部,按状态筛选;审批动作留痕
- [ ] 版本管理:记录级变更历史(master_id 维度),可查看各版本属性快照并回溯(只读展示)
- [ ] 契约测试通过

**验证记录(待填):**
