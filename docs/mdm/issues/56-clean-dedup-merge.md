# 56: 主数据清洗·去重与合并

**对应需求:** requirement.md 3.4 主数据清洗/design.md 3.4/menu.md 3.4|阶段:P1

**What to build:** 管理员进入"主数据管理 → 主数据清洗"(menuCode `mdm-cleansing`),配置去重规则(如手机号相同 + 姓名相似)发现重复组,对重复组执行合并/保留:合并后保留主记录(master_id 不变,source_ids 合并,其他记录标记 MERGED,version 递增)。质量检查(完整性/格式)跳数据质量,清洗只管去重、合并、标准化(57)。

**模块归属:** data-ops-business-mdm(+quality 参照)

**Blocked by:** [55 采集执行](./55-collect-execution.md)

**Status:** in-review(实现完成,待验收)

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md) —— 契约先行(更新 mdm 契约:清洗规则为实体级配置,新增 `mdm_clean_rule` 表,dev-plan D-M5);project_id 服务端可信上下文,不建物理外键;合并为显式操作,合并前预览待合并记录,合并后版本/状态变更可追溯;去重发现结果服务端聚合,禁止无界 list() 后内存统计(home-overview-contract);本 ticket 注册 `mdm-cleansing` 菜单(V2027)。

- [x] `mdm_clean_rule` 表 Flyway 已合入(V7:project_id/entity_id/rule_type(DEDUP)/rule_name/rule_expr(JSON)/enabled/sort_order/create_time/update_time);合并历史表 `mdm_merge_log` V8 随本票合入
- [x] 注册 `mdm-cleansing` 菜单(V2027)与页面路由,菜单契约测试通过
- [x] 去重规则配置:按实体配置匹配字段、匹配方式(精确/模糊),支持组合条件(AND 全部命中/OR 任一命中);相似度阈值随 AI 匹配演进,本期匹配方式 EXACT/FUZZY(模糊=trim+小写规范化)
- [x] 按规则执行去重发现,结果服务端聚合返回重复组(组内记录、匹配依据、置信度),分页展示;聚合在 DB 侧 GROUP BY 完成,无无界 list 后内存统计
- [x] 重复组支持"合并"(选主记录:保留属性合并 + source_ids 合并,其余记录 status=MERGED,version 递增,写合并日志)/"保留"(忽略该组,前端不执行任何操作)
- [x] 合并预览:展示各记录属性对比与合并结果,确认后执行;合并不可回滚但记录/日志可追溯
- [x] 清洗页提供"前往数据质量"跳转(完整性/格式检查归数据质量)
- [x] 契约测试通过

**验证记录(2026-09-17):**

- MDM 模块 `-am test` 通过:新增 `MdmCleanServiceTest` 11 例(规则校验/去重聚合/合并预览与执行/越界校验),合计 42 例全绿。
- `data-ops-boot -am compile` 通过;菜单契约测试 `navigationMenuContract.test.ts` 5/5 通过(V2027 已登记)。
- 前端 tsc 基线 199 个既有错误保持 0 新增(mdm/cleansing 与 services/mdm 无新增错误)。
- 错误码追加 44022 去重规则不存在/44023 去重规则不合法/44024 合并请求不合法(REQUIREMENTS Ticket 56 段同步)。
- 去重发现为 DB 侧 GROUP BY(JSON_EXTRACT 匹配键 + CHAR(1) 拼接 AND 键),属性编码经 `[A-Za-z0-9_]` 校验后嵌入 SQL,防注入。
