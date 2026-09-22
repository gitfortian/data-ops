# -*- coding: utf-8 -*-
"""语义体系灌数脚本（可重跑 / 可回滚）.

设计说明见 semantic-harness-2026-09-18.md。所有新增行都带 created_by=TAG，
`--rollback` 只删除本脚本写入的行并还原被改动的既有行快照。

    python docs/semantic/harness_semantics.py            # 灌入
    python docs/semantic/harness_semantics.py --rollback # 撤销
"""
import json
import os
import sys

import pymysql

PROJECT_ID = 1
TAG = "harness-2026-09-18"
SNAPSHOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "harness-rollback.json")

DSN = dict(
    host=os.environ.get("YAK_DATASOURCE_HOST", "127.0.0.1"),
    port=int(os.environ.get("YAK_DATASOURCE_PORT", "3306")),
    user=os.environ.get("YAK_DATASOURCE_USERNAME", "root"),
    password=os.environ.get("YAK_DATASOURCE_PASSWORD", "123456"),
    database=os.environ.get("YAK_DATASOURCE_DB", "yak_security"),
    charset="utf8mb4",
)

# 数据源 id -> 库名（ yak_ops_data_source / jdbc_url 实测 ）
DS = {
    "trade": 2,   # trade_db
    "crm": 3,     # crm_db
    "item": 4,    # item_db
    "pay": 5,     # pay_db
    "mkt": 6,     # mkt_db
    "wms": 7,     # wms_db
    "aftersale": 8,  # aftersale_db
    "settle": 9,  # settle_db
    "review": 10,  # review_db
}

# ---------------------------------------------------------------- 业务域
# (code, name, parent_code, 描述)
DOMAINS = [
    ("product",  "商品域",   None,  "商品主数据、类目与品牌，来源 item_db"),
    ("item",     "商品",     "product", "在售 SKU 粒度主数据"),
    ("category", "商品类目", "product", "类目层级，来源 item_item.category_*"),

    ("customer", "客户域",   None,  "客户主档与画像，来源 crm_db"),
    ("customer_profile", "客户档案", "customer", "客户当前态属性"),
    ("customer_scd",     "客户历史", "customer", "SCD Type2 拉链表，还原变更时点画像"),
    ("customer_addr",    "收货地址", "customer", "客户收货地址多值"),

    ("member",   "会员域",   None,  "会员等级、注册来源与生命周期"),

    ("marketing", "营销域",  None,  "活动与优惠券，来源 mkt_db"),
    ("activity",  "营销活动", "marketing", "活动主档与预算"),
    ("coupon",    "优惠券",   "marketing", "券的领取 / 核销流转"),

    ("fulfil",    "履约域",   None,  "从发货到签收的物流履约，来源 wms_db"),
    ("delivery",  "发货配送", "fulfil", "物流单与签收"),
    ("inventory", "库存",     "fulfil", "库存快照与出入库流水"),

    ("aftersale", "售后域",   None,  "售后工单，来源 aftersale_db"),
    ("ticket",    "售后工单", "aftersale", "工单申请与处理"),

    ("settlement", "结算域",  None,  "平台与商家结算，来源 settle_db"),
    ("settle",     "结算单",  "settlement", "结算单与佣金"),

    ("review",    "评价域",   None,  "商品评价，来源 review_db"),
    ("product_review", "商品评价", "review", "评分与评价内容"),

    ("risk",      "风控合规域", None, "敏感字段与数据分级落点"),
]

