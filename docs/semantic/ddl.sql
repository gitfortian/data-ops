-- ============================================================================
-- 数据治理语义层测试数据集 —— DDL（多库版）
-- 分 9 个库，模拟生产"分库分域"架构
-- 时间：2024-01-01 ~ 2026-12-31（36 个月）
-- 治理点：单表脏数据 + 跨表一致性 + 记录级质量 + 跨表标准不一致 + SCD
-- ============================================================================

-- ============================================================
-- 库 1：crm_db（客户域）
-- ============================================================
DROP DATABASE IF EXISTS crm_db;
CREATE DATABASE crm_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE crm_db;

DROP TABLE IF EXISTS crm_customer;
CREATE TABLE crm_customer (
                              cust_id         VARCHAR(32)   COMMENT '客户ID',
                              cust_name       VARCHAR(64)   COMMENT '客户姓名',
                              cust_mobile     VARCHAR(32)   COMMENT '手机号（明文，待脱敏）',
                              cust_idcard     VARCHAR(32)   COMMENT '身份证（明文，待脱敏）',
                              gender          VARCHAR(8)    COMMENT '性别：M/F/1/2/男/女（不统一）',
                              birthday        VARCHAR(32)   COMMENT '生日：多格式（不统一）',
                              reg_time        VARCHAR(32)   COMMENT '注册时间（不统一）',
                              reg_source      VARCHAR(16)   COMMENT '注册来源：APP/1/wechat/小程序（不统一）',
                              member_level    VARCHAR(16)   COMMENT '会员等级：1/2/3/VIP/GOLD（不统一）',
                              city            VARCHAR(64)   COMMENT '当前城市（SCD 当前值）',
                              province        VARCHAR(64)   COMMENT '省份',
                              status          VARCHAR(16)   COMMENT '状态：0/1/active/正常（不统一）',
                              etl_time        DATETIME      COMMENT '抽取时间',
                              PRIMARY KEY (cust_id),
                              KEY idx_cust_mobile (cust_mobile)
) COMMENT='客户表（脏数据，逻辑外键无物理约束）';

DROP TABLE IF EXISTS crm_customer_address;
CREATE TABLE crm_customer_address (
                                      addr_id         VARCHAR(32)   COMMENT '地址ID',
                                      cust_id         VARCHAR(32)   COMMENT '客户ID',
                                      receiver_name   VARCHAR(64)   COMMENT '收货人',
                                      receiver_mobile VARCHAR(32)   COMMENT '收货人手机（明文）',
                                      address         VARCHAR(256)  COMMENT '详细地址',
                                      is_default      VARCHAR(8)    COMMENT '是否默认：0/1/Y/N（不统一）',
                                      PRIMARY KEY (addr_id),
                                      KEY idx_addr_cust (cust_id)
) COMMENT='客户地址表';

DROP TABLE IF EXISTS crm_customer_history;
CREATE TABLE crm_customer_history (
                                      history_id      VARCHAR(32)   COMMENT '历史记录ID',
                                      cust_id         VARCHAR(32)   COMMENT '客户ID',
                                      version         INT           COMMENT '版本号',
                                      cust_name       VARCHAR(64)   COMMENT '客户姓名（当时）',
                                      city            VARCHAR(64)   COMMENT '城市（当时）',
                                      province        VARCHAR(64)   COMMENT '省份（当时）',
                                      member_level    VARCHAR(16)   COMMENT '会员等级（当时）',
                                      status          VARCHAR(16)   COMMENT '状态（当时）',
                                      start_time      DATETIME      COMMENT '生效时间',
                                      end_time        DATETIME      COMMENT '失效时间（NULL 表示当前有效）',
                                      is_current      TINYINT(1)    COMMENT '是否当前版本：1=是',
                                      change_reason   VARCHAR(128)  COMMENT '变更原因',
                                      etl_time        DATETIME      COMMENT '抽取时间',
                                      PRIMARY KEY (history_id),
                                      KEY idx_history_cust (cust_id),
                                      KEY idx_history_cust_time (cust_id, start_time, end_time),
                                      KEY idx_history_current (cust_id, is_current)
) COMMENT='客户历史维表（SCD Type 2）';

-- ============================================================
-- 库 2：item_db（商品域）
-- ============================================================
DROP DATABASE IF EXISTS item_db;
CREATE DATABASE item_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE item_db;

