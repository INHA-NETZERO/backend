INSERT INTO order_policy (store_id, item_id, item_name, category, order_method, order_cycle_days, lead_time_days, safety_z, order_lot_unit, note)
SELECT
  1,
  im.id,
  im.name,
  im.category,
  '정기발주',
  CASE WHEN im.name = '원두' THEN 14 ELSE 7 END,
  1,
  1.00,
  CASE WHEN im.unit = 'ea' THEN 1.000 ELSE 2.000 END,
  NULL
FROM item_master im
WHERE im.category <> '소모품';
