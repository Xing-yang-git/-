-- 06-supplement.sql -- 补充数据，补齐每种状态 >= 12 条
DO $$
DECLARE
  xi   BIGINT := 2;
  lan  BIGINT := 3;
  fei  BIGINT := 4;
  nuan BIGINT;
  mei  BIGINT;
  uid  BIGINT;
  rid  BIGINT;
  ha_id BIGINT;
  now_ts TIMESTAMP := NOW();
  i    INT;
BEGIN
  SELECT id INTO nuan FROM users WHERE name = '暖羊羊';
  SELECT id INTO mei  FROM users WHERE name = '美羊羊';

  -- ===== 1. 补充 online 求助 (当前3，需+9) =====
  FOR uid IN SELECT unnest(ARRAY[xi, lan, fei, nuan, mei]) LOOP
    FOR i IN 1..2 LOOP
      INSERT INTO help_requests (user_id, tenant_id, title, description, category, is_urgent, status, created_at, updated_at)
      VALUES (uid, 1,
        CASE WHEN uid=xi AND i=1 THEN '帮忙通下水道' WHEN uid=xi AND i=2 THEN '代买早餐'
             WHEN uid=lan AND i=1 THEN '帮忙换灯管' WHEN uid=lan AND i=2 THEN '陪聊天解闷'
             WHEN uid=fei AND i=1 THEN '帮忙搬家'   WHEN uid=fei AND i=2 THEN '帮忙洗车'
             WHEN uid=nuan AND i=1 THEN '帮忙接孩子' WHEN uid=nuan AND i=2 THEN '代取外卖'
             WHEN uid=mei AND i=1 THEN '帮忙倒垃圾' WHEN uid=mei AND i=2 THEN '帮忙浇花'
        END,
        '需要帮助，请私聊详谈',
        CASE WHEN uid IN (xi, fei) THEN '维修' WHEN uid=lan THEN '搬运' WHEN uid=nuan THEN '代取' WHEN uid=mei THEN '陪护' END,
        FALSE, 'online', now_ts - (i || ' days')::INTERVAL, now_ts);
    END LOOP;
  END LOOP;

  -- ===== 2. 补充 deleted 闲置物品 (当前7，需+5) =====
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

  -- ===== 3. 补充 deleted 求助 (当前6，需+6) =====
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

  -- ===== 4. 补充 rejected borrow (当前11，需+1) =====
  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, created_at, updated_at)
  SELECT id, mei, 'day', 2, '不需要了', 'rejected', now_ts - '3 days'::INTERVAL, now_ts
  FROM idle_items WHERE user_id = xi AND status = 'online' LIMIT 1;

  -- ===== 5. 补充 returned borrow (当前5，需+7) — 用没有进入 borrowing 状态的物品 =====
  FOR i IN 1..7 LOOP
    INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, return_status, damage_type, is_on_time, created_at, updated_at)
    SELECT id, CASE i WHEN 1 THEN xi WHEN 2 THEN lan WHEN 3 THEN fei WHEN 4 THEN nuan WHEN 5 THEN mei WHEN 6 THEN xi WHEN 7 THEN lan END,
      'day', i, '测试数据-已完成', 'returned',
      '2026-07-' || (10 + i), now_ts - (i * 3 || ' days')::INTERVAL,
      CASE i%3 WHEN 0 THEN 'ontime' WHEN 1 THEN 'delayed' ELSE 'ontime' END,
      CASE i%3 WHEN 0 THEN 'none' WHEN 1 THEN 'slight' ELSE 'none' END,
      i%3 <> 1, now_ts - (i * 4 || ' days')::INTERVAL, now_ts - (i * 2 || ' days')::INTERVAL
    FROM idle_items WHERE user_id = CASE i WHEN 1 THEN lan WHEN 2 THEN fei WHEN 3 THEN xi WHEN 4 THEN lan WHEN 5 THEN fei WHEN 6 THEN nuan WHEN 7 THEN mei END
      AND status NOT IN ('offline') LIMIT 1;
    rid := currval(pg_get_serial_sequence('borrow_requests', 'id'));
    INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at)
    VALUES (rid,
      CASE i WHEN 1 THEN xi WHEN 2 THEN lan WHEN 3 THEN fei WHEN 4 THEN nuan WHEN 5 THEN mei WHEN 6 THEN xi WHEN 7 THEN lan END,
      CASE i WHEN 1 THEN lan WHEN 2 THEN fei WHEN 3 THEN xi WHEN 4 THEN lan WHEN 5 THEN fei WHEN 6 THEN nuan WHEN 7 THEN mei END,
      4 + i%2, '感谢互借体验', now_ts - (i * 2 || ' days')::INTERVAL);
  END LOOP;

  -- ===== 6. 补充 rejected help_applications (当前8，需+4) =====
  FOR i IN 1..4 LOOP
    INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
    SELECT id, CASE i WHEN 1 THEN xi WHEN 2 THEN fei WHEN 3 THEN nuan WHEN 4 THEN mei END,
      '最近没空', 'rejected', now_ts - (i * 2 || ' days')::INTERVAL, now_ts
    FROM help_requests WHERE user_id = CASE i WHEN 1 THEN lan WHEN 2 THEN nuan WHEN 3 THEN fei WHEN 4 THEN xi END
      AND status = 'online' LIMIT 1;
  END LOOP;

  -- ===== 7. 补充 completed help_applications (当前2，需+10) — 用 helping 状态的求助 =====
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
