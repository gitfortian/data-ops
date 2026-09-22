-- =====================================================================
-- 订单域种子数据（语义层可行性验证专供）
-- 对应 yak-ops-business-ontology V1 迁移的真实列定义（无 created_by/updated_by 审计列）
-- 对象/属性/关系/指标/术语 全部 PUBLISHED；物理绑定为占位（smoke_db.*），
-- 供 SemanticSqlCompiler 的 dry-run SQL 快照编译；execute 前需替换为真实数据源映射。
-- =====================================================================

-- ---------- 1. 业务域 ----------
INSERT INTO yak_onto_domain (id, code, name, description, parent_id)
VALUES (1, 'order', '订单域', '电商订单主流程业务域：销售单-客户-商品-指标', NULL);

-- ---------- 2. 业务对象 ----------
INSERT INTO yak_onto_object
  (id, domain_id, code, name, description, concept_type, identify_by,
   primary_key_columns, display_name_template, status, owner, lineage_asset_key)
VALUES
  (1, 1, 'sales_order', '销售单', '客户下单产生的订单记录，含商品、数量与金额', 'ENTITY',
   JSON_ARRAY('order_no'), JSON_ARRAY('order_id'), '{order_no}', 'PUBLISHED', '订单组', 'asset:smoke:sales_order'),
  (2, 1, 'customer',    '客户',     '下单客户主数据', 'ENTITY',
   JSON_ARRAY('customer_id'), JSON_ARRAY('customer_id'), '{customer_name}', 'PUBLISHED', '客户组', 'asset:smoke:customer'),
  (3, 1, 'product',     '商品',     '在售商品主数据', 'ENTITY',
   JSON_ARRAY('product_id'), JSON_ARRAY('product_id'), '{product_name}', 'PUBLISHED', '商品组', 'asset:smoke:product');

-- ---------- 3. 属性（含能力派生 allowed_operators 与 requires 约束示例） ----------
-- sales_order (object_id=1)
INSERT INTO yak_onto_attribute
  (id, object_id, logical_name, display_name, description, data_type, role, is_time,
   is_pk, is_nullable, ds_id, db_name, tbl_name, col_name, physical_type,
   allowed_operators, requires_json, ai_context, mapping_source)
