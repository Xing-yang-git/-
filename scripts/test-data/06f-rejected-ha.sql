-- 06f -- 补充 rejected help_applications (+4)
DO $$
DECLARE
  xi BIGINT:=2; fei BIGINT:=4; nuan BIGINT; mei BIGINT;
  now_ts TIMESTAMP:=NOW(); i INT;
BEGIN
  SELECT id INTO nuan FROM users WHERE name='暖羊羊';
  SELECT id INTO mei  FROM users WHERE name='美羊羊';
  FOR i IN 1..4 LOOP
    INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
    SELECT id, CASE i WHEN 1 THEN xi WHEN 2 THEN fei WHEN 3 THEN nuan WHEN 4 THEN mei END,
      '最近没空', 'rejected', now_ts - (i * 2 || ' days')::INTERVAL, now_ts
    FROM help_requests WHERE user_id = CASE i WHEN 1 THEN 3 WHEN 2 THEN nuan WHEN 3 THEN fei WHEN 4 THEN xi END
      AND status = 'online' LIMIT 1;
  END LOOP;
END;
$$;
