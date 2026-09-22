# 语义体系设计（基于真实数据源灌数）

生成时间：2026-09-18 ｜ 作用范围：项目空间 **默认空间（project_id=1）**
灌数脚本：`docs/semantic/harness_semantics.py`（可重跑、可回滚）
回滚快照：`docs/semantic/harness-rollback.json`

## 1. 数据源盘点

默认空间下 12 个数据源，其中 9 个是业务库（共 16 张表，全部带脏数据注释），3 个是数仓层。

| 数据源 | 库 | 表 | 业务含义 |
| --- | --- | --- | --- |
| 订单库 | trade_db | trade_order / trade_order_detail | 订单主表 + 明细 |
| 客户库 | crm_db | crm_customer / crm_customer_address / crm_customer_history | 客户主档、收货地址、SCD2 拉链 |
| 商品库 | item_db | item_item | 商品主数据 |
| 支付库 | pay_db | pay_payment / pay_refund | 支付流水、退款 |
| 营销库 | mkt_db | mkt_activity / mkt_coupon | 活动、优惠券 |
| 物流库存 | wms_db | wms_delivery / wms_inventory / wms_stock_log | 发货、库存快照、出入库流水 |
| 售后库 | aftersale_db | aftersale_ticket | 售后工单 |
| 结算库 | settle_db | set_settlement | 商家结算 |
| 评价库 | review_db | review_product | 商品评价 |
| 数仓 | ods / dwd / dim / **dws / ads** | — | 建模落库层 |

灌数前语义中心只有 6 个业务域、2 个业务过程、12 个标准字段、**0 条字段绑定**，且 DWS/ADS 分层未配置数据源。

## 2. 灌数结果

| 对象 | 前 | 后 | 本次新增 |
| --- | --- | --- | --- |
| 业务域 | 6 | 27（12 根 + 15 子） | 21 |
| 业务过程 | 2 | 23（19 FACT + 4 DIMENSION） | 21 |
| 标准字段 | 12 | 148（77 维度 / 47 度量 / 24 过程属性） | 136 |
| 过程↔字段绑定 | 0 | 197 | 197 |
| 过程↔源表绑定 | 3 | 39（23 主表 / 2 明细 / 14 维表） | 36 |
| 数仓分层 | DWS/ADS 无数据源 | 5 层全部绑定 | 新建 dws/ads 数据源 |

标准引用覆盖率：148/148 字段有 `std_type_id`；47 个度量中 29 个挂了 `std_caliber_id`；23 个维度挂了码表；8 个敏感字段挂了安全分级（L2/L3）。

## 3. 业务域树

```
交易域 trade ── 订单 order / 支付 pay / 退款 refund      （已有，沿用）
用户域 user / 公共域 common                                （已有，沿用）
商品域 product ── 商品 item / 商品类目 category
客户域 customer ── 客户档案 customer_profile / 客户历史 customer_scd / 收货地址 customer_addr
会员域 member
营销域 marketing ── 营销活动 activity / 优惠券 coupon
履约域 fulfil ── 发货配送 delivery / 库存 inventory
售后域 aftersale ── 售后工单 ticket
结算域 settlement ── 结算单 settle
评价域 review ── 商品评价 product_review
风控合规域 risk
```

## 4. 业务过程（23 个）

事实过程 19 个，维度过程 4 个。每个过程都绑定了 1 张 MAIN 主表，跨库关联用 DIM 角色 + 关联条件表达。

| 过程 | 域 | 粒度 | 主表 | 关联 |
| --- | --- | --- | --- | --- |
| 下单 order_create | 订单 | 订单 | trade_order | 明细 trade_order_detail |
| 购买商品 purchase_item | 订单 | 订单明细行 | trade_order（已存在，沿用） | item_item |
| 订单支付 order_pay | 支付 | 支付单 | pay_payment | trade_order |
| 订单取消 order_cancel | 订单 | 订单 | trade_order | — |
| 订单完成 order_finish | 订单 | 订单 | trade_order | — |
| 退款 refund_apply | 退款 | 退款单 | pay_refund | pay_payment |
| 发货 delivery_send | 发货配送 | 发货单 | wms_delivery | trade_order |
| 签收 delivery_sign | 发货配送 | 发货单 | wms_delivery | — |
| 库存变动 stock_change | 库存 | 库存流水 | wms_stock_log | item_item |
| 库存快照 stock_snapshot | 库存 | 商品+仓+月 | wms_inventory | item_item |
| 领券 coupon_receive | 优惠券 | 券实例 | mkt_coupon | mkt_activity |
| 用券 coupon_use | 优惠券 | 券实例 | mkt_coupon | trade_order |
| 活动投放 activity_launch | 营销活动 | 活动 | mkt_activity | — |
| 售后申请 aftersale_apply | 售后工单 | 工单 | aftersale_ticket | pay_refund |
| 售后完成 aftersale_finish | 售后工单 | 工单 | aftersale_ticket | — |
| 结算出账 settle_out | 结算单 | 结算单 | set_settlement | trade_order |
| 发表评价 review_publish | 商品评价 | 评价 | review_product | trade_order、item_item |
| 客户注册 customer_register | 客户档案 | 客户 | crm_customer | — |
| 客户信息变更 customer_info_change | 客户历史 | 客户版本 | crm_customer_history | crm_customer |
| 客户维度 dim_customer | 客户档案 | 客户 | crm_customer | — |
| 收货地址维度 dim_customer_address | 收货地址 | 客户+地址 | crm_customer_address | crm_customer |
| 商品维度 dim_item | 商品 | 商品 | item_item | — |
| 活动维度 dim_activity | 营销活动 | 活动 | mkt_activity | — |

