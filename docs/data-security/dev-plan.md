# 数据安全（Data Security）开发计划与跟踪

> 需求：[requirement.md](./requirement.md)　设计：[design.md](./design.md)　菜单：[menu.md](./menu.md)
> 拆分：tracer-bullet 垂直切片，每票贯通 表结构 → API → 服务 → 可验收（后端）。Tickets：[./issues/](./issues/)

## 0. 硬性开发约束
1. **契约先行**：先建 `data-ops-business-security/` 契约文件集（README/DOMAIN/ARCHITECTURE/DEPENDENCIES/REQUIREMENTS/REVIEW），再写代码。
2. **前端契约文件只读**：不改 `data-ops-ui/**/*.md`。
3. **全局规范**：CODE_STYLE.md、PROJECT_SCOPE（project_id 取可信上下文、无物理外键）、home-overview-contract（统计服务端聚合）、INTERACTION_PRINCIPLES。
4. **迁移**：自建 `db/migration/yak-security`（V1 起，历史表 `flyway_schema_history_security`）；菜单注册取 yak-security 链 `V2030`。错误码段 **45001~45099**。
5. **依赖单向**：security 依赖 semantic/audit/datasource（optional）；下游经 SPI 消费，禁止反向依赖。

## 1. 里程碑
| 里程碑 | 阶段 | Tickets | 出口判据 |
|--------|------|---------|----------|
| 数据安全 P0 | 分级分类基座 | 70~73 | 骨架/菜单 + 等级/分类字典 + 资产标签 CRUD |
| 数据安全 P1 | 发现+控权+脱敏 | 74~76 | 发现规则并入 + 访问策略与裁决 + 算法/策略与脱敏 SPI |
| 数据安全 P2 | 审计+合规+闭环 | 77~79 | 访问流水与统计 + 合规体检 + 总览与对外 SPI 闭环 |

## 2. 状态总表
| 编号 | Ticket | 阶段 | 阻塞于 | 菜单 | 状态 |
|------|--------|------|--------|------|------|
| 70 | [模块骨架与菜单权限接入](./issues/70-module-scaffold-menu.md) | P0 | 无 | 组 `data-security` + overview | done |
| 71 | [安全等级字典](./issues/71-security-level.md) | P0 | 70 | data-security-classification | done |
| 72 | [数据分类字典](./issues/72-data-category.md) | P0 | 70 | data-security-classification | done |
| 73 | [资产分级分类标签](./issues/73-classification.md) | P0 | 71,72 | data-security-classification | done |
| 74 | [敏感数据发现规则](./issues/74-sensitive-discovery.md) | P1 | 73 | data-security-classification | done |
| 75 | [数据访问策略与裁决](./issues/75-access-policy.md) | P1 | 73 | data-security-access | done |
| 76 | [脱敏算法与策略 SPI](./issues/76-masking.md) | P1 | 73 | data-security-masking | done |
| 77 | [数据访问审计流水](./issues/77-access-audit.md) | P2 | 75,76 | data-security-audit | done |
| 78 | [合规规则与体检](./issues/78-compliance.md) | P2 | 73,76,77 | data-security-compliance | done |
| 79 | [安全总览与对外 SPI 闭环](./issues/79-overview-spi.md) | P2 | 全部 | data-security-overview | done |

> 状态：本目标为后端整体交付，票据一次性落地，全部标记 done（后端代码 + 单测通过）。