# ---------------------------------------------------------------- 标准字段
# code, name, role, data_type, std_type, std_unit, std_caliber, code_set, std_security, 说明
# role: PROCESS 过程属性 / DIMENSION 维度 / METRIC 度量
T_ID, T_CODE, T_NAME, T_DESC, T_STATUS, T_TYPE, T_LEVEL = 197, 198, 205, 208, 210, 211, 212
T_FLAG, T_DATETIME, T_DATE, T_MONTH = 220, 243, 240, 241
T_MOBILE, T_EMAIL, T_IDCARD, T_ADDRESS, T_CURRENCY = 250, 251, 252, 254, 256
T_AMOUNT = 221
FIELDS = [
    # --- 公共 / 过程属性
    ("etl_time",        "数据抽取时间",     "PROCESS",    "datetime",       T_DATETIME, None, None, None, None, "ODS 抽取批次时间"),
    ("biz_date",        "业务日期",         "DIMENSION",  "string",         T_DATE,     None, None, None, None, "事件发生自然日，分区常用键"),
    ("biz_month",       "业务月份",         "DIMENSION",  "string",         T_MONTH,    None, None, None, None, "月度汇总分区键"),
    ("batch_no",        "批次号",           "PROCESS",    "string",         201,        None, None, None, None, "一次抽取/回流的批次标识"),
    ("version_no",      "版本号",           "PROCESS",    "string",         202,        None, None, None, None, "维表快照版本号"),

    # --- 交易 / 订单
    ("order_no",        "订单编号",         "PROCESS",    "string",         200,        None, None, None, None, "业务侧订单号，全局唯一"),
    ("order_pay_amount","订单应付金额",     "METRIC",     "decimal(18,2)",  T_AMOUNT,   257,  4,    None, None, "订单主表应付口径"),
    ("order_discount_amount", "订单优惠金额", "METRIC",   "decimal(18,2)",  227,        257,  None, None, None, "整单优惠，券核销应回填"),
    ("order_freight_amount",  "订单运费",   "METRIC",     "decimal(18,2)",  229,        257,  None, None, None, "运费金额"),
    ("order_city",      "下单城市",         "DIMENSION",  "string",         T_NAME,     None, None, None, None, "下单时点城市，取 SCD 历史值"),
    ("order_channel",   "下单渠道",         "DIMENSION",  "string",         T_TYPE,     None, None, None, None, "来源渠道，需按码表归一"),
    ("pay_time",        "支付时间",         "DIMENSION",  "datetime",       T_DATETIME, None, None, None, None, "订单支付完成时间"),
    ("ship_time",       "出库发货时间",     "DIMENSION",  "datetime",       T_DATETIME, None, None, None, None, "订单发货时间"),
    ("finish_time",     "订单完成时间",     "DIMENSION",  "datetime",       T_DATETIME, None, None, None, None, "订单交易成功时间"),
    ("cancel_time",     "订单取消时间",     "DIMENSION",  "datetime",       T_DATETIME, None, None, None, None, "订单取消时间"),
    ("cancel_reason",   "订单取消原因",     "DIMENSION",  "string",         T_DESC,     None, None, None, None, "取消原因文本"),
    ("is_cancelled",    "是否取消订单",     "DIMENSION",  "tinyint",        T_FLAG,     None, None, "yes_no", None, "派生标志位"),
    ("is_paid",         "是否支付订单",     "DIMENSION",  "tinyint",        T_FLAG,     None, None, "yes_no", None, "派生标志位"),

    # --- 订单明细
    ("detail_id",       "明细行ID",         "PROCESS",    "string",         T_ID,       None, None, None, None, "订单明细主键"),
    ("unit_price",      "成交单价",         "METRIC",     "decimal(18,2)",  223,        257,  None, None, None, "明细成交单价，来源 trade_order_detail.price"),
    ("line_amount",     "明细行金额",       "METRIC",     "decimal(18,2)",  T_AMOUNT,   257,  36,   None, None, "数量*单价，整单 GMV 的可加口径"),
    ("line_discount",   "明细行折扣",       "METRIC",     "decimal(18,2)",  227,        257,  None, None, None, "行级折扣"),

    # --- 商品
    ("item_name",       "商品名称",         "DIMENSION",  "string",         T_NAME,     None, None, None, None, "商品标题"),
    ("category_id",     "类目ID",           "DIMENSION",  "string",         T_ID,       None, None, None, None, "末级类目标识"),
    ("category_name",   "类目名称",         "DIMENSION",  "string",         T_NAME,     None, None, None, None, "末级类目名称"),
    ("brand_name",      "品牌",             "DIMENSION",  "string",         T_NAME,     None, None, None, None, "商品品牌"),
    ("item_price",      "商品标价",         "DIMENSION",  "decimal(18,2)",  223,        257,  None, None, None, "挂牌单价，维表属性"),
    ("item_cost",       "商品成本",         "METRIC",     "decimal(18,2)",  224,        257,  None, None, None, "成本口径"),
    ("item_status",     "商品状态",         "DIMENSION",  "string",         T_STATUS,   None, None, "item_status", None, "上架/下架状态，需归一"),

    # --- 客户
    ("reg_time",        "注册时间",         "DIMENSION",  "datetime",       T_DATETIME, None, None, None, None, "客户注册时点"),
    ("reg_source",      "注册来源",         "DIMENSION",  "string",         T_TYPE,     None, None, "register_source", None, "注册渠道，需归一"),
    ("gender",          "性别",             "DIMENSION",  "string",         T_TYPE,     None, None, "gender", None, "M/F/1/2/男/女 需归一"),
    ("birthday",        "生日",             "DIMENSION",  "string",         T_DATE,     None, None, None, 194, "多格式，属 L2 个人信息"),
    ("member_level",    "会员等级",         "DIMENSION",  "string",         T_LEVEL,    None, None, "member_level", None, "1/2/3/VIP/GOLD 需归一"),
    ("province_name",   "省份",             "DIMENSION",  "string",         T_NAME,     None, None, None, None, "客户所在省份"),
    ("city_name",       "城市",             "DIMENSION",  "string",         T_NAME,     None, None, None, None, "客户所在城市"),
    ("cust_status",     "客户状态",         "DIMENSION",  "string",         T_STATUS,   None, None, "user_status", None, "0/1/active/正常 需归一"),
    ("email",           "电子邮箱",         "DIMENSION",  "string",         T_EMAIL,    None, None, None, 175, "L2 个人信息"),

    # --- 地址
    ("addr_id",         "地址ID",           "DIMENSION",  "string",         T_ID,       None, None, None, None, "收货地址主键"),
    ("receiver_name",   "收货人姓名",       "DIMENSION",  "string",         T_NAME,     None, None, None, 176, "L2 个人信息"),
    ("receiver_mobile", "收货人手机号",     "DIMENSION",  "string",         T_MOBILE,   None, None, None, 172, "明文存储，需脱敏"),
    ("receiver_address","收货详细地址",     "DIMENSION",  "string",         T_ADDRESS,  None, None, None, 177, "L2 个人信息"),
    ("is_default_addr", "是否默认地址",     "DIMENSION",  "tinyint",        T_FLAG,     None, None, "yes_no", None, "0/1/Y/N 需归一"),

    # --- 客户历史（SCD2）
    ("history_id",      "历史版本ID",       "PROCESS",    "string",         T_ID,       None, None, None, None, "SCD2 记录主键"),
    ("scd_version",     "拉链版本号",       "PROCESS",    "int",            202,        None, None, None, None, "同一客户递增"),
    ("effective_time",  "生效时间",         "PROCESS",    "datetime",       T_DATETIME, None, None, None, None, "版本开始生效"),
    ("expire_time",     "失效时间",         "PROCESS",    "datetime",       T_DATETIME, None, None, None, None, "NULL 表示当前有效"),
    ("is_current_version", "是否当前版本",  "DIMENSION",  "tinyint",        T_FLAG,     None, None, "yes_no", None, "1=当前有效版本"),
    ("change_reason",   "变更原因",         "DIMENSION",  "string",         T_DESC,     None, None, None, None, "本次拉链变更说明"),

    # --- 支付
    ("pay_id",          "支付ID",           "PROCESS",    "string",         T_ID,       None, None, None, None, "支付流水主键"),
    ("pay_amount",      "支付金额",         "METRIC",     "decimal(18,2)",  T_AMOUNT,   257,  4,    None, None, "支付流水实付，3% 与订单应付不一致"),
    ("pay_channel",     "支付渠道",         "DIMENSION",  "string",         T_TYPE,     None, None, "pay_channel", None, "微信/支付宝等，需归一"),
    ("pay_status",      "支付状态",         "DIMENSION",  "string",         T_STATUS,   None, None, "pay_status", None, "需归一"),
    ("trade_no",        "第三方流水号",     "PROCESS",    "string",         200,        None, None, None, None, "第三方支付交易号"),
    ("pay_success_time","支付成功时间",     "DIMENSION",  "datetime",       T_DATETIME, None, None, None, None, "渠道回调时间"),

    # --- 退款
    ("refund_id",       "退款ID",           "PROCESS",    "string",         T_ID,       None, None, None, None, "退款单主键"),
    ("refund_amount",   "退款金额",         "METRIC",     "decimal(18,2)",  T_AMOUNT,   257,  5,    None, None, "存在 2% 大于实付的脏数据"),
    ("refund_status",   "退款状态",         "DIMENSION",  "string",         T_STATUS,   None, None, "refund_status", None, "需归一"),
    ("refund_time",     "退款时间",         "DIMENSION",  "datetime",       T_DATETIME, None, None, None, None, "退款完成时间"),
    ("refund_reason",   "退款原因",         "DIMENSION",  "string",         T_DESC,     None, None, None, None, "退款原因文本"),
    ("net_gmv",         "净GMV",            "METRIC",     "decimal(18,2)",  T_AMOUNT,   257,  6,    None, None, "GMV - 退款"),

    # --- 营销
    ("activity_id",     "活动ID",           "PROCESS",    "string",         T_ID,       None, None, None, None, "营销活动主键"),
    ("activity_name",   "活动名称",         "DIMENSION",  "string",         T_NAME,     None, None, None, None, "活动名称"),
    ("activity_type",   "活动类型",         "DIMENSION",  "string",         T_TYPE,     None, None, "promotion_type", None, "满减/折扣/秒杀等"),
    ("activity_status", "活动状态",         "DIMENSION",  "string",         T_STATUS,   None, None, "activity_status", None, "需归一"),
    ("activity_start_time", "活动开始时间", "DIMENSION",  "datetime",       T_DATETIME, None, None, None, None, "活动生效开始"),
    ("activity_end_time",   "活动结束时间", "DIMENSION",  "datetime",       T_DATETIME, None, None, None, None, "活动生效结束"),
    ("activity_budget", "活动预算",         "METRIC",     "decimal(18,2)",  T_AMOUNT,   257,  None, None, None, "投放预算"),
    ("promotion_gmv",   "活动GMV",          "METRIC",     "decimal(18,2)",  T_AMOUNT,   257,  30,   None, None, "归因到活动的成交额"),
    ("promotion_roi",   "活动ROI",          "METRIC",     "decimal(9,4)",   233,        294,  32,   None, None, "活动GMV / 预算"),
    ("coupon_id",       "优惠券ID",         "PROCESS",    "string",         T_ID,       None, None, None, None, "券实例主键"),
    ("coupon_code",     "券码",             "DIMENSION",  "string",         T_CODE,     None, None, None, None, "券兑换码"),
    ("coupon_type",     "优惠券类型",       "DIMENSION",  "string",         T_TYPE,     None, None, "coupon_type", None, "满减券/折扣券等"),
    ("coupon_amount",   "优惠券面额",       "METRIC",     "decimal(18,2)",  T_AMOUNT,   257,  None, None, None, "券面值"),
    ("coupon_status",   "优惠券状态",       "DIMENSION",  "string",         T_STATUS,   None, None, "coupon_status", None, "未使用/已使用/已过期"),
    ("receive_time",    "领券时间",         "DIMENSION",  "datetime",       T_DATETIME, None, None, None, None, "券领取时间"),
    ("coupon_use_time", "用券时间",         "DIMENSION",  "datetime",       T_DATETIME, None, None, None, None, "券核销时间"),
    ("coupon_use_rate", "优惠券使用率",     "METRIC",     "decimal(9,4)",   233,        292,  29,   None, None, "核销数 / 领取数"),

    # --- 履约
    ("delivery_id",     "发货单ID",         "PROCESS",    "string",         T_ID,       None, None, None, None, "发货单主键"),
    ("logistics_no",    "物流单号",         "DIMENSION",  "string",         200,        None, None, None, None, "运单号"),
    ("logistics_company","物流公司",        "DIMENSION",  "string",         T_TYPE,     None, None, "logistics_company", None, "需归一"),
    ("delivery_status", "配送状态",         "DIMENSION",  "string",         T_STATUS,   None, None, "delivery_status", None, "需归一"),
    ("delivery_time",   "发货时间",         "DIMENSION",  "datetime",       T_DATETIME, None, None, None, None, "仓库出库时间"),
    ("sign_time",       "签收时间",         "DIMENSION",  "datetime",       T_DATETIME, None, None, None, None, "存在 1% 早于发货的脏数据"),
    ("delivery_duration","配送时长",        "METRIC",     "bigint",         245,        290,  None, None, None, "签收时间 - 发货时间，单位小时"),
    ("inventory_id",    "库存记录ID",       "PROCESS",    "string",         T_ID,       None, None, None, None, "库存快照主键"),
    ("warehouse_id",    "仓库ID",           "DIMENSION",  "string",         T_ID,       None, None, None, None, "仓节点标识"),
    ("stock_qty",       "库存数量",         "METRIC",     "int",            232,        264,  None, None, None, "可能为负，需质量校验"),
    ("lock_qty",        "锁定数量",         "METRIC",     "int",            232,        264,  None, None, None, "占用未出库"),
    ("available_qty",   "可用数量",         "METRIC",     "int",            232,        264,  None, None, None, "5% 与 stock-lock 不一致"),
    ("snapshot_time",   "库存快照时间",     "DIMENSION",  "datetime",       T_DATETIME, None, None, None, None, "月度快照时点"),
    ("stock_log_id",    "库存流水ID",       "PROCESS",    "string",         T_ID,       None, None, None, None, "出入库流水主键"),
    ("change_type",     "库存变动类型",     "DIMENSION",  "string",         T_TYPE,     None, None, None, None, "IN/OUT/LOCK/UNLOCK"),
    ("change_qty",      "库存变动数量",     "METRIC",     "int",            230,        264,  None, None, None, "带符号变动量"),
    ("before_qty",      "变动前库存",       "METRIC",     "int",            232,        264,  None, None, None, "流水前置值"),
    ("after_qty",       "变动后库存",       "METRIC",     "int",            232,        264,  None, None, None, "1% 与 before+change 不等"),
    ("stock_turnover_rate", "库存周转率",   "METRIC",     "decimal(9,4)",   233,        294,  26,   None, None, "出库量 / 平均可用库存"),

    # --- 售后
    ("ticket_id",       "售后工单ID",       "PROCESS",    "string",         T_ID,       None, None, None, None, "工单主键"),
    ("ticket_type",     "售后类型",         "DIMENSION",  "string",         T_TYPE,     None, None, None, None, "仅退款/退货退款/换货"),
    ("ticket_status",   "工单状态",         "DIMENSION",  "string",         T_STATUS,   None, None, "audit_status", None, "需归一"),
    ("ticket_apply_time", "工单申请时间",   "DIMENSION",  "datetime",       T_DATETIME, None, None, None, None, "客户发起时点"),
    ("ticket_finish_time","工单完成时间",   "DIMENSION",  "datetime",       T_DATETIME, None, None, None, None, "10% 早于申请时间，需校验"),
    ("ticket_reason",   "售后原因",         "DIMENSION",  "string",         T_DESC,     None, None, None, None, "客户填写原因"),
    ("item_return_rate","商品退货率",       "METRIC",     "decimal(9,4)",   233,        292,  27,   None, None, "退货量 / 销量"),

    # --- 结算
    ("settle_id",       "结算单ID",         "PROCESS",    "string",         T_ID,       None, None, None, None, "结算单主键"),
    ("merchant_id",     "商家ID",           "DIMENSION",  "string",         T_ID,       None, None, None, None, "平台商家标识"),
    ("settle_amount",   "商家实收金额",     "METRIC",     "decimal(18,2)",  T_AMOUNT,   257,  None, None, None, "= 实付 - 佣金，3% 偏差"),
    ("commission_amount", "平台佣金",       "METRIC",     "decimal(18,2)",  T_AMOUNT,   257,  None, None, None, "平台抽佣"),
    ("commission_rate", "佣金率",           "METRIC",     "decimal(9,4)",   233,        294,  None, None, None, "佣金 / 实付"),
    ("settle_status",   "结算状态",         "DIMENSION",  "string",         T_STATUS,   None, None, "settle_status", None, "需归一"),
    ("settle_cycle",    "结算周期",         "DIMENSION",  "string",         T_TYPE,     None, None, None, None, "T+1/周/月，需归一"),
    ("settle_currency", "结算币种",         "DIMENSION",  "string",         T_CURRENCY, None, None, "currency_code", None, "CNY/USD"),
    ("settle_time",     "结算时间",         "DIMENSION",  "datetime",       T_DATETIME, None, None, None, None, "出账时点"),

    # --- 评价
    ("review_id",       "评价ID",           "PROCESS",    "string",         T_ID,       None, None, None, None, "评价主键"),
    ("review_score",    "商品评分",         "METRIC",     "decimal(9,2)",   235,        None, None, None, None, "评分口径不统一，需归一"),
    ("review_content",  "评价内容",         "DIMENSION",  "string",         217,        None, None, None, None, "文本内容"),
    ("is_anonymous",    "是否匿名评价",     "DIMENSION",  "tinyint",        T_FLAG,     None, None, "yes_no", None, "需归一"),
    ("review_time",     "评价时间",         "DIMENSION",  "datetime",       T_DATETIME, None, None, None, None, "1% 早于签收，需校验"),
    ("avg_review_score","平均评分",         "METRIC",     "decimal(9,2)",   235,        None, 37,   None, None, "商品维度平均星级"),

    # --- 汇总层派生指标（DWS/ADS 反推的落点）
    ("gmv",             "成交总额GMV",      "METRIC",     "decimal(18,2)",  T_AMOUNT,   257,  1,    None, None, "已支付订单金额合计"),
    ("order_cnt",       "订单量",           "METRIC",     "bigint",         231,        263,  3,    None, None, "订单数"),
    ("avg_order_amount","客单价",           "METRIC",     "decimal(18,2)",  T_AMOUNT,   257,  7,    None, None, "GMV / 订单量"),
    ("order_user_cnt",  "下单用户数",       "METRIC",     "bigint",         231,        264,  11,   None, None, "去重客户数"),
    ("pay_user_cnt",    "支付用户数",       "METRIC",     "bigint",         231,        264,  12,   None, None, "支付成功去重客户数"),
    ("pay_rate",        "支付率",           "METRIC",     "decimal(9,4)",   233,        292,  10,   None, None, "支付订单 / 下单订单"),
    ("cancel_rate",     "取消率",           "METRIC",     "decimal(9,4)",   233,        292,  9,    None, None, "取消订单 / 下单订单"),
    ("refund_rate",     "退款率",           "METRIC",     "decimal(9,4)",   233,        292,  8,    None, None, "退款金额 / GMV"),
    ("new_user_cnt",    "新增用户数",       "METRIC",     "bigint",         231,        264,  15,   None, None, "当期首次注册客户数"),
    ("active_user_cnt", "活跃用户数",       "METRIC",     "bigint",         231,        264,  16,   None, None, "当期有下单行为的客户数"),
    ("arpu",            "ARPU",             "METRIC",     "decimal(18,2)",  T_AMOUNT,   257,  19,   None, None, "GMV / 活跃用户数"),
    ("user_avg_order_cnt", "人均订单数",    "METRIC",     "decimal(9,4)",   233,        263,  20,   None, None, "订单量 / 下单用户数"),
    ("retention_rate",  "次日留存率",       "METRIC",     "decimal(9,4)",   233,        292,  17,   None, None, "次日回访 / 当日新增"),
    ("item_sale_cnt",   "商品销量",         "METRIC",     "bigint",         230,        263,  21,   None, None, "明细数量合计"),
    ("item_sale_amount","商品销售额",       "METRIC",     "decimal(18,2)",  T_AMOUNT,   257,  22,   None, None, "明细金额合计"),
    ("item_avg_price",  "商品成交均价",     "METRIC",     "decimal(18,2)",  223,        257,  28,   None, None, "销售额 / 销量"),
]

