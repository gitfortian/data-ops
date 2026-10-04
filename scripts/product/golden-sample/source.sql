-- Golden Sample v1: isolated synthetic source data, never platform business tables.
-- INSERT IGNORE preserves later corrections and repeated bootstrap identities.
CREATE DATABASE IF NOT EXISTS yak_golden_sample CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS yak_golden_sample.golden_orders (
  order_id BIGINT PRIMARY KEY,
  customer_code VARCHAR(32) NOT NULL,
  quantity INT NULL,
  amount DECIMAL(12,2) NOT NULL,
  order_date DATE NOT NULL
) COMMENT='yak-golden-sample-v1';
CREATE TABLE IF NOT EXISTS yak_golden_sample.golden_orders_bad (
  order_id BIGINT PRIMARY KEY,
  customer_code VARCHAR(32) NOT NULL,
  quantity INT NULL,
  amount DECIMAL(12,2) NOT NULL,
  order_date DATE NOT NULL
) COMMENT='yak-golden-sample-v1';
CREATE TABLE IF NOT EXISTS yak_golden_sample.golden_customers (
  customer_code VARCHAR(32) PRIMARY KEY,
  customer_name VARCHAR(64) NOT NULL,
  phone VARCHAR(32) NOT NULL COMMENT 'Synthetic sensitive-field candidate, not a classification result',
  source_note VARCHAR(128) NOT NULL
) COMMENT='yak-golden-sample-v1';
INSERT IGNORE INTO yak_golden_sample.golden_orders VALUES
  (1001,'C001',2,240.00,'2026-10-01'),
  (1002,'C002',1,125.00,'2026-10-02'),
  (1003,'C003',3,360.00,'2026-10-03');
INSERT IGNORE INTO yak_golden_sample.golden_orders_bad VALUES
  (2001,'C001',2,240.00,'2026-10-01'),
  (2002,'C002',NULL,125.00,'2026-10-02'),
  (2003,'C003',3,360.00,'2026-10-03');
INSERT IGNORE INTO yak_golden_sample.golden_customers VALUES
  ('C001','示例客户甲','SYNTHETIC-0001','Golden Sample synthetic data'),
  ('C002','示例客户乙','SYNTHETIC-0002','Golden Sample synthetic data'),
  ('C003','示例客户丙','SYNTHETIC-0003','Golden Sample synthetic data');
