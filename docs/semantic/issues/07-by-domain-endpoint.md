# 07: /processes/by-domain 端点消费或下线

**对应需求:** 语义中心盘点 §6 缺失能力（P3）| 阶段: P3

**What to build:** `GET /api/v1/semantic/processes/by-domain/{domainId}`(`SemanticProcessController` L101)存在但**全仓库无任何前端/他模块调用**,属 API 噪音。先确认预留意图(盘点 §8 标"未确证"),二选一:接入真实场景(如过程列表页按域筛选改用该端点、或主线视图消费),或连同其测试一起下线,收敛对外面。

**模块归属:** semantic

**Blocked by:** 无

**Status:** backlog(待排期)

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md);若下线,须 grep 全仓(含 OpenAPI 快照/前端 services)确认零引用后再删,不留"注释掉的端点"。

- [ ] 确认意图并记录结论(接/删 + 理由)于本单
- [ ] 接入:找到真实调用方并改造(过程页域筛选是首选候选),端点进契约测试
- [ ] 或下线:删除端点与对应测试,同步 controller 层文档
- [ ] 契约测试通过
