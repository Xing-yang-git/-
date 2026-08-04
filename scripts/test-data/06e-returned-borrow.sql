-- 06e -- 补充 returned borrow (+7)
DO $$
DECLARE
  xi BIGINT:=2; lan BIGINT:=3; fei BIGINT:=4; nuan BIGINT; mei BIGINT;
  rid BIGINT; now_ts TIMESTAMP:=NOW(); i INT;
BEGIN
  SELECT id INTO nuan FROM users WHERE name='暖羊羊';
  SELECT id INTO mei  FROM users WHERE name='美羊羊';
  FOR i IN 1..7 LOOP
    INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, return_status, damage_type, is_on_time, created_at, updated_at)
    SELECT id, CASE i WHEN 1 THEN xi WHEN 2 THEN lan WHEN 3 THEN fei WHEN 4 THEN nuan WHEN 5 THEN mei WHEN 6 THEN xi WHEN 7 THEN lan END,
      'day', i, '测试数据-已完成', 'returned',
      ('2026-07-' || (10 + i))::DATE, now_ts - (i * 3 || ' days')::INTERVAL,
      CASE i%3 WHEN 0 THEN 'ontime' WHEN 1 THEN 'delayed' ELSE 'ontime' END,
      CASE i%3 WHEN 0 THEN 'none' WHEN 1 THEN 'slight' ELSE 'none' END,
      i%3 <> 1, now_ts - (i * 4 || ' days')::INTERVAL, now_ts - (i * 2 || ' days')::INTERVAL
    FROM idle_items WHERE user_id = CASE i WHEN 1 THEN lan WHEN 2 THEN fei WHEN 3 THEN xi WHEN 4 THEN lan WHEN 5 THEN fei WHEN 6 THEN nuan WHEN 7 THEN mei END
      AND status NOT IN ('deleted') LIMIT 1;
    rid := currval(pg_get_serial_sequence('borrow_requests', 'id'));
    INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at)
    VALUES (rid,
      CASE i WHEN 1 THEN xi WHEN 2 THEN lan WHEN 3 THEN fei WHEN 4 THEN nuan WHEN 5 THEN mei WHEN 6 THEN xi WHEN 7 THEN lan END,
      CASE i WHEN 1 THEN lan WHEN 2 THEN fei WHEN 3 THEN xi WHEN 4 THEN lan WHEN 5 THEN fei WHEN 6 THEN nuan WHEN 7 THEN mei END,
      4 + i%2, '感谢互借体验', now_ts - (i * 2 || ' days')::INTERVAL);
  END LOOP;
END;
$$;