# ---------------------------------------------------------------- 业务过程
# code, name, domain_code, grain, biz_type, 描述, sources[(ds, table, role, join)], fields[(code, required)]

PROCESSES = [
    ("order_create", "下单", "order", "订单", "FACT",
     "客户提交订单，主键 order_id；已存在，仅补充字段绑定",
     [("trade", "trade_order", "MAIN", None), ("trade", "trade_order_detail", "DETAIL", "d.order_id = t.order_id")],
     [("order_id", 1), ("order_no", 1), ("cust_id", 1), ("order_city", 0), ("order_time", 1),
      ("order_status", 1), ("order_amount", 1), ("order_discount_amount", 0), ("order_freight_amount", 0),
      ("order_channel", 0), ("biz_date", 1), ("etl_time", 1)]),

    ("purchase_item", "购买商品", "order", "订单明细行", "FACT",
     "订单明细粒度，一行一个 SKU",
     [("trade", "trade_order_detail", "MAIN", None), ("trade", "trade_order", "DIM", "o.order_id = d.order_id"),
      ("item", "item_item", "DIM", "i.item_id = d.item_id")],
     [("detail_id", 1), ("order_id", 1), ("item_id", 1), ("item_name", 0), ("quantity", 1),
      ("unit_price", 0), ("line_amount", 1), ("line_discount", 0), ("biz_date", 1), ("etl_time", 1)]),

    ("order_pay", "订单支付", "pay", "支付单", "FACT",
     "支付流水粒度，一笔订单可多次支付尝试",
     [("pay", "pay_payment", "MAIN", None), ("trade", "trade_order", "DIM", "o.order_id = p.order_id")],
     [("pay_id", 1), ("order_id", 1), ("cust_id", 1), ("pay_amount", 1), ("pay_channel", 1),
      ("pay_status", 1), ("pay_success_time", 1), ("trade_no", 0), ("biz_date", 1), ("etl_time", 1)]),

    ("order_cancel", "订单取消", "order", "订单", "FACT",
     "以取消时间非空判定，用于取消率",
     [("trade", "trade_order", "MAIN", None)],
     [("order_id", 1), ("cancel_time", 1), ("cancel_reason", 0), ("is_cancelled", 1), ("biz_date", 1), ("etl_time", 1)]),

    ("order_finish", "订单完成", "order", "订单", "FACT",
     "交易成功口径，用于确认收入",
     [("trade", "trade_order", "MAIN", None)],
     [("order_id", 1), ("finish_time", 1), ("order_amount", 1), ("net_gmv", 0), ("biz_date", 1), ("etl_time", 1)]),

    ("refund_apply", "退款", "refund", "退款单", "FACT",
     "退款流水，2% 金额大于实付需质量拦截",
     [("pay", "pay_refund", "MAIN", None), ("pay", "pay_payment", "DIM", "pp.pay_id = r.pay_id")],
     [("refund_id", 1), ("order_id", 1), ("refund_amount", 1), ("refund_status", 1),
      ("refund_time", 1), ("refund_reason", 0), ("biz_date", 1), ("etl_time", 1)]),

    ("delivery_send", "发货", "delivery", "发货单", "FACT",
     "仓库出库发运",
     [("wms", "wms_delivery", "MAIN", None), ("trade", "trade_order", "DIM", "o.order_id = dl.order_id")],
     [("delivery_id", 1), ("order_id", 1), ("logistics_no", 1), ("logistics_company", 0),
      ("delivery_status", 1), ("delivery_time", 1), ("biz_date", 1), ("etl_time", 1)]),

    ("delivery_sign", "签收", "delivery", "发货单", "FACT",
     "客户签收，与发货时间差为配送时长",
     [("wms", "wms_delivery", "MAIN", None)],
     [("delivery_id", 1), ("order_id", 1), ("sign_time", 1), ("delivery_time", 1),
      ("delivery_duration", 0), ("biz_date", 1), ("etl_time", 1)]),

    ("stock_change", "库存变动", "inventory", "库存流水", "FACT",
     "出入库流水，可还原库存快照",
     [("wms", "wms_stock_log", "MAIN", None), ("item", "item_item", "DIM", "i.item_id = l.item_id")],
     [("stock_log_id", 1), ("item_id", 1), ("order_id", 0), ("change_type", 1), ("change_qty", 1),
      ("before_qty", 0), ("after_qty", 1), ("log_time_dim", 1), ("etl_time", 1)]),

    ("stock_snapshot", "库存快照", "inventory", "商品+仓+月", "FACT",
     "月度库存快照",
     [("wms", "wms_inventory", "MAIN", None), ("item", "item_item", "DIM", "i.item_id = inv.item_id")],
     [("inventory_id", 1), ("item_id", 1), ("warehouse_id", 1), ("stock_qty", 1), ("lock_qty", 1),
      ("available_qty", 1), ("snapshot_time", 1), ("biz_month", 1), ("etl_time", 1)]),

    ("coupon_receive", "领券", "coupon", "券实例", "FACT",
     "优惠券发放/领取",
     [("mkt", "mkt_coupon", "MAIN", None), ("mkt", "mkt_activity", "DIM", "a.activity_id = c.activity_id")],
     [("coupon_id", 1), ("coupon_code", 1), ("activity_id", 1), ("cust_id", 1), ("coupon_type", 1),
      ("coupon_amount", 1), ("receive_time", 1), ("biz_date", 1), ("etl_time", 1)]),

    ("coupon_use", "用券", "coupon", "券实例", "FACT",
     "优惠券核销，回填订单号",
     [("mkt", "mkt_coupon", "MAIN", None), ("trade", "trade_order", "DIM", "o.order_id = c.order_id")],
     [("coupon_id", 1), ("order_id", 1), ("coupon_status", 1), ("coupon_use_time", 1),
      ("coupon_amount", 1), ("biz_date", 1), ("etl_time", 1)]),

    ("activity_launch", "活动投放", "activity", "活动", "FACT",
     "活动档期与预算",
     [("mkt", "mkt_activity", "MAIN", None)],
     [("activity_id", 1), ("activity_name", 1), ("activity_type", 1), ("activity_status", 1),
      ("activity_start_time", 1), ("activity_end_time", 1), ("activity_budget", 1), ("etl_time", 1)]),

    ("aftersale_apply", "售后申请", "ticket", "工单", "FACT",
     "客户发起售后工单",
     [("aftersale", "aftersale_ticket", "MAIN", None), ("pay", "pay_refund", "DIM", "rf.refund_id = t.refund_id")],
     [("ticket_id", 1), ("order_id", 1), ("cust_id", 1), ("ticket_type", 1), ("ticket_status", 1),
      ("refund_id", 0), ("ticket_apply_time", 1), ("ticket_reason", 0), ("biz_date", 1), ("etl_time", 1)]),

    ("aftersale_finish", "售后完成", "ticket", "工单", "FACT",
     "工单闭环，用于售后时效；10% 完成早于申请需拦截",
     [("aftersale", "aftersale_ticket", "MAIN", None)],
     [("ticket_id", 1), ("ticket_finish_time", 1), ("ticket_status", 1), ("biz_date", 1), ("etl_time", 1)]),

    ("settle_out", "结算出账", "settle", "结算单", "FACT",
     "商家结算，实收 = 实付 - 佣金",
     [("settle", "set_settlement", "MAIN", None), ("trade", "trade_order", "DIM", "o.order_id = s.order_id")],
     [("settle_id", 1), ("order_id", 1), ("merchant_id", 1), ("settle_amount", 1), ("commission_amount", 1),
      ("settle_status", 1), ("settle_cycle", 0), ("settle_currency", 1), ("settle_time", 1),
      ("biz_month", 1), ("etl_time", 1)]),

    ("review_publish", "发表评价", "product_review", "评价", "FACT",
     "客户对订单商品评价，5% 与订单脱钩",
     [("review", "review_product", "MAIN", None), ("trade", "trade_order", "DIM", "o.order_id = rv.order_id"),
      ("item", "item_item", "DIM", "i.item_id = rv.item_id")],
     [("review_id", 1), ("order_id", 1), ("item_id", 1), ("cust_id", 1), ("review_score", 1),
      ("review_content", 0), ("is_anonymous", 0), ("review_time", 1), ("biz_date", 1), ("etl_time", 1)]),

    ("customer_register", "客户注册", "customer_profile", "客户", "FACT",
     "客户建档，新增用户数口径来源",
     [("crm", "crm_customer", "MAIN", None)],
     [("cust_id", 1), ("reg_time", 1), ("reg_source", 1), ("member_level", 0), ("cust_status", 1),
      ("biz_date", 1), ("etl_time", 1)]),

    ("customer_info_change", "客户信息变更", "customer_scd", "客户版本", "FACT",
     "SCD2 拉链变更，还原任意时点画像",
     [("crm", "crm_customer_history", "MAIN", None), ("crm", "crm_customer", "DIM", "c.cust_id = h.cust_id")],
     [("history_id", 1), ("cust_id", 1), ("scd_version", 1), ("city_name", 0), ("province_name", 0),
      ("member_level", 0), ("effective_time", 1), ("expire_time", 0), ("is_current_version", 1),
      ("change_reason", 0), ("etl_time", 1)]),

    # ---- 维度过程（DIMENSION）
    ("dim_customer", "客户维度", "customer_profile", "客户", "DIMENSION",
     "客户当前态主数据，含敏感字段分级",
     [("crm", "crm_customer", "MAIN", None)],
     [("cust_id", 1), ("cust_name", 1), ("cust_mobile", 0), ("cust_idcard", 0), ("gender", 0),
      ("birthday", 0), ("email", 0), ("city_name", 0), ("province_name", 0), ("member_level", 0),
      ("reg_time", 0), ("reg_source", 0), ("cust_status", 1)]),

    ("dim_customer_address", "收货地址维度", "customer_addr", "客户+地址", "DIMENSION",
     "客户收货地址多值维表",
     [("crm", "crm_customer_address", "MAIN", None), ("crm", "crm_customer", "DIM", "c.cust_id = a.cust_id")],
     [("addr_id", 1), ("cust_id", 1), ("receiver_name", 1), ("receiver_mobile", 0),
      ("receiver_address", 1), ("is_default_addr", 0)]),

    ("dim_item", "商品维度", "item", "商品", "DIMENSION",
     "商品主数据，含类目与品牌",
     [("item", "item_item", "MAIN", None)],
     [("item_id", 1), ("item_name", 1), ("category_id", 1), ("category_name", 0), ("brand_name", 0),
      ("item_price", 0), ("item_cost", 0), ("item_status", 1)]),

    ("dim_activity", "活动维度", "activity", "活动", "DIMENSION",
     "营销活动主数据",
     [("mkt", "mkt_activity", "MAIN", None)],
     [("activity_id", 1), ("activity_name", 1), ("activity_type", 1), ("activity_status", 1),
      ("activity_start_time", 1), ("activity_end_time", 1), ("activity_budget", 1)]),
]

