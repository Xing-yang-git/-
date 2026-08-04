-- 06c -- 补充 deleted 求助 (+6)
DO $$
DECLARE
  xi BIGINT:=2; lan BIGINT:=3; fei BIGINT:=4; nuan BIGINT; mei BIGINT;
  now_ts TIMESTAMP:=NOW(); i INT;
BEGIN
  SELECT id INTO nuan FROM users WHERE name='暖羊羊';
  SELECT id INTO mei  FROM users WHERE name='美羊羊';
  FOR i IN 1..6 LOOP
    INSERT INTO help_requests (user_id, tenant_id, title, description, category, is_urgent, status, delist_reason, created_at, updated_at)
    VALUES (
      CASE i WHEN 1 THEN xi WHEN 2 THEN lan WHEN 3 THEN fei WHEN 4 THEN nuan WHEN 5 THEN mei WHEN 6 THEN xi END,
      1, CASE i WHEN 1 THEN '代做作业' WHEN 2 THEN '要求免费送物品' WHEN 3 THEN '恶意刷屏求助' WHEN 4 THEN '虚假募捐' WHEN 5 THEN '人肉搜索求助' WHEN 6 THEN '代吵架求助' END,
      '违规求助', '其他', FALSE, 'offline', '平台禁止此类求助',
      now_ts - (i || ' days')::INTERVAL, now_ts);
    INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at)
    VALUES (CASE i WHEN 1 THEN xi WHEN 2 THEN lan WHEN 3 THEN fei WHEN 4 THEN nuan WHEN 5 THEN mei WHEN 6 THEN xi END,
      'violation', '求助被下架', '您的求助因违规被下架',
      currval(pg_get_serial_sequence('help_requests', 'id')), FALSE, now_ts);
  END LOOP;
END;
$$;
