# -*- coding: utf-8 -*-
"""
数据治理语义层测试数据集 —— 造数脚本（多库版）
覆盖 9 库 15 表，时间 2024-01-01 ~ 2026-12-31（36 个月）
含 10 类治理点 + 跨表一致性 + 记录级质量 + 跨表标准不一致 + SCD
覆盖 9 库 15 表、约 300 万条数据、30+ 治理点，可直接用于语义层建设全链路测试。
依赖：pip install pymysql faker
用法：python 02_gen_data.py
"""

import random
import pymysql
from datetime import datetime, timedelta
from faker import Faker

# ============================================================
# 配置
# ============================================================
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'charset': 'utf8mb4',
}

START_DATE = datetime(2024, 1, 1)
END_DATE = datetime(2026, 12, 31)
CUSTOMER_COUNT = 3000
ITEM_COUNT = 100
ACTIVITY_COUNT = 100
PROMO_MONTHS = {3, 6, 8, 11, 12}

fake = Faker('zh_CN')
random.seed(42)
Faker.seed(42)

# 全局连接（每库一个连接）
conns = {}


def get_conn(db):
    """按库获取连接"""
    if db not in conns:
        cfg = dict(DB_CONFIG)
        cfg['database'] = db
        conns[db] = pymysql.connect(**cfg)
    return conns[db]


def cursor_of(db):
    return get_conn(db).cursor()


def commit(db):
    get_conn(db).commit()


# ============================================================
# 工具函数
# ============================================================
def dirty_gender():
    return random.choice(['M', 'F', '1', '2', '男', '女'])


def dirty_time(dt):
    if dt is None:
        return None
    if random.random() < 0.1:
        return str(int(dt.timestamp()))
    fmt = random.choice(['%Y-%m-%d %H:%M:%S', '%Y-%m-%d', '%Y%m%d'])
    return dt.strftime(fmt)


def dirty_time_year_aware(dt):
    if dt is None:
        return None
    if dt.year == 2024:
        return dt.strftime('%Y%m%d')
    return dirty_time(dt)


def dirty_mobile():
    if random.random() < 0.15:
        return None
    style = random.choice(['plain', 'dash', 'intl'])
    num = random.randint(100000000, 999999999)
    if style == 'plain':
        return f'13{num}'
    if style == 'dash':
        return f'13{random.randint(0,9)}-{random.randint(0,9999):04d}-{random.randint(0,9999):04d}'
    return f'+8613{num}'


def dirty_amount(amount):
    style = random.choice(['yuan', 'cent', 'wanyuan', 'str'])
    if style == 'cent':
        return str(int(amount * 100))
    if style == 'wanyuan':
        return str(round(amount / 10000, 4))
    if style == 'str':
        return f'{amount:.2f}'
    return str(amount)


def dirty_order_status(dt):
    if dt.year == 2024:
        return random.choice(['1', '2', '3', '4', '5'])
    return random.choice(['1', '2', '3', '4', '5', 'PAID', 'SHIPPED', '已支付', '已发货', '已完成', '已取消'])


def dirty_pay_channel():
    return random.choice(['alipay', '1', '支付宝', 'wechat', '2', '微信', 'bank', '3'])


def dirty_pay_status():
    return random.choice(['0', '1', 'SUCCESS', '成功', 'FAILED'])


def dirty_channel():
    return random.choice(['APP', '1', 'web', '小程序', 'wechat'])


def dirty_idcard():
    if random.random() < 0.15:
        return None
    return f'110101{random.randint(1970, 2005)}{random.randint(1,12):02d}{random.randint(1,28):02d}{random.randint(1000,9999)}'


def dirty_birthday():
    dt = datetime(random.randint(1970, 2005), random.randint(1, 12), random.randint(1, 28))
    return dirty_time(dt)


def dirty_item_id(item_id):
    if random.random() < 0.01:
        return f'ITEM-{item_id[1:]}'
    return item_id


def dirty_etl_time(dt):
    if random.random() < 0.1:
        return dt + timedelta(hours=random.randint(25, 72))
    return dt + timedelta(minutes=random.randint(1, 30))