## 5. 可支撑的业务场景

灌数后语义中心能直接支撑以下分析主线（每条都能从「域 → 过程 → 标准字段 → 源表」闭环走通）：

1. **交易大盘**：GMV / 订单量 / 客单价 / 支付率 / 取消率 / 退款率 / 净 GMV，按 业务日期 × 渠道 × 城市 下钻。
2. **商品经营**：商品销量、销售额、成交均价、库存周转率、退货率、平均评分，按 类目 × 品牌 下钻。
3. **客户增长与生命周期**：新增用户数、活跃用户数、ARPU、人均订单数、次日留存率、流失率，按 注册来源 × 会员等级 下钻。
4. **SCD2 时点回溯**：`customer_info_change` 绑定拉链表的 `effective_time / expire_time / is_current_version`，可还原"下单当时"的城市与会员等级，与 `trade_order.order_city` 互为校验。
5. **营销归因**：领券 → 用券 → 活动 GMV → ROI → 优惠券使用率，券与订单通过 `coupon_use` 回填的 `order_id` 打通。
6. **履约时效**：发货 → 签收 → 配送时长；签收时间早于发货、库存 `before+change ≠ after` 等脏数据可直接作为质量规则来源。
7. **售后与退款**：售后申请 → 完成时长、退款率、商品退货率。
8. **平台结算**：商家实收、平台佣金、佣金率、按结算周期与币种汇总。
9. **数据安全分级**：手机号 / 身份证 / 姓名 / 地址 / 邮箱 / 生日 8 个字段挂 L2/L3 标准，可直接驱动脱敏策略。

## 6. 既有数据的处理

5 个早期手工字段（`order_time`、`order_status`、`order_amount`、`quantity`、`cust_name`）缺标准引用或单位不一致（`order_amount` 原挂「分」而全库金额统一「元」）。脚本按本次口径重写，**改前值已快照**到 `harness-rollback.json`。

## 7. 回滚

```bash
python docs/semantic/harness_semantics.py --rollback
```

只删除 `created_by='harness-2026-09-18'` 的行，并按快照还原被改动的既有字段与 DWS/ADS 分层配置、删除本次新建的 dws/ads 数据源。

## 8. 已知不一致（留给标准治理，不在本次强改）

- `purchase_item` 的主表仍是 `trade_order`（早期绑定），语义上应为 `trade_order_detail`；因 `uk_yak_semantic_process_source` 唯一键不含 `table_role`，同表无法同时挂主表与明细表，需在 UI 上先解绑再改绑。
- 18 个度量未挂口径（多为维表属性型金额，如商品标价、成本），属正常。
- `settle_cycle`、`order_channel` 缺对应码表，需先补数据标准再回填。

## 9. 脚本缺陷修订（2026-09-19）

本文 §3 的「5 层全部绑定」当时只成立到**分层配置**层面——本脚本新建的两个数据源行本身是坏的：

- `conn_status` 写成 `'SUCCESS'`，而 `DataSourceConnStatus` 只有 `UNKNOWN/CONNECTED/DISCONNECTED`。`parse()` 走 `valueOf` 抛异常，且因为它出现在 PO→VO 逐行转换里，**`POST /api/v1/data-source/page` 整表返回 999**（连带把语义中心的数据源名打成裸 ID，见 ux-review A2）。
- `jdbc_url` 停留在源行的 `/dwd`：`replace("/dwd?", …)` 对无查询串的 URL 是空操作。`connection_params.jdbcUrl` 是对的，而多数执行路径优先读 params，所以连接本身没暴露问题，但离不读 params 的路径只差一步。

已修：数据行改为 `CONNECTED` / `DEVELOP` / 指向 `dws`、`ads`；脚本改成以 `connection_params` JSON 为唯一来源重建连接配置（`harness_semantics.py` seed 第 1 步）。实测 `data-source/page` 返回 14 条、`/semantic/processes/2/edit` 的关联源表正确显示「订单库 / 商品库」。
