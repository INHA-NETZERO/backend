INSERT INTO item_master (name, category, waste_target, unit, shelf_life_days, storage_condition, kg_per_unit, ef_prod, ef_waste, purchase_price, price_unit, note) VALUES
  ('우유',     '원재료',  true,  'L',  7,    '냉장', 1.0300, 3.000, 0.200, 1000.00, '원/L',   'EF: OWID/Poore2018 우유≈3'),
  ('치즈',     '원재료',  true,  'kg', 30,   '냉장', 1.0000, 21.00, 0.200, 9000.00, '원/kg',  '치즈≈21 kgCO2e/kg'),
  ('원두',     '원재료',  true,  'kg', 180,  '상온', 1.0000, 17.00, 0.200, 25000.00,'원/kg',  '커피 원두 EF≈17'),
  ('베이커리', '완제품',  true,  'ea', 2,    '상온', 0.0800, 1.100, 0.200, 1500.00, '원/ea',  '빵류 단품 평균'),
  ('아메리카노','판매음료',false, 'ea', 1,    '상온', 0.0000, 0.000, 0.000,  500.00, '원/ea',  '소모 원두 별도 계산'),
  ('컵',       '소모품',  false, 'ea', 3650, '상온', 0.0000, 0.000, 0.000,   40.00, '원/ea',  '일회용 컵, 폐기 대상 아님');
