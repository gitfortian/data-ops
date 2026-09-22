# Lifecycle Requirements

> 只描述模块需要什么，不描述怎么实现。按 ticket 追加；行为变更先改本文件再写代码。

## Ticket 80：模块骨架 + 菜单权限

- Maven 模块接线（bom/business/boot），Flyway `yak-lifecycle` V1 建 5 张表，yak-security 菜单迁移 V2031（数据治理→数据生命周期：策略管理/TTL 监控/存储统计），错误码 47001~47012，权限码 `data-lifecycle:read/create/update/delete`。
- 契约文件集（本目录 6 份）创建。

## Ticket 81：TTL 策略 CRUD + 分层默认策略

- 策略列表分页：范围（层默认/自定义）、层、关键词筛选。
- 新建/编辑自定义策略：名称、层级、粒度（默认 DAY）、热/冷/销毁三段（各自可空=不限，销毁空=永久）；校验 hot≤cold≤destroy。
- 层默认策略唯一内置，不可删除，可改数值；`initialize-layer-defaults` 按预置模板补齐缺失层并登记项目级重试/快照调度。
- D1：模型所在层无策略但分层配置有 `lifecycle_days` 时，合成只读虚拟策略展示。

## Ticket 82：模型绑定策略

- 模型生命周期解析：绑定 > 层默认 > 虚拟兜底 > UNSET。
- 绑定/解绑覆盖策略；每次写操作落审计（fail-open）。
- D5 状态机 deriveState：无下发记录=DRIFT；最近失败=FAILED；换策略或策略改后=DRIFT；否则 APPLIED。

## Ticket 83：TTL 语句生成

- D2 按目标表真实方言生成：Doris/StarRocks→dynamic_partition（end=3、prefix=p、天数按粒度向上取整换算分区数）；Paimon→partition.expiration-time（粒度对应 formatter）。
- D4 永久：Doris `enable="false"`；Paimon 注释 no-op 不下发。
- D3 不支持方言：按 Doris 语法生成仅复制，`writable=false`。
- 标识符反引号包裹并剥离内嵌反引号，杜绝注入。

## Ticket 84：TTL 预览 + 确认令牌

- Doris 且网关可用：SHOW PARTITIONS 归类热/冷/将删（≤50 条将删清单），不可解析名标 estimated（D6）。
- 其它存储/不可达：降级为按策略推算，UI 明示"估算"。
- 批量预览出 HMAC 确认令牌（项目+模型集+5 分钟窗），防"预览 A 下发 B"。

## Ticket 85~86：策略下发 + 失败重试

- 校验令牌→逐模型执行（仅 writable=true）→写 `yak_lc_dispatch_record`（成功/失败、语句快照、分区计数、policyUpdatedAt）。
- 失败记录进重试：上限 5 次，退避 30min×attempts，超限 EXHAUSTED；手动重试与调度自动重试同通道；业务表是唯一事实源（D7）。

## Ticket 87：TTL 监控 + 存储统计

- 监控汇总：各状态数量、最近清理（成功下发的分区删除数）、异常告警列表；模型分页支持状态/层/关键词筛选与单模型重新下发。
- 存储：每日快照各层表大小（parseSize 容错），统计页给各层量/趋势/按单价折算的月成本；单价存 `yak_lc_setting`。

## Ticket 88~89：前端

- 策略管理页（模板预填、三段带"永久"开关）、TTL 监控页、存储统计页；模型详情生命周期 Tab + 批量下发向导（预览→确认→结果）。
- 交互总原则：**能选择就不填、能默认就不留空**；空态给引导（一键初始化分层默认策略）。
