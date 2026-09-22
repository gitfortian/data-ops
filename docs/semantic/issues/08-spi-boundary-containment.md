# 08: SPI 泄漏对象收敛（api 包边界名实相符）

**对应需求:** 语义中心盘点 §6 缺失能力（P3）| 阶段: P3(建议提至 P2 排在 04 之前)

**What to build:** `DEPENDENCIES.md` 自我声明"消费方仅经 api 包 SPI、实现不得泄漏内部类型",但现实是消费方直接 import api 包之外的 domain 对象(`BusinessDomain`/`Standard`/`StandardField`/`WarehouseLayer`)——modeling 23 处 import 尤重。这是 pragmatic 妥协(对象是只读 record),但契约边界名不副实,语义内部重构会波及消费方编译。收敛二选一(倾向 A):
- **方案 A**:把被消费的 record 上移 `api` 包(移动+消费方 import 批改,一次编译期重构);
- **方案 B**:文档改口,承认这些类型是公共契约的一部分,写进 DEPENDENCIES.md 白名单。

**模块归属:** **跨模块**——semantic + 全部 import 方(modeling/metric/asset/lifecycle/mdm)

**Blocked by:** 无;**阻塞 04**(边界测试须先有干净基线)

**Status:** backlog(待排期)

**硬性约束(不可打破):** 遵守 [dev-plan.md《硬性开发约束》](../dev-plan.md);方案 A 是纯移动不改行为,禁止夹带字段/语义变更;散列引用(消费方只存松散 ID/编码)的 S2 数据层契约不动。

- [ ] 裁决 A/B 并更新 `DEPENDENCIES.md`
- [ ] 方案 A:四个 record 上移 api 包,五个消费模块 import 同步修正,全仓编译通过
- [ ] 方案 B:api 公共类型清单写入契约文档,与 04 的 allowlist 对齐
- [ ] 完成后解除 04 的阻塞
