# Asset Review Guide

每张 ticket 评审时按以下清单核对：

1. **契约先行**：契约 diff 先于代码 diff；REQUIREMENTS.md 覆盖本票全部用户可见行为。
2. **管目录不管内容（D1）**：任何读取路径不得把台账快照列当事实源返回给详情页；快照列必须可对账刷新且有来源标记。
3. **依赖边界**：零源域内部包 import（仅 `api/` SPI 与 provider bean）；不写 `yak_metadata_asset` 与任何源域表（D6）；PO/错误码/权限码在 yak-ops-common。
4. **asset_key 同源**：新 provider 必须先过"与 lineage 登记键逐字符一致"契约测试；MANUAL 键 `manual:{code}`。
5. **状态机纪律**：流转集中一处判定（非法 48003）；publish 必经有效 precheck token（48005）；offline 必填原因；IGNORE 边界（48016）。
6. **对账容错**：单 provider 失败不中断全局；IGNORED 不复活；SOURCE_GONE 只按 reconciled_at 窗口判定；MANUAL 不参与。
7. **健康度只读**：无接口可写 score；N/A 项剔分母；失败依赖 0 分+标注，不伪造空。
8. **项目空间与预算**：读写绑定注入的 `CurrentProject`；概览 ≤8 查询、列表排序零跨域调用（D11）。
9. **迁移纪律**：Flyway `yak-asset` 只增不改；菜单变更走 yak-security 新文件。
10. **审计**：登记/上下架/忽略/确认/规则启停 fail-open 落审计 `ASSET_*`，风险上架的缺口清单写入 detail。
11. **纯函数测试**：HealthScorer/规则匹配/hash 判定为 static 纯函数且各有表驱动单测；新增规则先加用例。
12. **前端契约**：menuCode 稳定且过 `navigationMenuContract.test.ts`（V2032）；不改 yak-ops-ui 下任何 .md；表单遵守"能选择就不填、能默认就不留空"。