VALUES
  (11, 1, 'order_id',   '订单ID',   '订单主键', 'number', 'ATTRIBUTE', NULL, 1, 0, 1, 'smoke_db', 't_sales_order', 'order_id', 'BIGINT',
   JSON_ARRAY('eq','in'), NULL,
   JSON_OBJECT('instructions','内部主键，不直接用于过滤条件','synonyms',JSON_ARRAY('订单主键'),'examples',JSON_ARRAY()), 'MANUAL'),
  (12, 1, 'order_no',   '订单号',   '业务单号，复合标识', 'string', 'ATTRIBUTE', NULL, 0, 0, 1, 'smoke_db', 't_sales_order', 'order_no', 'VARCHAR',
   JSON_ARRAY('eq','in'), NULL,
   JSON_OBJECT('synonyms',JSON_ARRAY('单号','销售单号'),'examples',JSON_ARRAY('查订单号 SO2026xxxx 的明细')), 'MANUAL'),
  (13, 1, 'order_date', '下单日期', '下单时间（时间维）', 'date', 'DIMENSION', 1, 0, 0, 1, 'smoke_db', 't_sales_order', 'order_date', 'DATE',
   JSON_ARRAY('eq','between'), NULL,
   JSON_OBJECT('synonyms',JSON_ARRAY('下单时间','日期'),'examples',JSON_ARRAY('近 7 天下单量')), 'MANUAL'),
  (14, 1, 'order_status','订单状态','PAID/UNPAID/CANCELLED', 'string', 'DIMENSION', NULL, 0, 0, 1, 'smoke_db', 't_sales_order', 'order_status', 'VARCHAR',
   JSON_ARRAY('eq','in','not_in'), NULL,
   JSON_OBJECT('instructions','口径关键属性：仅 PAID 计入销售额','synonyms',JSON_ARRAY('状态','支付状态','已支付','未支付'),'examples',JSON_ARRAY('只看已支付订单')), 'MANUAL'),
  (15, 1, 'customer_id','客户ID',   '关联客户对象', 'number', 'ATTRIBUTE', NULL, 0, 1, 1, 'smoke_db', 't_sales_order', 'customer_id', 'BIGINT',
   JSON_ARRAY('eq','in'), NULL,
   JSON_OBJECT('synonyms',JSON_ARRAY('客户'),'examples',JSON_ARRAY()), 'MANUAL'),
  (16, 1, 'product_id', '商品ID',   '关联商品对象', 'number', 'ATTRIBUTE', NULL, 0, 1, 1, 'smoke_db', 't_sales_order', 'product_id', 'BIGINT',
   JSON_ARRAY('eq','in'), NULL,
   JSON_OBJECT('synonyms',JSON_ARRAY('商品'),'examples',JSON_ARRAY()), 'MANUAL'),
  (17, 1, 'quantity',   '数量',     '下单件数', 'number', 'MEASURE', NULL, 0, 1, 1, 'smoke_db', 't_sales_order', 'quantity', 'INT',
   JSON_ARRAY('eq','gt','gte','lt','lte','between'), JSON_ARRAY(JSON_OBJECT('type','ATTR_VALUE_DOMAIN','attr','quantity','op','gt','value',0)),
   JSON_OBJECT('synonyms',JSON_ARRAY('件数','下单数量'),'examples',JSON_ARRAY('购买数量超过 3 件的订单')), 'MANUAL'),
  (18, 1, 'amount',     '金额',     '订单实付金额', 'number', 'MEASURE', NULL, 0, 1, 1, 'smoke_db', 't_sales_order', 'amount', 'DECIMAL',
   JSON_ARRAY('eq','gt','gte','lt','lte','between'), JSON_ARRAY(JSON_OBJECT('type','ATTR_VALUE_DOMAIN','attr','amount','op','gte','value',0)),
   JSON_OBJECT('synonyms',JSON_ARRAY('销售额','金额','实付','GMV'),'examples',JSON_ARRAY('销售额','成交额','客单价')), 'MANUAL'),
  (19, 1, 'unit_price', '单价',     '商品单价快照', 'number', 'ATTRIBUTE', NULL, 0, 1, 1, 'smoke_db', 't_sales_order', 'unit_price', 'DECIMAL',
   JSON_ARRAY('eq','gt','gte','lt','lte','between'), NULL,
   JSON_OBJECT('synonyms',JSON_ARRAY('价格','售价'),'examples',JSON_ARRAY()), 'MANUAL');

-- customer (object_id=2)
INSERT INTO yak_onto_attribute
  (id, object_id, logical_name, display_name, description, data_type, role, is_time,
   is_pk, is_nullable, ds_id, db_name, tbl_name, col_name, physical_type,
   allowed_operators, requires_json, ai_context, mapping_source)
VALUES
  (21, 2, 'customer_id',   '客户ID',   '客户主键', 'number', 'ATTRIBUTE', NULL, 1, 0, 1, 'smoke_db', 't_customer', 'customer_id', 'BIGINT',
   JSON_ARRAY('eq','in'), NULL, JSON_OBJECT('synonyms',JSON_ARRAY('客户ID'),'examples',JSON_ARRAY()), 'MANUAL'),
  (22, 2, 'customer_name', '客户名',   '客户名称', 'string', 'DIMENSION', NULL, 0, 0, 1, 'smoke_db', 't_customer', 'customer_name', 'VARCHAR',
   JSON_ARRAY('eq','in'), NULL, JSON_OBJECT('synonyms',JSON_ARRAY('客户','名称'),'examples',JSON_ARRAY('按客户看销售额')), 'MANUAL'),
  (23, 2, 'city',          '城市',     '所在城市', 'string', 'DIMENSION', NULL, 0, 0, 1, 'smoke_db', 't_customer', 'city', 'VARCHAR',
   JSON_ARRAY('eq','in'), NULL, JSON_OBJECT('synonyms',JSON_ARRAY('地区','城市'),'examples',JSON_ARRAY('杭州客户占比')), 'MANUAL'),
  (24, 2, 'register_date', '注册日期', '客户注册时间', 'date', 'DIMENSION', 1, 0, 0, 1, 'smoke_db', 't_customer', 'register_date', 'DATE',
   JSON_ARRAY('eq','between'), NULL, JSON_OBJECT('synonyms',JSON_ARRAY('注册时间'),'examples',JSON_ARRAY('本月新注册客户数')), 'MANUAL');

