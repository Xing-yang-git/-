-- 06a -- 补充 online 求助 (+9)
DO $$
DECLARE
  xi   BIGINT := 2; lan BIGINT := 3; fei BIGINT := 4; nuan BIGINT; mei BIGINT; uid BIGINT;
  now_ts TIMESTAMP := NOW(); i INT;
BEGIN
  SELECT id INTO nuan FROM users WHERE name = '暖羊羊';
  SELECT id INTO mei  FROM users WHERE name = '美羊羊';
  FOR uid IN SELECT unnest(ARRAY[xi, lan, fei, nuan, mei]) LOOP
    FOR i IN 1..2 LOOP
      INSERT INTO help_requests (user_id, tenant_id, title, description, category, is_urgent, status, created_at, updated_at)
      VALUES (uid, 1,
        CASE WHEN uid=xi AND i=1 THEN '帮忙通下水道' WHEN uid=xi AND i=2 THEN '代买早餐'
             WHEN uid=lan AND i=1 THEN '帮忙换灯管' WHEN uid=lan AND i=2 THEN '陪聊天解闷'
             WHEN uid=fei AND i=1 THEN '帮忙搬家'   WHEN uid=fei AND i=2 THEN '帮忙洗车'
             WHEN uid=nuan AND i=1 THEN '帮忙接孩子' WHEN uid=nuan AND i=2 THEN '代取外卖'
             WHEN uid=mei AND i=1 THEN '帮忙倒垃圾' WHEN uid=mei AND i=2 THEN '帮忙浇花' END,
        '需要帮助，请私聊详谈',
        CASE WHEN uid IN (xi, fei) THEN '维修' WHEN uid=lan THEN '搬运' WHEN uid=nuan THEN '代取' WHEN uid=mei THEN '陪护' END,
        FALSE, 'online', now_ts - (i || ' days')::INTERVAL, now_ts);
    END LOOP;
  END LOOP;
END;
$$;