def parse_dirty_time(s):
    if s is None:
        return None
    s = str(s).strip()
    if s.isdigit() and len(s) >= 10:
        try:
            return datetime.fromtimestamp(int(s))
        except Exception:
            pass
    for fmt in ('%Y-%m-%d %H:%M:%S', '%Y-%m-%d', '%Y%m%d'):
        try:
            return datetime.strptime(s, fmt)
        except Exception:
            continue
    return None


# ============================================================
# 1. 商品（item_db）
# ============================================================
def gen_items():
    print('生成商品（item_db）...')
    cur = cursor_of('item_db')
    categories = ['手机', '电脑', '家电', '服饰', '食品', '美妆', '图书', '运动', '母婴', '家居']
    brands = ['Apple', 'Huawei', 'Xiaomi', 'Lenovo', 'Dell', 'Samsung', 'Sony', 'Nike', 'Adidas', 'Other']
    rows = []
    for i in range(1, ITEM_COUNT + 1):
        cat = random.choice(categories)
        brand = random.choice(brands)
        price = round(random.uniform(10, 9999), 2)
        rows.append((
            f'I{i:06d}', f'{brand} {cat} 商品{i}',
            f'CAT{random.randint(1,20):03d}', cat, brand,
            str(price), str(round(price * 0.7, 2)),
            random.choice(['0', '1', 'on', 'off']),
            datetime.now(),
        ))
    cur.executemany('INSERT INTO item_item VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)', rows)
    commit('item_db')
    print(f'  商品 {len(rows)} 条')


# ============================================================
# 2. 活动（mkt_db）
# ============================================================
def gen_activities():
    print('生成活动（mkt_db）...')
    cur = cursor_of('mkt_db')
    names = ['年货节', '春节活动', '女神节', '618大促', '88节', '99划算节', '双11', '双12']
    rows = []
    for i in range(1, ACTIVITY_COUNT + 1):
        if random.random() < 0.5:
            year = random.choice([2024, 2025, 2026])
            month = random.choice(list(PROMO_MONTHS))
            start = datetime(year, month, random.randint(1, 5))
        else:
            start = START_DATE + timedelta(days=random.randint(0, 1095))
        end = start + timedelta(days=random.randint(3, 30))
        rows.append((
            f'A{i:05d}', f'{random.choice(names)}-{i}',
            random.choice(['1', '2', '3', '满减', '折扣']),
            start.strftime('%Y-%m-%d %H:%M:%S'),
            end.strftime('%Y-%m-%d %H:%M:%S'),
            random.choice(['0', '1', '2', 'ongoing']),
            str(random.randint(100000, 10000000)),
            datetime.now(),
        ))
    cur.executemany('INSERT INTO mkt_activity VALUES (%s,%s,%s,%s,%s,%s,%s,%s)', rows)
    commit('mkt_db')
    print(f'  活动 {len(rows)} 条')