-- product (object_id=3)
INSERT INTO yak_onto_attribute
  (id, object_id, logical_name, display_name, description, data_type, role, is_time,
   is_pk, is_nullable, ds_id, db_name, tbl_name, col_name, physical_type,
   allowed_operators, requires_json, ai_context, mapping_source)
VALUES
  (31, 3, 'product_id',   '商品ID',  '商品主键', 'number', 'ATTRIBUTE', NULL, 1, 0, 1, 'smoke_db', 't_product', 'product_id', 'BIGINT',
   JSON_ARRAY('eq','in'), NULL, JSON_OBJECT('synonyms',JSON_ARRAY('商品ID'),'examples',JSON_ARRAY()), 'MANUAL'),
  (32, 3, 'product_name', '商品名',  '商品名称', 'string', 'DIMENSION', NULL, 0, 0, 1, 'smoke_db', 't_product', 'product_name', 'VARCHAR',
   JSON_ARRAY('eq','in'), NULL, JSON_OBJECT('synonyms',JSON_ARRAY('商品','名称'),'examples',JSON_ARRAY('按商品看销量')), 'MANUAL'),
  (33, 3, 'category',     '品类',    '商品类目', 'string', 'DIMENSION', NULL, 0, 0, 1, 'smoke_db', 't_product', 'category', 'VARCHAR',
   JSON_ARRAY('eq','in'), NULL, JSON_OBJECT('synonyms',JSON_ARRAY('类目','品类'),'examples',JSON_ARRAY('按品类看销售额')), 'MANUAL'),
  (34, 3, 'brand',        '品牌',    '商品品牌', 'string', 'DIMENSION', NULL, 0, 0, 1, 'smoke_db', 't_product', 'brand', 'VARCHAR',
   JSON_ARRAY('eq','in'), NULL, JSON_OBJECT('synonyms',JSON_ARRAY('品牌'),'examples',JSON_ARRAY('某品牌销售额')), 'MANUAL');

-- ---------- 4. 关系（join 映射，INFERRED 需人工确认——此处全部 MANUAL 生效） ----------
INSERT INTO yak_onto_relation
  (id, source_object_id, target_object_id, name, cardinality, join_type, join_conditions,
   verbalizes, confidence, source)
VALUES
  (1, 1, 2, '销售单-客户', 'MANY_TO_ONE', 'DIRECT',
   JSON_ARRAY(JSON_OBJECT('left_attr_id',15,'right_attr_id',21,'op','eq')),
   JSON_ARRAY('{销售单} 属于 {客户}','{客户} 拥有 {销售单}'), 1.0000, 'MANUAL'),
  (2, 1, 3, '销售单-商品', 'MANY_TO_ONE', 'DIRECT',
   JSON_ARRAY(JSON_OBJECT('left_attr_id',16,'right_attr_id',31,'op','eq')),
   JSON_ARRAY('{销售单} 关联 {商品}','{商品} 出现在 {销售单}'), 1.0000, 'MANUAL');

-- ---------- 5. 指标（≥5 原子指标，全部 PUBLISHED；base_attribute 指向上面的金额/数量/ID） ----------
INSERT INTO yak_onto_metric
  (id, code, name, description, metric_type, object_id, base_attribute_id, aggregation,
   expression_json, business_filter_json, time_grain, unit, verbalizes, version, status,
   owner, approved_by)
VALUES
  (1, 'order_gmv', '销售额(GMV)', '已支付订单的实付金额合计', 'ATOMIC', 1, 18, 'SUM',
   NULL, JSON_ARRAY(JSON_OBJECT('attr','order_status','op','eq','value','PAID')), 'DAY', '元',
   JSON_ARRAY('{对象} 的销售额','按{time_grain}统计的{name}'), 1, 'PUBLISHED', '订单组', '口径负责人'),
  (2, 'order_cnt', '订单量', '已支付订单笔数', 'ATOMIC', 1, 11, 'COUNT',
   NULL, JSON_ARRAY(JSON_OBJECT('attr','order_status','op','eq','value','PAID')), 'DAY', '单',
   JSON_ARRAY('{对象} 的订单量','{name}'), 1, 'PUBLISHED', '订单组', '口径负责人'),
  (3, 'customer_cnt', '客户数', '已支付订单的去重客户数', 'ATOMIC', 1, 15, 'COUNT_DISTINCT',
   NULL, JSON_ARRAY(JSON_OBJECT('attr','order_status','op','eq','value','PAID')), 'DAY', '人',
   JSON_ARRAY('{对象} 的客户数','去重{name}'), 1, 'PUBLISHED', '客户组', '口径负责人'),
  (4, 'avg_order_value', '客单价', '每单平均实付金额', 'ATOMIC', 1, 18, 'AVG',
   NULL, JSON_ARRAY(JSON_OBJECT('attr','order_status','op','eq','value','PAID')), 'DAY', '元',
   JSON_ARRAY('{对象} 的客单价','{name}'), 1, 'PUBLISHED', '订单组', '口径负责人'),
  (5, 'total_quantity', '销量', '已支付订单的商品总件数', 'ATOMIC', 1, 17, 'SUM',
   NULL, JSON_ARRAY(JSON_OBJECT('attr','order_status','op','eq','value','PAID')), 'DAY', '件',
   JSON_ARRAY('{对象} 的销量','{name}'), 1, 'PUBLISHED', '订单组', '口径负责人');