# 源表里没有语义标准对应、但过程绑定要用的列
EXTRA_FIELDS = [
    ("log_time_dim", "库存变动时间", "PROCESS", "datetime", T_DATETIME, None, None, None, None, "wms_stock_log.log_time"),
]

# 需要按本次标准统一口径重写的既有字段（回滚时按快照还原）
# field_code -> (std_type, std_unit, std_caliber, code_set, std_security, data_type)
PATCH_FIELDS = {
    "order_time":   (T_DATETIME, None, None, None, None, "datetime"),
    "order_status": (T_STATUS, None, None, "order_status", None, "string"),
    "order_amount": (T_AMOUNT, 257, 1, None, None, "decimal(18,2)"),
    "quantity":     (230, 263, 21, None, None, "int"),
    "cust_name":    (T_NAME, None, None, None, 176, "string"),
}

LAYER_TARGETS = {
    "DWS": ("dws", "数仓-dws"),
    "ADS": ("ads", "数仓-ads"),
}


def connect():
    return pymysql.connect(**DSN)


def snapshot_patchable(conn):
    """记录将被改动的既有行，供 --rollback 还原。已有快照时不覆盖，避免重跑把改后值当成原值。"""
    if os.path.exists(SNAPSHOT):
        return
    cur = conn.cursor()
    cur.execute(
        "select id,field_code,std_type_id,std_unit_id,std_caliber_id,std_code_set_code,std_security_id,data_type "
        "from yak_semantic_field where project_id=%s", (PROJECT_ID,))
    keys = [d[0] for d in cur.description]
    fields = [dict(zip(keys, r)) for r in cur.fetchall() if r[1] in PATCH_FIELDS]
    cur.execute("select id,layer_code,database_name,datasource_id from yak_semantic_layer where project_id=%s",
                (PROJECT_ID,))
    keys = [d[0] for d in cur.description]
    layers = [dict(zip(keys, r)) for r in cur.fetchall() if r[1] in LAYER_TARGETS]
    with open(SNAPSHOT, "w", encoding="utf-8") as fh:
        json.dump({"fields": fields, "layers": layers}, fh, ensure_ascii=False, indent=1, default=str)
    return fields, layers