# ============================================================
# 3. 客户 + 地址（crm_db）
# ============================================================
def gen_customers():
    print('生成客户（crm_db）...')
    cur = cursor_of('crm_db')
    provinces = ['北京', '上海', '广东', '浙江', '江苏', '四川', '湖北', '山东', '福建', '河南']
    rows = []
    mobile_pool = []
    for i in range(1, CUSTOMER_COUNT + 1):
        province = random.choice(provinces)
        reg_dt = datetime(2023, 1, 1) + timedelta(days=random.randint(0, 1095))
        mobile = dirty_mobile()
        if mobile:
            mobile_pool.append(mobile)
        rows.append((
            f'C{i:06d}', f'客户{i}', mobile, dirty_idcard(),
            dirty_gender(), dirty_birthday(), dirty_time(reg_dt),
            random.choice(['APP', '1', 'wechat', '小程序', 'web']),
            random.choice(['1', '2', '3', 'VIP', 'GOLD', 'PLATINUM']),
            f'{province}市', province,
            random.choice(['0', '1', 'active', '正常']),
            datetime.now(),
        ))
    # 2% 重复客户
    for i in range(int(CUSTOMER_COUNT * 0.02)):
        if not mobile_pool:
            break
        province = random.choice(provinces)
        rows.append((
            f'C{CUSTOMER_COUNT + i + 1:06d}', f'重复客户{i}',
            random.choice(mobile_pool), dirty_idcard(),
            dirty_gender(), dirty_birthday(),
            dirty_time(datetime(2023, 1, 1) + timedelta(days=random.randint(0, 1095))),
            random.choice(['APP', '1', 'wechat', '小程序', 'web']),
            random.choice(['1', '2', '3', 'VIP', 'GOLD', 'PLATINUM']),
            f'{province}市', province,
            random.choice(['0', '1', 'active', '正常']),
            datetime.now(),
        ))
    cur.executemany('INSERT INTO crm_customer VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)', rows)
    commit('crm_db')
    print(f'  客户 {len(rows)} 条（含 2% 重复）')

    # 地址
    print('生成客户地址（crm_db）...')
    addr_rows = []
    addr_id = 1
    total_cust = CUSTOMER_COUNT + int(CUSTOMER_COUNT * 0.02)
    for i in range(1, total_cust + 1):
        for _ in range(random.randint(1, 2)):
            addr_rows.append((
                f'ADDR{addr_id:06d}', f'C{i:06d}',
                f'收货人{addr_id}',
                f'13{random.randint(100000000, 999999999)}',
                f'{random.choice(provinces)}市某区某路{random.randint(1,999)}号',
                random.choice(['0', '1', 'Y', 'N']),
            ))
            addr_id += 1
            if addr_id > 18000:
                break
        if addr_id > 18000:
            break
    cur.executemany('INSERT INTO crm_customer_address VALUES (%s,%s,%s,%s,%s,%s)', addr_rows)
    commit('crm_db')
    print(f'  地址 {len(addr_rows)} 条')


# ============================================================
# 4. 客户历史维表 SCD Type 2（crm_db）
# ============================================================
def gen_customer_history():
    print('生成客户历史维表（crm_db，SCD Type 2）...')
    cur = cursor_of('crm_db')
    cur.execute('SELECT cust_id, cust_name, city, province, member_level, status, reg_time FROM crm_customer')
    customers = cur.fetchall()

    rows = []
    history_id = 1

    for c in customers:
        cust_id, name, city, province, member_level, status, reg_str = c
        reg_dt = parse_dirty_time(reg_str) or datetime(2023, 1, 1)

        v1_start = reg_dt
        v1_end = None
        v1_is_current = 1

        # 5% 城市变更
        if random.random() < 0.05:
            change_dt = v1_start + timedelta(days=random.randint(180, 720))
            if change_dt > datetime(2026, 12, 31):
                change_dt = datetime(2026, 12, 31)
            new_city = random.choice(['北京', '上海', '广州', '深圳', '杭州', '成都'])
            new_province = {'北京': '北京', '上海': '上海', '广州': '广东',
                            '深圳': '广东', '杭州': '浙江', '成都': '四川'}[new_city]
            v1_end = change_dt
            v1_is_current = 0

            v2_start = change_dt
            v2_end = None
            v2_is_current = 1

            # 2% 二次变更
            if random.random() < 0.02:
                change_dt2 = v2_start + timedelta(days=random.randint(180, 540))
                if change_dt2 > datetime(2026, 12, 31):
                    change_dt2 = datetime(2026, 12, 31)
                new_city2 = random.choice(['北京', '上海', '广州', '深圳', '杭州', '成都'])
                new_province2 = {'北京': '北京', '上海': '上海', '广州': '广东',
                                 '深圳': '广东', '杭州': '浙江', '成都': '四川'}[new_city2]
                v2_end = change_dt2
                v2_is_current = 0

                v3_start = change_dt2
                v3_end = None
                v3_is_current = 1
                # 1% 时间倒挂
                if random.random() < 0.01:
                    v3_start = v3_start - timedelta(days=random.randint(10, 100))
                    v3_end = v3_start - timedelta(days=random.randint(1, 10))

                rows.append((f'CH{history_id:010d}', cust_id, 3, name,
                             new_city2, new_province2, member_level, status,
                             v3_start, v3_end, v3_is_current, '再次搬迁', datetime.now()))
                history_id += 1

            rows.append((f'CH{history_id:010d}', cust_id, 2, name,
                         new_city, new_province, member_level, status,
                         v2_start, v2_end, v2_is_current, '搬迁', datetime.now()))
            history_id += 1

        rows.append((f'CH{history_id:010d}', cust_id, 1, name,
                     city, province, member_level, status,
                     v1_start, v1_end, v1_is_current, '注册', datetime.now()))
        history_id += 1

        if len(rows) >= 5000:
            cur.executemany(
                'INSERT INTO crm_customer_history VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)', rows)
            commit('crm_db')
            rows = []

    if rows:
        cur.executemany(
            'INSERT INTO crm_customer_history VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)', rows)
        commit('crm_db')

    # 注入治理点：版本不唯一 / 不连续 / 重叠
    cur.execute('SELECT history_id, cust_id FROM crm_customer_history ORDER BY RAND() LIMIT 100')
    samples = cur.fetchall()
    for hid, cid in samples:
        r = random.random()
        if r < 0.25:
            cur.execute('UPDATE crm_customer_history SET is_current=1 WHERE cust_id=%s AND version=1', (cid,))
        elif r < 0.5:
            cur.execute('UPDATE crm_customer_history SET end_time=DATE_SUB(end_time, INTERVAL 10 DAY) '
                        'WHERE cust_id=%s AND version=1 AND end_time IS NOT NULL', (cid,))
        elif r < 0.75:
            cur.execute('UPDATE crm_customer_history SET start_time=DATE_SUB(start_time, INTERVAL 10 DAY) '
                        'WHERE cust_id=%s AND version=2', (cid,))
    commit('crm_db')
    print(f'  客户历史 {history_id - 1} 条（含治理点）')


