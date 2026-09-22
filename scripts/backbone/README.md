# Legacy Backbone Checks

本目录包含旧“主心骨”阶段留下的守门脚本。

它们现在的定位是：

> Historical Evidence Diagnostics

而不是 Product Truth / Product Guard。

当前权威入口：

- `PRODUCT_STYLE.md`
- `docs/product/README.md`
- `docs/product/DOCUMENT_GOVERNANCE.md`
- `scripts/product/**`

规则：

- 不把 `docs/v1/**` 当成当前产品规范；
- 不因为 legacy checker 报告漂移就修改当前 Product Truth；
- 有价值的旧结论应先进入 Evidence -> Decision -> Current Contract 的 promotion 流程。

`check-silent-catch.mjs` 属于工程质量检查，不依赖 `docs/v1` 产品权威，可继续独立使用。
