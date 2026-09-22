# Lifecycle Domain

## 核心概念

### TTL 策略（Policy）

热/冷/销毁三段保留天数 + 分区粒度。`scopeType=LAYER_DEFAULT` 时每层至多一条内置策略；`CUSTOM` 供模型覆盖绑定。**销毁=null 即永久保留**。

分层预置（requirement 3.2）：ODS 7/30/90 · DIM 永久 · DWD 30/180/730 · DWS 90/365/1095 · ADS 365/730/永久。

### 模型绑定（Binding）

模型默认继承其分层的层默认策略，可绑定任意策略覆盖。无绑定行 = 继承（或 D1 兜底合成）。

### 语句生成（Statement）

按目标表**真实方言**生成（D2）：Doris/StarRocks → `dynamic_partition.*`；Paimon → `partition.expiration-*`；其它方言按 Doris 生成但 `writable=false` 仅可复制（D3）。

### 下发（Dispatch）

预览（分区归类 + 将删列表）→ HMAC 确认令牌（5 分钟窗）→ 执行 ALTER → 记录。失败进重试队列，上限 5 次、退避 30min×attempts，超限 EXHAUSTED。

### 监控状态机（D5）

`UNSET`（无策略）/ `APPLIED` / `DRIFT`（未下发或策略已改）/ `FAILED`（最近一次失败/重试中/耗尽）。

## 不变量

1. **项目空间归属**：全部业务行带 `project_id`，只取 `CurrentProject` 服务端上下文。
2. **层默认策略唯一**：`(project_id, layer_code, LAYER_DEFAULT)` 至多一条有效策略；内置策略不可删，仅可改数值。
3. **销毁段单调性**：hotDays ≤ coldDays ≤ destroyDays（各自可空）；违反报 47002。
4. **生成/执行分离（D3）**：`writable=false` 的语句任何通道都不下发,网关侧同样拒绝。
5. **永久即自治（D4）**：Doris 生成 `dynamic_partition.enable="false"`；Paimon 不设置 `partition.expiration-time` 即为永久保留。
6. **令牌即事实（design 3.3）**：下发必须携带与"当前模型集+项目"一致的确认令牌（47009）。
7. **业务表是重试事实源（D7）**：重试状态只存 `yak_lc_dispatch_record`，调度引擎仅触发。
8. **D8 默认值**：动态分区 end=3、prefix=p、粒度默认 DAY。