# ============================================================
# 5. 库存快照（wms_db，按月）
# ============================================================
def gen_inventory():
    print('生成库存快照（wms_db，按月）...')
    cur_item = cursor_of('item_db')
    cur_item.execute('SELECT item_id FROM item_item')
    items = [r[0] for r in cur_item.fetchall()]

    cur = cursor_of('wms_db')
    warehouses = ['WH001', 'WH002', 'WH003', 'WH004', 'WH005']
    rows = []
    inv_id = 1
    d = datetime(2024, 1, 1)
    while d <= datetime(2026, 12, 1):
        for item in items:
            for wh in random.sample(warehouses, random.randint(1, 3)):
                stock = random.randint(0, 1000)
                lock = random.randint(0, int(stock * 0.3))
                available = stock - lock
                if random.random() < 0.05:
                    available += random.randint(-50, 50)
                if random.random() < 0.02:
                    stock = -random.randint(1, 100)
                rows.append((f'INV{inv_id:08d}', item, wh, stock, lock, available,
                             d.strftime('%Y-%m-%d %H:%M:%S'),
                             d + timedelta(hours=random.randint(1, 6))))
                inv_id += 1
        cur.executemany('INSERT INTO wms_inventory VALUES (%s,%s,%s,%s,%s,%s,%s,%s)', rows)
        commit('wms_db')
        rows = []
        d = (d + timedelta(days=31)).replace(day=1)
    print(f'  库存快照 {inv_id - 1} 条（36 个月）')