-- ---------- 6. 术语表（≥10 条，ai_context 三段结构直接供语义检索/Agent prompt） ----------
INSERT INTO yak_onto_glossary (id, term, target_type, target_code, domain_id, ai_context)
VALUES
  (1,  '销售额', 'METRIC', 'order_gmv', 1, JSON_OBJECT('synonyms',JSON_ARRAY('GMV','成交额','营业额'),'examples',JSON_ARRAY('本月销售额'))),
  (2,  '成交额', 'METRIC', 'order_gmv', 1, JSON_OBJECT('synonyms',JSON_ARRAY('GMV','销售额'),'examples',JSON_ARRAY('成交额是多少'))),
  (3,  '客单价', 'METRIC', 'avg_order_value', 1, JSON_OBJECT('instructions','每单平均实付金额=销售额/订单量','synonyms',JSON_ARRAY('平均客单价'),'examples',JSON_ARRAY('客单价趋势'))),
  (4,  '订单量', 'METRIC', 'order_cnt', 1, JSON_OBJECT('synonyms',JSON_ARRAY('订单数','订单笔数'),'examples',JSON_ARRAY('每天订单量'))),
  (5,  '客户数', 'METRIC', 'customer_cnt', 1, JSON_OBJECT('synonyms',JSON_ARRAY('客户量'),'examples',JSON_ARRAY('付费客户数'))),
  (6,  '销量',   'METRIC', 'total_quantity', 1, JSON_OBJECT('synonyms',JSON_ARRAY('销售量','件数'),'examples',JSON_ARRAY('各品类销量'))),
  (7,  '销售单', 'OBJECT', 'sales_order', 1, JSON_OBJECT('synonyms',JSON_ARRAY('订单','销售订单'),'examples',JSON_ARRAY('销售单明细'))),
  (8,  '订单',   'OBJECT', 'sales_order', 1, JSON_OBJECT('synonyms',JSON_ARRAY('销售单'),'examples',JSON_ARRAY('订单列表'))),
  (9,  '客户',   'OBJECT', 'customer', 1, JSON_OBJECT('synonyms',JSON_ARRAY('买家'),'examples',JSON_ARRAY('客户贡献 TOP10'))),
  (10, '商品',   'OBJECT', 'product', 1, JSON_OBJECT('synonyms',JSON_ARRAY('产品','货品'),'examples',JSON_ARRAY('商品销量排行'))),
  (11, '已支付', 'ATTRIBUTE', 'order_status', 1, JSON_OBJECT('instructions','口径：统计销售额时默认仅含 PAID','synonyms',JSON_ARRAY('已付款','PAID'),'examples',JSON_ARRAY('只看已支付订单'))),
  (12, '品类',   'ATTRIBUTE', 'category', 1, JSON_OBJECT('synonyms',JSON_ARRAY('类目'),'examples',JSON_ARRAY('按品类拆分')));

-- =====================================================================
-- 校验提示：灌库后执行
--   SELECT (SELECT COUNT(*) FROM yak_onto_domain) d,(SELECT COUNT(*) FROM yak_onto_object) o,
--          (SELECT COUNT(*) FROM yak_onto_attribute) a,(SELECT COUNT(*) FROM yak_onto_relation) r,
--          (SELECT COUNT(*) FROM yak_onto_metric) m,(SELECT COUNT(*) FROM yak_onto_glossary) g;
-- 期望：1 / 3 / 17 / 2 / 5 / 12，全部 PUBLISHED。
-- =====================================================================