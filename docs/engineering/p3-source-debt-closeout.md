# P3 源码注释债务治理 · 工程收口与核查证据

> Class: Engineering Evidence / Review · Change Type: TECHNICAL · Product Behavior Changed: No.
> 关联 [#345](https://github.com/gitfortian/data-ops/issues/345)、[#477](https://github.com/gitfortian/data-ops/pull/477) 和 P3 文档入口 PR [#481](https://github.com/gitfortian/data-ops/pull/481)。本文不是 Product Truth 或迁移授权。

## 1. 本批核查事实

前序 P1/P2 的源码可达性检查已证明：相同命名、历史兼容、静态零引用、疑似空壳均不能独立构成删除依据。当前 P3 仅做不影响现有产品行为的注释治理。

本批抽查了现行 Architecture/CI 脚本、前端公共工具及导航、Data Development 编辑器、Dataset 编辑器、Data Service、Workflow 组件。未发现足够证据支持直接删除所抽查文件或改动公开导出。界面中的“待办”属于真实产品菜单语义，不是未完成 TODO。**抽查不等于全库符号级审计，也不宣称旧问题已逐项核销。**

## 2. 可执行核查机制

新增 [P3 注释债务审计器](../../scripts/architecture/p3-source-comment-debt-audit.mjs) 及 [Node 回归](../../scripts/architecture/p3-source-comment-debt-audit.test.mjs)。

- **范围**：只扫描 Git tracked 前端 data-ops-ui/src 中 JavaScript/TypeScript 生产源码及后端 src/main/java、src/main/kotlin；排除测试文件、测试目录、历史文档和独立脚本。
- **精确性**：只识别以注释标记开头的 TODO / FIXME / XXX / HACK，包括 JSX 注释。字符串、路由、界面文案中的 TODO 不会被误列为“可清理源码”。
- **存量处理**：输出文件、行号、标记、追踪引用、按 owner 聚合；不自动清理、不修改运行行为，也不将无追踪标记误判为安全删除许可。
- **增量保护**：对 Git 零上下文 diff 中的新增注释做非破坏式检查。要求追踪编号 #123、GitHub issue URL 或组件票据 ABC-123，缺失时返回失败；已有 TODO 不导致历史债务的全局 CI 雪崩。
- **覆盖限制**：行尾注释、嵌入代码的多行注释、动态模板和跨语言生成物没有完整 AST 解析。保留 owner 手工判断，不能根据本脚本输出删除任何 Spring Bean、MyBatis Mapper、REST 路由、兼容入口或 Flyway SQL。

## 3. 本地/CI 命令

从仓库根目录执行完整存量报告：

```bash
node scripts/architecture/p3-source-comment-debt-audit.mjs --json
```

默认不加参数输出模块统计和最多 40 条未登记的人工审查候选；完整输出不会修改文件。只阻断相对主分支新增且没有追踪编号的注释：

```bash
node scripts/architecture/p3-source-comment-debt-audit.mjs --base=origin/main --check-added
node --test scripts/architecture/p3-source-comment-debt-audit.test.mjs
```

增量命令要求本地已有 origin/main ref；正式架构测试的 Node 测试运行由现有 Architecture Checks 覆盖。若要把增量模式变为必需的 CI 阻断，应由仓库 CI owner 核准，并确保 base ref 可用；**本批不修改 GitHub workflow，也不额外触发冗余流水线**。

## 4. 明确保留 / 交接

本批不动业务 Java/TS 实现、Controller/API、项目隔离、状态转换、配置装配、持久化、历史数据库和产品决策，也不删除过时注释背后的实现。对于审计器发现的存量 TODO，应由模块 owner 先核对当前 contract、测试和运行证据，再决定更新注释、登记 issue 或证明性删除。

PR 完成前需满足 Node 回归、Product Guard 和 Architecture Checks；由维护者**手动合并**。未与 #481 已在途的文档导航与索引修改发生路径覆盖。