# ============================================================
# 6. 订单 + 明细 + 支付 + 退款 + 物流 + 库存流水 + 售后 + 结算 + 评价 + 优惠券
# ============================================================
def gen_orders():
    print('生成订单链路（跨 7 库）...')

    cur_item = cursor_of('item_db')
    cur_item.execute('SELECT item_id, item_name, price FROM item_item')
    items = cur_item.fetchall()

    cur_crm = cursor_of('crm_db')
    cur_crm.execute('SELECT cust_id FROM crm_customer')
    customers = [r[0] for r in cur_crm.fetchall()]
    cur_crm.execute('SELECT cust_id, city, start_time, end_time FROM crm_customer_history ORDER BY cust_id, version')
    history_map = {}
    for h in cur_crm.fetchall():
        history_map.setdefault(h[0], []).append({'city': h[1], 'start': h[2], 'end': h[3]})

    cur_mkt = cursor_of('mkt_db')
    cur_mkt.execute('SELECT activity_id FROM mkt_activity')
    activities = [r[0] for r in cur_mkt.fetchall()]

    order_rows, detail_rows, pay_rows, refund_rows, delivery_rows = [], [], [], [], []
    stock_log_rows, aftersale_rows, settle_rows, review_rows, coupon_rows = [], [], [], [], []
    order_seq = detail_seq = pay_seq = refund_seq = delivery_seq = 0
    stock_log_seq = aftersale_seq = settle_seq = review_seq = coupon_seq = 0

    cur_date = START_DATE
    while cur_date <= END_DATE:
        year, month = cur_date.year, cur_date.month
        if year == 2024:
            daily = random.randint(120, 150)
        elif year == 2025:
            daily = random.randint(180, 220)
        else:
            daily = random.randint(240, 290)
        if month in PROMO_MONTHS:
            daily *= 4

        for _ in range(daily):
            order_seq += 1
            cust_id = random.choice(customers)
            order_dt = cur_date + timedelta(hours=random.randint(0, 23),
                                            minutes=random.randint(0, 59),
                                            seconds=random.randint(0, 59))
            order_id = f'O{order_dt.strftime("%Y%m%d")}{order_seq:08d}'
            order_no = f'NO{order_dt.strftime("%Y%m%d")}{order_seq:08d}'

            # SCD：按订单时间取当时城市
            order_city = None
            for h in history_map.get(cust_id, []):
                if h['start'] and order_dt >= h['start'] and (h['end'] is None or order_dt < h['end']):
                    order_city = h['city']
                    break

            # 明细
            detail_count = random.randint(1, 4)
            total_amount = 0.0
            detail_items = []
            for _ in range(detail_count):
                detail_seq += 1
                item = random.choice(items)
                qty = random.randint(1, 3)
                if random.random() < 0.005:
                    qty = 9999
                price = float(item[2])
                amount = round(price * qty, 2)
                if random.random() < 0.005:
                    amount = -amount
                total_amount += amount
                real_item_id = dirty_item_id(item[0])
                detail_items.append((real_item_id, item[1], qty, price, amount))
                detail_rows.append((
                    f'D{detail_seq:010d}', order_id, real_item_id, item[1], qty,
                    dirty_amount(price), dirty_amount(amount),
                    dirty_amount(round(amount * random.uniform(0, 0.2), 2)),
                    dirty_etl_time(order_dt),
                ))
                # 1% 孤儿明细
                if random.random() < 0.01:
                    detail_rows[-1] = (detail_rows[-1][0],
                                       f'O_ORPHAN{random.randint(1, 99999999):08d}',
                                       *detail_rows[-1][2:])

            discount = round(total_amount * random.uniform(0, 0.3), 2)
            freight = random.choice([0, 0, 0, 8, 10, 12, 15])
            pay_amount = round(total_amount - discount + freight, 2)

            order_amount_field = total_amount
            if random.random() < 0.05:
                order_amount_field = round(total_amount * random.uniform(0.8, 1.2), 2)

            r = random.random()
            if r < 0.15:
                order_status = random.choice(['5', '已取消'])
                pay_dt = ship_dt = finish_dt = None
                cancel_dt = order_dt + timedelta(minutes=random.randint(5, 60))
                cancel_reason = random.choice(['用户取消', '超时未支付', '库存不足'])
            else:
                order_status = dirty_order_status(order_dt)
                pay_dt = order_dt + timedelta(minutes=random.randint(1, 30))
                ship_dt = pay_dt + timedelta(hours=random.randint(6, 48))
                finish_dt = ship_dt + timedelta(days=random.randint(1, 7))
                cancel_dt = cancel_reason = None

            discount_field = discount
            if random.random() < 0.05 and discount > 0:
                discount_field = 0

            order_rows.append((
                order_id, order_no, cust_id, order_city,
                dirty_time_year_aware(order_dt), order_status,
                dirty_amount(order_amount_field), dirty_amount(pay_amount),
                dirty_amount(discount_field), dirty_amount(freight),
                dirty_time(pay_dt) if pay_dt else None,
                dirty_time(ship_dt) if ship_dt else None,
                dirty_time(finish_dt) if finish_dt else None,
                dirty_time(cancel_dt) if cancel_dt else None,
                cancel_reason, dirty_channel(), dirty_etl_time(order_dt),
            ))

            # 1% 孤儿订单
            if random.random() < 0.01:
                order_rows[-1] = (order_rows[-1][0], order_rows[-1][1],
                                  f'C_ORPHAN{random.randint(1, 999999):06d}',
                                  *order_rows[-1][3:])

            # 0.5% 未来日期订单
            if random.random() < 0.005:
                future_dt = datetime(2027, random.randint(1, 6), random.randint(1, 28),
                                     random.randint(0, 23), random.randint(0, 59))
                order_rows[-1] = (order_rows[-1][0], order_rows[-1][1], order_rows[-1][2],
                                  order_rows[-1][3], dirty_time_year_aware(future_dt),
                                  *order_rows[-1][5:])

            # 支付
            if pay_dt:
                pay_seq += 1
                pay_id = f'P{pay_dt.strftime("%Y%m%d")}{pay_seq:08d}'
                real_pay = pay_amount
                if random.random() < 0.03:
                    real_pay = round(pay_amount * random.uniform(0.9, 1.1), 2)
                pay_rows.append((pay_id, order_id, cust_id, real_pay,
                                 dirty_pay_channel(), dirty_pay_status(),
                                 dirty_time(pay_dt), f'TRADE{pay_seq:012d}',
                                 dirty_etl_time(pay_dt)))

                # 退款
                if r > 0.9:
                    refund_seq += 1
                    refund_dt = pay_dt + timedelta(days=random.randint(1, 15))
                    refund_amt = round(real_pay * random.uniform(0.5, 1.0), 2)
                    if random.random() < 0.02:
                        refund_amt = round(real_pay * random.uniform(1.1, 1.5), 2)
                    refund_rows.append((
                        f'R{refund_seq:010d}', order_id, pay_id,
                        dirty_amount(refund_amt),
                        random.choice(['0', '1', '2', 'REFUNDED', '已退款']),
                        dirty_time(refund_dt),
                        random.choice(['质量问题', '7天无理由', '拍错/多拍', '缺货']),
                        dirty_etl_time(refund_dt),
                    ))

                    # 售后
                    if random.random() < 0.5:
                        aftersale_seq += 1
                        apply_t = refund_dt - timedelta(hours=random.randint(1, 24))
                        finish_t = refund_dt + timedelta(days=random.randint(1, 7))
                        # 10% 时间倒挂
                        if random.random() < 0.1:
                            finish_t = apply_t - timedelta(days=random.randint(1, 3))
                        aftersale_rows.append((
                            f'AS{aftersale_seq:010d}', order_id, cust_id,
                            random.choice(['RETURN', 'EXCHANGE', 'REPAIR', '退货', '换货', '维修']),
                            random.choice(['0', '1', '2', '3', 'PROCESSING', '处理中']),
                            f'R{refund_seq:010d}',
                            dirty_time(apply_t), dirty_time(finish_t),
                            random.choice(['质量问题', '7天无理由', '拍错/多拍']),
                            dirty_etl_time(refund_dt),
                        ))

                # 物流
                if ship_dt:
                    delivery_seq += 1
                    sign_dt = ship_dt + timedelta(days=random.randint(1, 7))
                    if random.random() < 0.01:
                        sign_dt = ship_dt - timedelta(hours=random.randint(1, 24))
                    delivery_rows.append((
                        f'DL{delivery_seq:010d}', order_id,
                        f'LOG{random.randint(1000000000, 9999999999)}',
                        random.choice(['SF', '1', '顺丰', 'YT', '2', '圆通', 'ZT', '3', '中通']),
                        random.choice(['0', '1', '2', '3', '已签收']),
                        dirty_time(ship_dt), dirty_time(sign_dt),
                        dirty_etl_time(ship_dt),
                    ))

                    # 库存流水
                    for it in detail_items:
                        stock_log_seq += 1
                        before = random.randint(0, 1000)
                        change = -it[2]
                        after = before + change
                        if random.random() < 0.01:
                            after = before + change + random.randint(-10, 10)
                        stock_log_rows.append((
                            f'SL{stock_log_seq:010d}', it[0], order_id,
                            random.choices(['OUT', 'LOCK'], weights=[0.9, 0.1])[0],
                            change, before, after,
                            dirty_time(order_dt), dirty_etl_time(order_dt),
                        ))

                    # 结算（金额自洽：settle = pay - commission，3% 偏差）
                    if random.random() < 0.7:
                        settle_seq += 1
                        commission = round(pay_amount * random.uniform(0.05, 0.15), 2)
                        settle_amount = round(pay_amount - commission, 2)
                        if random.random() < 0.03:
                            settle_amount = round(settle_amount * random.uniform(0.95, 1.05), 2)
                        settle_rows.append((
                            f'ST{settle_seq:010d}', order_id,
                            f'M{random.randint(1,100):05d}',
                            settle_amount, commission,
                            random.choice(['0', '1', '2', 'SETTLED']),
                            random.choice(['T+1', 'T+7', 'T+30']),
                            random.choice(['CNY', 'CNY', 'CNY', 'USD']),
                            dirty_time(finish_dt) if finish_dt else None,
                            dirty_etl_time(finish_dt) if finish_dt else datetime.now(),
                        ))

                    # 评价（2% 刷单 + 1% 早于签收）
                    if finish_dt and random.random() < 0.4:
                        review_seq += 1
                        review_order_id = order_id
                        if random.random() < 0.05:
                            review_order_id = f'O{order_dt.strftime("%Y%m%d")}{99999999:08d}'
                        review_t = finish_dt + timedelta(days=random.randint(1, 30))
                        if random.random() < 0.01 and sign_dt:
                            review_t = sign_dt - timedelta(days=random.randint(1, 5))
                        review_rows.append((
                            f'RV{review_seq:010d}', review_order_id,
                            detail_items[0][0], cust_id,
                            random.choice(['1', '2', '3', '4', '5', '一星', '二星', '三星', '四星', '五星']),
                            fake.text(max_nb_chars=100),
                            random.choice(['0', '1', 'Y', 'N']),
                            dirty_time(review_t), dirty_etl_time(finish_dt),
                        ))
                        # 2% 刷单：同客户多商品 5 星
                        if random.random() < 0.02:
                            for _ in range(random.randint(2, 5)):
                                review_seq += 1
                                review_rows.append((
                                    f'RV{review_seq:010d}', order_id,
                                    random.choice(items)[0], cust_id,
                                    '5',
                                    '好评！质量很好，物流很快，下次还来！',
                                    '1',
                                    dirty_time(finish_dt + timedelta(days=random.randint(1, 3))),
                                    dirty_etl_time(finish_dt),
                                ))

            # 优惠券（已使用必回填 order_id）
            if random.random() < 0.2 and pay_dt:
                coupon_seq += 1
                receive_dt = order_dt - timedelta(days=random.randint(1, 30))
                coupon_rows.append((
                    f'CP{coupon_seq:08d}', f'CPN{coupon_seq:012d}',
                    random.choice(activities), cust_id,
                    random.choice(['1', '2', '3', '满减', '折扣']),
                    dirty_amount(random.choice([5, 10, 20, 30, 50, 100])),
                    random.choice(['1', 'used', '已使用']),
                    dirty_time(receive_dt), dirty_time(order_dt), order_id,
                    dirty_etl_time(receive_dt),
                ))

        # 按日期 flush
        flush(order_rows, detail_rows, pay_rows, refund_rows, delivery_rows,
              stock_log_rows, aftersale_rows, settle_rows, review_rows, coupon_rows)
        order_rows, detail_rows, pay_rows, refund_rows, delivery_rows = [], [], [], [], []
        stock_log_rows, aftersale_rows, settle_rows, review_rows, coupon_rows = [], [], [], [], []

        if cur_date.day == 1:
            print(f'  已生成至 {cur_date.strftime("%Y-%m")}，累计订单 {order_seq}，明细 {detail_seq}')

        cur_date += timedelta(days=1)

    print(f'\n  订单 {order_seq}，明细 {detail_seq}，支付 {pay_seq}，退款 {refund_seq}，'
          f'物流 {delivery_seq}，库存流水 {stock_log_seq}，售后 {aftersale_seq}，'
          f'结算 {settle_seq}，评价 {review_seq}，优惠券 {coupon_seq}')