DROP TABLE IF EXISTS item_item;
CREATE TABLE item_item (
                           item_id         VARCHAR(32)   COMMENT '商品ID（格式 I000001）',
                           item_name       VARCHAR(128)  COMMENT '商品名称',
                           category_id     VARCHAR(32)   COMMENT '类目ID',
                           category_name   VARCHAR(64)   COMMENT '类目名称',
                           brand           VARCHAR(64)   COMMENT '品牌',
                           price           VARCHAR(32)   COMMENT '价格（类型不统一）',
                           cost            VARCHAR(32)   COMMENT '成本',
                           status          VARCHAR(16)   COMMENT '状态：0/1/on/off（不统一）',
                           etl_time        DATETIME      COMMENT '抽取时间',
                           PRIMARY KEY (item_id)
) COMMENT='商品表';

-- ============================================================
-- 库 3：trade_db（订单域）
-- ============================================================
DROP DATABASE IF EXISTS trade_db;
CREATE DATABASE trade_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE trade_db;

DROP TABLE IF EXISTS trade_order;
CREATE TABLE trade_order (
                             order_id        VARCHAR(32)   COMMENT '订单ID',
                             order_no        VARCHAR(64)   COMMENT '订单号',
                             cust_id         VARCHAR(32)   COMMENT '客户ID（逻辑外键）',
                             order_city      VARCHAR(64)   COMMENT '下单时客户城市（来自 SCD 历史维表）',
                             order_time      VARCHAR(32)   COMMENT '下单时间（不统一）',
                             order_status    VARCHAR(16)   COMMENT '订单状态（不统一，2024 数字、2025 后混用）',
                             order_amount    VARCHAR(32)   COMMENT '订单金额（5% 与明细汇总不一致）',
                             pay_amount      VARCHAR(32)   COMMENT '支付金额',
                             discount_amount VARCHAR(32)   COMMENT '优惠金额（5% 用了券但此处为 0）',
                             freight_amount  VARCHAR(32)   COMMENT '运费',
                             pay_time        VARCHAR(32)   COMMENT '支付时间',
                             ship_time       VARCHAR(32)   COMMENT '发货时间',
                             finish_time     VARCHAR(32)   COMMENT '完成时间',
                             cancel_time     VARCHAR(32)   COMMENT '取消时间',
                             cancel_reason   VARCHAR(128)  COMMENT '取消原因',
                             channel         VARCHAR(16)   COMMENT '渠道（不统一）',
                             etl_time        DATETIME      COMMENT '抽取时间',
                             PRIMARY KEY (order_id),
                             KEY idx_order_cust (cust_id),
                             KEY idx_order_time (order_time),
                             KEY idx_order_status (order_status)
) COMMENT='订单主表（脏数据）';

DROP TABLE IF EXISTS trade_order_detail;
CREATE TABLE trade_order_detail (
                                    detail_id       VARCHAR(32)   COMMENT '明细ID',
                                    order_id        VARCHAR(32)   COMMENT '订单ID（1% 孤儿记录）',
                                    item_id         VARCHAR(32)   COMMENT '商品ID（1% ITEM- 前缀）',
                                    item_name       VARCHAR(128)  COMMENT '商品名称',
                                    quantity        INT           COMMENT '数量（0.5% 极值 9999）',
                                    price           VARCHAR(32)   COMMENT '单价',
                                    amount          VARCHAR(32)   COMMENT '金额（0.5% 负数）',
                                    discount        VARCHAR(32)   COMMENT '折扣',
                                    etl_time        DATETIME      COMMENT '抽取时间',
                                    PRIMARY KEY (detail_id),
                                    KEY idx_detail_order (order_id)
) COMMENT='订单明细表';

-- ============================================================
-- 库 4：pay_db（支付域）
-- ============================================================
DROP DATABASE IF EXISTS pay_db;
CREATE DATABASE pay_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE pay_db;

DROP TABLE IF EXISTS pay_payment;
CREATE TABLE pay_payment (
                             pay_id          VARCHAR(32)   COMMENT '支付ID',
                             order_id        VARCHAR(32)   COMMENT '订单ID',
                             cust_id         VARCHAR(32)   COMMENT '客户ID',
                             pay_amount      DECIMAL(12,2) COMMENT '支付金额（老系统 DECIMAL；3% 与订单应付不一致）',
                             pay_channel     VARCHAR(16)   COMMENT '支付渠道（不统一）',
                             pay_status      VARCHAR(16)   COMMENT '支付状态（不统一）',
                             pay_time        VARCHAR(32)   COMMENT '支付时间',
                             trade_no        VARCHAR(64)   COMMENT '第三方流水号',
                             etl_time        DATETIME      COMMENT '抽取时间',
                             PRIMARY KEY (pay_id),
                             KEY idx_pay_order (order_id),
                             KEY idx_pay_time (pay_time)
) COMMENT='支付表';

