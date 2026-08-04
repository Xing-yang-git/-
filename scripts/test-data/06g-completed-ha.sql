-- 06g -- 补充 completed help_applications (+10)
DO $$
DECLARE
  xi BIGINT:=2; lan BIGINT:=3; fei BIGINT:=4; nuan BIGINT; mei BIGINT;
  ha_id BIGINT; now_ts TIMESTAMP:=NOW(); i INT;
BEGIN
  SELECT id INTO nuan FROM users WHERE name='暖羊羊';
  SELECT id INTO mei  FROM users WHERE name='美羊羊';
  FOR i IN 1..10 LOOP
    INSERT INTO help_applications (help_id, helper_id, note, status, completed_at, created_at, updated_at)
    SELECT id, CASE i%5 WHEN 0 THEN xi WHEN 1 THEN lan WHEN 2 THEN fei WHEN 3 THEN nuan WHEN 4 THEN mei END,
      '测试-已完成', 'completed', now_ts - (i || ' days')::INTERVAL,
      now_ts - (i * 2 || ' days')::INTERVAL, now_ts - (i || ' days')::INTERVAL
    FROM help_requests WHERE user_id = CASE i%5 WHEN 0 THEN fei WHEN 1 THEN xi WHEN 2 THEN mei WHEN 3 THEN lan WHEN 4 THEN nuan END
      AND status IN ('online', 'helping') LIMIT 1;
    ha_id := currval(pg_get_serial_sequence('help_applications', 'id'));
    INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at)
    VALUES (ha_id,
      CASE i%5 WHEN 0 THEN fei WHEN 1 THEN xi WHEN 2 THEN mei WHEN 3 THEN lan WHEN 4 THEN nuan END,
      CASE i%5 WHEN 0 THEN xi WHEN 1 THEN lan WHEN 2 THEN fei WHEN 3 THEN nuan WHEN 4 THEN mei END,
      4 + i%2, '感谢互助体验', now_ts - (i || ' days')::INTERVAL);
  END LOOP;
END;
$$;