def flush(order_rows, detail_rows, pay_rows, refund_rows, delivery_rows,
          stock_log_rows, aftersale_rows, settle_rows, review_rows, coupon_rows):
    """按库写入，每库独立事务。任一库失败不影响其他库，但该库当日回滚。"""
    tasks = [
        ('trade_db', 'trade_order', 'INSERT INTO trade_order VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)', order_rows),
        ('trade_db', 'trade_order_detail', 'INSERT INTO trade_order_detail VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)', detail_rows),
        ('pay_db', 'pay_payment', 'INSERT INTO pay_payment VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)', pay_rows),
        ('pay_db', 'pay_refund', 'INSERT INTO pay_refund VALUES (%s,%s,%s,%s,%s,%s,%s,%s)', refund_rows),
        ('wms_db', 'wms_delivery', 'INSERT INTO wms_delivery VALUES (%s,%s,%s,%s,%s,%s,%s,%s)', delivery_rows),
        ('wms_db', 'wms_stock_log', 'INSERT INTO wms_stock_log VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)', stock_log_rows),
        ('aftersale_db', 'aftersale_ticket', 'INSERT INTO aftersale_ticket VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)', aftersale_rows),
        ('settle_db', 'set_settlement', 'INSERT INTO set_settlement VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)', settle_rows),
        ('review_db', 'review_product', 'INSERT INTO review_product VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)', review_rows),
        ('mkt_db', 'mkt_coupon', 'INSERT INTO mkt_coupon VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)', coupon_rows),
    ]
    # 按库分组，每库一次事务
    by_db = {}
    for db, name, sql, rows in tasks:
        if rows:
            by_db.setdefault(db, []).append((name, sql, rows))

    for db, items in by_db.items():
        cur = cursor_of(db)
        try:
            for name, sql, rows in items:
                cur.executemany(sql, rows)
            commit(db)
        except Exception as e:
            get_conn(db).rollback()
            print(f'  [警告] {db} 当日批量写入失败，已回滚：{e}')