DROP TABLE IF EXISTS pay_refund;
CREATE TABLE pay_refund (
                            refund_id       VARCHAR(32)   COMMENT '退款ID',
                            order_id        VARCHAR(32)   COMMENT '订单ID',
                            pay_id          VARCHAR(32)   COMMENT '支付ID',
                            refund_amount   VARCHAR(32)   COMMENT '退款金额（2% > 支付金额）',
                            refund_status   VARCHAR(16)   COMMENT '退款状态（不统一）',
                            refund_time     VARCHAR(32)   COMMENT '退款时间',
                            refund_reason   VARCHAR(128)  COMMENT '退款原因',
                            etl_time        DATETIME      COMMENT '抽取时间',
                            PRIMARY KEY (refund_id),
                            KEY idx_refund_order (order_id)
) COMMENT='退款表';

-- ============================================================
-- 库 5：mkt_db（营销域）
-- ============================================================
DROP DATABASE IF EXISTS mkt_db;
CREATE DATABASE mkt_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mkt_db;

DROP TABLE IF EXISTS mkt_activity;
CREATE TABLE mkt_activity (
                              activity_id     VARCHAR(32)   COMMENT '活动ID',
                              activity_name   VARCHAR(128)  COMMENT '活动名称',
                              activity_type   VARCHAR(16)   COMMENT '活动类型（不统一）',
                              start_time      VARCHAR(32)   COMMENT '开始时间',
                              end_time        VARCHAR(32)   COMMENT '结束时间',
                              status          VARCHAR(16)   COMMENT '状态（不统一）',
                              budget          VARCHAR(32)   COMMENT '预算',
                              etl_time        DATETIME      COMMENT '抽取时间',
                              PRIMARY KEY (activity_id)
) COMMENT='活动表';

DROP TABLE IF EXISTS mkt_coupon;
CREATE TABLE mkt_coupon (
                            coupon_id       VARCHAR(32)   COMMENT '优惠券ID',
                            coupon_code     VARCHAR(64)   COMMENT '券码',
                            activity_id     VARCHAR(32)   COMMENT '活动ID',
                            cust_id         VARCHAR(32)   COMMENT '客户ID',
                            coupon_type     VARCHAR(16)   COMMENT '类型（不统一）',
                            coupon_amount   VARCHAR(32)   COMMENT '券金额',
                            status          VARCHAR(16)   COMMENT '状态（不统一）',
                            receive_time    VARCHAR(32)   COMMENT '领取时间',
                            use_time        VARCHAR(32)   COMMENT '使用时间',
                            order_id        VARCHAR(32)   COMMENT '使用的订单ID（已使用必回填）',
                            etl_time        DATETIME      COMMENT '抽取时间',
                            PRIMARY KEY (coupon_id),
                            KEY idx_coupon_cust (cust_id),
                            KEY idx_coupon_activity (activity_id)
) COMMENT='优惠券表';

-- ============================================================
-- 库 6：wms_db（物流 + 库存域）
-- ============================================================
DROP DATABASE IF EXISTS wms_db;
CREATE DATABASE wms_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE wms_db;

DROP TABLE IF EXISTS wms_delivery;
CREATE TABLE wms_delivery (
                              delivery_id     VARCHAR(32)   COMMENT '发货ID',
                              order_id        VARCHAR(32)   COMMENT '订单ID',
                              logistics_no    VARCHAR(64)   COMMENT '物流单号',
                              logistics_company VARCHAR(32) COMMENT '物流公司（不统一）',
                              delivery_status VARCHAR(16)   COMMENT '配送状态（不统一）',
                              delivery_time   VARCHAR(32)   COMMENT '发货时间',
                              sign_time       VARCHAR(32)   COMMENT '签收时间（1% 早于发货时间）',
                              etl_time        DATETIME      COMMENT '抽取时间',
                              PRIMARY KEY (delivery_id),
                              KEY idx_delivery_order (order_id),
                              KEY idx_delivery_status (delivery_status)
) COMMENT='发货表';

DROP TABLE IF EXISTS wms_inventory;
CREATE TABLE wms_inventory (
                               inventory_id    VARCHAR(32)   COMMENT '库存ID',
                               item_id         VARCHAR(32)   COMMENT '商品ID',
                               warehouse_id    VARCHAR(32)   COMMENT '仓库ID',
                               stock_qty       INT           COMMENT '库存数量（可能为负）',
                               lock_qty        INT           COMMENT '锁定数量',
                               available_qty   INT           COMMENT '可用数量（5% ≠ stock-lock）',
                               snapshot_time   VARCHAR(32)   COMMENT '快照时间（按月快照）',
                               etl_time        DATETIME      COMMENT '抽取时间',
                               PRIMARY KEY (inventory_id),
                               KEY idx_inv_item (item_id)
) COMMENT='库存快照表（按月）';