def rollback(conn):
    cur = conn.cursor()
    with open(SNAPSHOT, encoding="utf-8") as fh:
        snap = json.load(fh)
    for row in snap.get("fields", []):
        cur.execute(
            "update yak_semantic_field set std_type_id=%s,std_unit_id=%s,std_caliber_id=%s,"
            "std_code_set_code=%s,std_security_id=%s,data_type=%s where id=%s",
            (row["std_type_id"], row["std_unit_id"], row["std_caliber_id"], row["std_code_set_code"],
             row["std_security_id"], row["data_type"], row["id"]))
    for row in snap.get("layers", []):
        cur.execute("update yak_semantic_layer set database_name=%s,datasource_id=%s where id=%s",
                    (row["database_name"], row["datasource_id"], row["id"]))
    cur.execute("delete from yak_semantic_process_field where project_id=%s and created_by=%s", (PROJECT_ID, TAG))
    cur.execute("delete from yak_semantic_process_source where project_id=%s and created_by=%s", (PROJECT_ID, TAG))
    cur.execute("delete from yak_semantic_process where project_id=%s and created_by=%s", (PROJECT_ID, TAG))
    cur.execute("delete from yak_semantic_field where project_id=%s and created_by=%s", (PROJECT_ID, TAG))
    cur.execute("delete from yak_semantic_domain where project_id=%s and created_by=%s", (PROJECT_ID, TAG))
    cur.execute("delete from yak_ops_data_source where project_id=%s and remark=%s", (PROJECT_ID, TAG))
    conn.commit()
    os.remove(SNAPSHOT)
    print("rollback done")