# ============================================================
# 7. 未使用优惠券（mkt_db）
# ============================================================
def gen_unused_coupons():
    print('生成未使用优惠券（mkt_db）...')
    cur_mkt = cursor_of('mkt_db')
    cur_mkt.execute('SELECT activity_id FROM mkt_activity')
    activities = [r[0] for r in cur_mkt.fetchall()]
    cur_crm = cursor_of('crm_db')
    cur_crm.execute('SELECT cust_id FROM crm_customer')
    customers = [r[0] for r in cur_crm.fetchall()]

    rows = []
    for i in range(1, 50001):
        cust_id = random.choice(customers)
        receive_dt = START_DATE + timedelta(days=random.randint(0, 1095))
        rows.append((
            f'CPU{i:07d}', f'CPNU{i:012d}',
            random.choice(activities), cust_id,
            random.choice(['1', '2', '3', '满减', '折扣']),
            dirty_amount(random.choice([5, 10, 20, 30, 50, 100])),
            random.choice(['0', '2', '已过期']),
            dirty_time(receive_dt), None, None,
            dirty_etl_time(receive_dt),
        ))
        if len(rows) >= 5000:
            cur_mkt.executemany(
                'INSERT INTO mkt_coupon VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)', rows)
            commit('mkt_db')
            rows = []
    if rows:
        cur_mkt.executemany(
            'INSERT INTO mkt_coupon VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)', rows)
        commit('mkt_db')
    print('  未使用优惠券 50000 条')


# ============================================================
# 主流程
# ============================================================
if __name__ == '__main__':
    try:
        gen_items()
        gen_activities()
        gen_customers()
        gen_customer_history()
        gen_inventory()
        gen_orders()
        gen_unused_coupons()

        print('\n=== 数据统计 ===')
        stats = [
            ('crm_db', ['crm_customer', 'crm_customer_address', 'crm_customer_history']),
            ('item_db', ['item_item']),
            ('trade_db', ['trade_order', 'trade_order_detail']),
            ('pay_db', ['pay_payment', 'pay_refund']),
            ('mkt_db', ['mkt_activity', 'mkt_coupon']),
            ('wms_db', ['wms_delivery', 'wms_inventory', 'wms_stock_log']),
            ('aftersale_db', ['aftersale_ticket']),
            ('settle_db', ['set_settlement']),
            ('review_db', ['review_product']),
        ]
        for db, tables in stats:
            cur = cursor_of(db)
            print(f'\n  [{db}]')
            for t in tables:
                cur.execute(f'SELECT COUNT(*) FROM {t}')
                print(f'    {t}: {cur.fetchone()[0]}')
    finally:
        for c in conns.values():
            c.close()