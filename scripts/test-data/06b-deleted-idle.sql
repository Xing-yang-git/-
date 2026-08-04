-- 06b -- 补充 deleted 闲置物品 (+5)
DO $$
DECLARE
  xi BIGINT:=2; lan BIGINT:=3; fei BIGINT:=4; nuan BIGINT; mei BIGINT;
  now_ts TIMESTAMP:=NOW(); i INT;
BEGIN
  SELECT id INTO nuan FROM users WHERE name='暖羊羊';
  SELECT id INTO mei  FROM users WHERE name='美羊羊';
  FOR i IN 1..5 LOOP
    INSERT INTO idle_items (user_id, tenant_id, post_type, title, description, category, condition, price, max_duration, duration_unit, status, delist_reason, created_at, updated_at)
    VALUES (
      CASE i WHEN 1 THEN xi WHEN 2 THEN lan WHEN 3 THEN fei WHEN 4 THEN nuan WHEN 5 THEN mei END,
      1, 'LEND',
      CASE i WHEN 1 THEN '过期食品代购' WHEN 2 THEN '违规刀具出售' WHEN 3 THEN '无证药品出售' WHEN 4 THEN '虚假租房信息' WHEN 5 THEN '仿冒化妆品' END,
      '违规内容', '其他', 'normal', 0, 1, 'day',
      'offline', '平台不允许发布此类内容',
      now_ts - (i || ' days')::INTERVAL, now_ts);
    INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at)
    VALUES (CASE i WHEN 1 THEN xi WHEN 2 THEN lan WHEN 3 THEN fei WHEN 4 THEN nuan WHEN 5 THEN mei END,
      'violation', '物品被下架', '您的物品因违规被下架',
      currval(pg_get_serial_sequence('idle_items', 'id')), FALSE, now_ts);
  END LOOP;
END;
$$;