def seed(conn):
    cur = conn.cursor()

    # 1) 数仓 dws / ads 数据源（复用 dwd 的连接参数，只换库名）
    # environment/conn_status 必须写枚举真名：DataSourceConnStatus 无 SUCCESS 常量，
    # 写错会让数据源列表接口整表抛异常兜底成 999。
    cur.execute("select id,connection_params from yak_ops_data_source "
                "where project_id=%s and name=%s", (PROJECT_ID, "数仓-dwd"))
    row = cur.fetchone()
    ds_new = {}
    if row:
        for code, (db, label) in LAYER_TARGETS.items():
            cur.execute("select id from yak_ops_data_source where project_id=%s and name=%s", (PROJECT_ID, label))
            hit = cur.fetchone()
            if not hit:
                cfg = json.loads(row[1])
                cfg["database"] = db
                cfg["jdbcUrl"] = "jdbc:mysql://{}:{}/{}".format(cfg["host"], cfg["port"], db)
                blob = json.dumps(cfg, ensure_ascii=False)
                cur.execute(
                    "insert into yak_ops_data_source(project_id,name,db_type,jdbc_url,environment,conn_status,"
                    "remark,connection_params,original_json) values(%s,%s,'MYSQL',%s,'DEVELOP','CONNECTED',%s,%s,%s)",
                    (PROJECT_ID, label, cfg["jdbcUrl"], TAG, blob, blob))
                hit = (cur.lastrowid,)
            ds_new[code] = hit[0]

    # 2) 业务域
    domain_id = {}
    cur.execute("select id,domain_code from yak_semantic_domain where project_id=%s", (PROJECT_ID,))
    for i, c in cur.fetchall():
        domain_id[c] = i
    for order, (code, name, parent, desc) in enumerate(DOMAINS):
        if code in domain_id:
            continue
        cur.execute(
            "insert into yak_semantic_domain(project_id,domain_code,domain_name,parent_id,description,sort_order,created_by)"
            " values(%s,%s,%s,%s,%s,%s,%s)",
            (PROJECT_ID, code, name, domain_id.get(parent, 0) if parent else 0, desc, order, TAG))
        domain_id[code] = cur.lastrowid

    # 3) 标准字段
    field_id = {}
    cur.execute("select id,field_code from yak_semantic_field where project_id=%s", (PROJECT_ID,))
    for i, c in cur.fetchall():
        field_id[c] = i
    for f in FIELDS + EXTRA_FIELDS:
        code, name, role, dtype, stype, unit, caliber, codeset, sec, desc = f
        if code in field_id:
            continue
        cur.execute(
            "insert into yak_semantic_field(project_id,field_code,field_name,role,status,data_type,std_type_id,"
            "std_unit_id,std_caliber_id,std_code_set_code,std_security_id,business_desc,source,version,created_by)"
            " values(%s,%s,%s,%s,'ENABLED',%s,%s,%s,%s,%s,%s,%s,'MANUAL',1,%s)",
            (PROJECT_ID, code, name, role, dtype, stype, unit, caliber, codeset, sec, desc, TAG))
        field_id[code] = cur.lastrowid

    # 4) 既有字段的标准引用补齐（可回滚）
    cur.execute("select field_code,id from yak_semantic_field where project_id=%s", (PROJECT_ID,))
    existing = dict(cur.fetchall())
    for code, (stype, unit, caliber, codeset, sec, dtype) in PATCH_FIELDS.items():
        fid = existing.get(code)
        if not fid:
            continue
        cur.execute(
            "update yak_semantic_field set std_type_id=%s,std_unit_id=%s,std_caliber_id=%s,"
            "std_code_set_code=%s,std_security_id=%s,data_type=%s where id=%s",
            (stype, unit, caliber, codeset, sec, dtype, fid))

    # 5) 业务过程 + 数据来源 + 字段绑定
    process_id = {}
    cur.execute("select id,process_code from yak_semantic_process where project_id=%s", (PROJECT_ID,))
    for i, c in cur.fetchall():
        process_id[c] = i

    for order, p in enumerate(PROCESSES):
        code, name, dom, grain, biz, desc, sources, fields = p
        if code not in process_id:
            cur.execute(
                "insert into yak_semantic_process(project_id,process_code,process_name,domain_id,grain,biz_type,"
                "description,sort_order,created_by) values(%s,%s,%s,%s,%s,%s,%s,%s,%s)",
                (PROJECT_ID, code, name, domain_id[dom], grain, biz, desc, order, TAG))
            process_id[code] = cur.lastrowid
        pid = process_id[code]

        # 唯一键是 (project, process, datasource, table)，不含 table_role
        cur.execute("select datasource_id,source_table from yak_semantic_process_source "
                    "where project_id=%s and process_id=%s", (PROJECT_ID, pid))
        bound = set(cur.fetchall())
        for role_key in ("MAIN", "DETAIL", "DIM"):
            for ds, table, role, join in sources:
                if role != role_key or (DS[ds], table) in bound:
                    continue
                cur.execute(
                    "insert into yak_semantic_process_source(project_id,process_id,datasource_id,source_table,"
                    "table_role,join_condition,created_by) values(%s,%s,%s,%s,%s,%s,%s)",
                    (PROJECT_ID, pid, DS[ds], table, role, join, TAG))
                bound.add((DS[ds], table))

        cur.execute("select field_id from yak_semantic_process_field where project_id=%s and process_id=%s", (PROJECT_ID, pid))
        linked = {r[0] for r in cur.fetchall()}
        for idx, (fcode, required) in enumerate(fields):
            fid = field_id.get(fcode)
            if fid is None or fid in linked:
                continue
            cur.execute(
                "insert into yak_semantic_process_field(project_id,process_id,field_id,is_required,sort_order,created_by)"
                " values(%s,%s,%s,%s,%s,%s)",
                (PROJECT_ID, pid, fid, required, idx, TAG))
            linked.add(fid)

    # 6) DWS / ADS 分层落库配置
    for code, (db, label) in LAYER_TARGETS.items():
        if code in ds_new:
            cur.execute("update yak_semantic_layer set database_name=%s,datasource_id=%s where project_id=%s and layer_code=%s",
                        (db, ds_new[code], PROJECT_ID, code))
    conn.commit()

    cur.execute("select count(*) from yak_semantic_domain where project_id=%s", (PROJECT_ID,))
    nd = cur.fetchone()[0]
    cur.execute("select count(*) from yak_semantic_process where project_id=%s", (PROJECT_ID,))
    np_ = cur.fetchone()[0]
    cur.execute("select count(*) from yak_semantic_field where project_id=%s", (PROJECT_ID,))
    nf = cur.fetchone()[0]
    cur.execute("select count(*) from yak_semantic_process_field where project_id=%s", (PROJECT_ID,))
    npf = cur.fetchone()[0]
    cur.execute("select count(*) from yak_semantic_process_source where project_id=%s", (PROJECT_ID,))
    nps = cur.fetchone()[0]
    print(f"domains={nd} processes={np_} fields={nf} process_field={npf} process_source={nps}")


def main():
    conn = connect()
    try:
        if "--rollback" in sys.argv:
            rollback(conn)
        else:
            snapshot_patchable(conn)
            seed(conn)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