DROP TABLE IF EXISTS wms_stock_log;
CREATE TABLE wms_stock_log (
                               log_id          VARCHAR(32)   COMMENT '流水ID',
                               item_id         VARCHAR(32)   COMMENT '商品ID',
                               order_id        VARCHAR(32)   COMMENT '关联订单ID',
                               change_type     VARCHAR(16)   COMMENT '变动类型：IN/OUT/LOCK/UNLOCK',
                               change_qty      INT           COMMENT '变动数量',
                               before_qty      INT           COMMENT '变动前库存',
                               after_qty       INT           COMMENT '变动后库存（1% before+change ≠ after）',
                               log_time        VARCHAR(32)   COMMENT '变动时间',
                               etl_time        DATETIME      COMMENT '抽取时间',
                               PRIMARY KEY (log_id),
                               KEY idx_stock_order (order_id)
) COMMENT='库存流水表';

-- ============================================================
-- 库 7：aftersale_db（售后域）
-- ============================================================
DROP DATABASE IF EXISTS aftersale_db;
CREATE DATABASE aftersale_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE aftersale_db;

DROP TABLE IF EXISTS aftersale_ticket;
CREATE TABLE aftersale_ticket (
                                  ticket_id       VARCHAR(32)   COMMENT '工单ID',
                                  order_id        VARCHAR(32)   COMMENT '订单ID',
                                  cust_id         VARCHAR(32)   COMMENT '客户ID',
                                  ticket_type     VARCHAR(16)   COMMENT '工单类型（不统一）',
                                  ticket_status   VARCHAR(16)   COMMENT '工单状态（不统一）',
                                  refund_id       VARCHAR(32)   COMMENT '关联退款ID',
                                  apply_time      VARCHAR(32)   COMMENT '申请时间',
                                  finish_time     VARCHAR(32)   COMMENT '完成时间（10% 早于申请时间）',
                                  reason          VARCHAR(128)  COMMENT '售后原因',
                                  etl_time        DATETIME      COMMENT '抽取时间',
                                  PRIMARY KEY (ticket_id),
                                  KEY idx_aftersale_order (order_id)
) COMMENT='售后工单表';

-- ============================================================
-- 库 8：settle_db（结算域）
-- ============================================================
DROP DATABASE IF EXISTS settle_db;
CREATE DATABASE settle_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE settle_db;

DROP TABLE IF EXISTS set_settlement;
CREATE TABLE set_settlement (
                                settle_id       VARCHAR(32)   COMMENT '结算ID',
                                order_id        VARCHAR(32)   COMMENT '订单ID',
                                merchant_id     VARCHAR(32)   COMMENT '商家ID',
                                settle_amount   DECIMAL(12,2) COMMENT '商家实收（= pay - commission，3% 偏差）',
                                commission      DECIMAL(12,2) COMMENT '平台佣金',
                                settle_status   VARCHAR(16)   COMMENT '结算状态（不统一）',
                                settle_cycle    VARCHAR(16)   COMMENT '结算周期（不统一）',
                                currency        VARCHAR(8)    COMMENT '币种：CNY/USD',
                                settle_time     VARCHAR(32)   COMMENT '结算时间',
                                etl_time        DATETIME      COMMENT '抽取时间',
                                PRIMARY KEY (settle_id),
                                KEY idx_settle_order (order_id)
) COMMENT='结算单表';

-- ============================================================
-- 库 9：review_db（评价域）
-- ============================================================
DROP DATABASE IF EXISTS review_db;
CREATE DATABASE review_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE review_db;

DROP TABLE IF EXISTS review_product;
CREATE TABLE review_product (
                                review_id       VARCHAR(32)   COMMENT '评价ID',
                                order_id        VARCHAR(32)   COMMENT '订单ID（5% 与订单脱钩）',
                                item_id         VARCHAR(32)   COMMENT '商品ID',
                                cust_id         VARCHAR(32)   COMMENT '客户ID',
                                score           VARCHAR(8)    COMMENT '评分（不统一）',
                                content         VARCHAR(512)  COMMENT '评价内容',
                                is_anonymous    VARCHAR(8)    COMMENT '是否匿名（不统一）',
                                review_time     VARCHAR(32)   COMMENT '评价时间（1% 早于签收）',
                                etl_time        DATETIME      COMMENT '抽取时间',
                                PRIMARY KEY (review_id),
                                KEY idx_review_order (order_id),
                                KEY idx_review_item (item_id)
) COMMENT='商品评价表';