CREATE TABLE store (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL,
  region VARCHAR(40), nx INT, ny INT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);

CREATE TABLE item_master (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(80) NOT NULL,
  category VARCHAR(12) NOT NULL CHECK (category IN ('완제품','원재료','판매음료','소모품')),
  waste_target BOOLEAN NOT NULL DEFAULT TRUE, unit VARCHAR(12) NOT NULL,
  shelf_life_days INT, storage_condition VARCHAR(6) CHECK (storage_condition IN ('상온','냉장','냉동')),
  kg_per_unit NUMERIC(10,4), ef_prod NUMERIC(8,3), ef_waste NUMERIC(8,3),
  purchase_price NUMERIC(12,2), price_unit VARCHAR(12), note TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);

CREATE TABLE order_policy (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, store_id BIGINT NOT NULL REFERENCES store(id),
  item_id BIGINT NOT NULL REFERENCES item_master(id), item_name VARCHAR(80),
  category VARCHAR(12), order_method VARCHAR(16), order_cycle_days INT NOT NULL,
  lead_time_days INT NOT NULL, safety_z NUMERIC(5,2), order_lot_unit NUMERIC(12,3) DEFAULT 1, note TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (store_id, item_id));

CREATE TABLE sales_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, store_id BIGINT NOT NULL REFERENCES store(id),
  business_date DATE NOT NULL, day_of_week VARCHAR(3), weather VARCHAR(4) CHECK (weather IN ('맑음','흐림','비')),
  avg_temp NUMERIC(4,1), precipitation_mm NUMERIC(6,2), event VARCHAR(40), new_menu VARCHAR(40),
  category VARCHAR(12), item_id BIGINT NOT NULL REFERENCES item_master(id),
  quantity_sold NUMERIC(12,3) NOT NULL, scenario_note TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (store_id, item_id, business_date));
CREATE INDEX ix_sales_store_date ON sales_record(store_id, business_date);

CREATE TABLE inventory_snapshot (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, store_id BIGINT NOT NULL REFERENCES store(id),
  business_date DATE NOT NULL, day_of_week VARCHAR(3),
  item_id BIGINT NOT NULL REFERENCES item_master(id), category VARCHAR(12), unit VARCHAR(12),
  ordered_qty NUMERIC(12,3) DEFAULT 0, opening_stock NUMERIC(12,3), demand NUMERIC(12,3),
  actual_sales NUMERIC(12,3), stockout NUMERIC(12,3), waste_qty NUMERIC(12,3), closing_stock NUMERIC(12,3),
  waste_kg NUMERIC(12,3), waste_carbon_kg NUMERIC(14,4), waste_cost_krw NUMERIC(14,2), last_order_date DATE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (store_id, item_id, business_date));
CREATE INDEX ix_inv_store_item_date ON inventory_snapshot(store_id, item_id, business_date);

CREATE TABLE weather_forecast (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, region VARCHAR(40), forecast_date DATE NOT NULL,
  temp_max NUMERIC(4,1), temp_min NUMERIC(4,1), avg_temp NUMERIC(4,1),
  precipitation_mm NUMERIC(6,2), precipitation_prob INT, sky_code INT, fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (region, forecast_date, fetched_at));

CREATE TABLE demand_forecast (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, store_id BIGINT NOT NULL REFERENCES store(id),
  item_id BIGINT NOT NULL REFERENCES item_master(id), target_date DATE NOT NULL,
  predicted_quantity NUMERIC(12,3), p10 NUMERIC(12,3), p50 NUMERIC(12,3), p90 NUMERIC(12,3),
  model_version VARCHAR(40), features JSON,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (store_id, item_id, target_date));

CREATE TABLE order_recommendation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, store_id BIGINT NOT NULL REFERENCES store(id),
  item_id BIGINT NOT NULL REFERENCES item_master(id), target_date DATE NOT NULL,
  recommended_quantity NUMERIC(12,3), actual_quantity NUMERIC(12,3), actual_updated_at TIMESTAMP NULL,
  optimal_stock_quantity NUMERIC(12,3), baseline_quantity NUMERIC(12,3),
  critical_ratio NUMERIC(6,4), expected_waste_avoided_kg NUMERIC(12,3), rationale JSON,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (store_id, item_id, target_date));

CREATE TABLE carbon_saving (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, store_id BIGINT NOT NULL REFERENCES store(id),
  item_id BIGINT NOT NULL REFERENCES item_master(id), target_date DATE NOT NULL,
  waste_avoided_kg NUMERIC(12,3), guaranteed_saving_kg NUMERIC(12,3), potential_saving_kg NUMERIC(12,3),
  ef_prod_snapshot NUMERIC(8,3), ef_waste_snapshot NUMERIC(8,3),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (store_id, item_id, target_date));
