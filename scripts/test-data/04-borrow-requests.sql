-- 04-borrow-requests.sql -- 借入申请 pending/active/returned/rejected + 通知 + 评分
DO $$
DECLARE
  xi   BIGINT := 2;
  lan  BIGINT := 3;
  fei  BIGINT := 4;
  nuan BIGINT;
  mei  BIGINT;
  uid  BIGINT;
  br_id BIGINT;
  now_ts TIMESTAMP := NOW();
  i    INT;
BEGIN
  SELECT id INTO nuan FROM users WHERE name = '暖羊羊';
  SELECT id INTO mei  FROM users WHERE name = '美羊羊';

  -- ===== pending 13条 =====
  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, created_at, updated_at)
  SELECT id, lan, 'day', 2, '周末装修需要用一下', 'pending', CURRENT_DATE, now_ts - '1 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = xi AND title = '博世冲击钻套装 GBH 2-20' AND status = 'online' LIMIT 1;
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at) VALUES (xi, 'borrow_request', '新的借入申请', '有人想借你的物品', currval(pg_get_serial_sequence('borrow_requests', 'id')), FALSE, now_ts);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, created_at, updated_at)
  SELECT id, fei, 'day', 3, '大扫除需要', 'pending', CURRENT_DATE, now_ts - '1 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = xi AND title = '戴森吸尘器 V10' AND status = 'online' LIMIT 1;
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at) VALUES (xi, 'borrow_request', '新的借入申请', '有人想借你的物品', currval(pg_get_serial_sequence('borrow_requests', 'id')), FALSE, now_ts);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, created_at, updated_at)
  SELECT id, nuan, 'day', 5, '周末骑行活动', 'pending', CURRENT_DATE, now_ts - '1 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = fei AND title = '山地自行车 27 速' AND status = 'online' LIMIT 1;
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at) VALUES (fei, 'borrow_request', '新的借入申请', '有人想借你的物品', currval(pg_get_serial_sequence('borrow_requests', 'id')), FALSE, now_ts);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, created_at, updated_at)
  SELECT id, mei, 'day', 10, '想读几本电子书', 'pending', CURRENT_DATE, now_ts - '1 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = fei AND title = 'Kindle 电子书' AND status = 'online' LIMIT 1;
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at) VALUES (fei, 'borrow_request', '新的借入申请', '有人想借你的物品', currval(pg_get_serial_sequence('borrow_requests', 'id')), FALSE, now_ts);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, created_at, updated_at)
  SELECT id, xi, 'day', 5, '家里来客人想要现磨咖啡', 'pending', CURRENT_DATE, now_ts - '1 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = nuan AND title = '德龙咖啡机' AND status = 'online' LIMIT 1;
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at) VALUES (nuan, 'borrow_request', '新的借入申请', '有人想借你的物品', currval(pg_get_serial_sequence('borrow_requests', 'id')), FALSE, now_ts);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, created_at, updated_at)
  SELECT id, lan, 'day', 3, '朋友聚餐需要大桌子', 'pending', CURRENT_DATE, now_ts - '1 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = nuan AND title = '折叠餐桌' AND status = 'online' LIMIT 1;
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at) VALUES (nuan, 'borrow_request', '新的借入申请', '有人想借你的物品', currval(pg_get_serial_sequence('borrow_requests', 'id')), FALSE, now_ts);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, created_at, updated_at)
  SELECT id, fei, 'day', 5, '通勤试用', 'pending', CURRENT_DATE, now_ts - '1 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = mei AND title = '折叠自行车' AND status = 'online' LIMIT 1;
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at) VALUES (mei, 'borrow_request', '新的借入申请', '有人想借你的物品', currval(pg_get_serial_sequence('borrow_requests', 'id')), FALSE, now_ts);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, created_at, updated_at)
  SELECT id, nuan, 'day', 14, '想试试再决定要不要买', 'pending', CURRENT_DATE, now_ts - '1 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = mei AND title = '电钢琴 Yamaha' AND status = 'online' LIMIT 1;
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at) VALUES (mei, 'borrow_request', '新的借入申请', '有人想借你的物品', currval(pg_get_serial_sequence('borrow_requests', 'id')), FALSE, now_ts);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, created_at, updated_at)
  SELECT id, xi, 'day', 3, '周末派对用', 'pending', CURRENT_DATE, now_ts - '1 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = lan AND title = 'Switch 游戏机' AND status = 'online' LIMIT 1;
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at) VALUES (lan, 'borrow_request', '新的借入申请', '有人想借你的物品', currval(pg_get_serial_sequence('borrow_requests', 'id')), FALSE, now_ts);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, created_at, updated_at)
  SELECT id, mei, 'day', 1, '周末烧烤用', 'pending', CURRENT_DATE, now_ts - '1 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = lan AND title = '烧烤架' AND status = 'online' LIMIT 1;
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at) VALUES (lan, 'borrow_request', '新的借入申请', '有人想借你的物品', currval(pg_get_serial_sequence('borrow_requests', 'id')), FALSE, now_ts);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, created_at, updated_at)
  SELECT id, lan, 'day', 2, '面试前熨衣服', 'pending', CURRENT_DATE, now_ts - '1 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = mei AND title = '挂烫机' AND status = 'online' LIMIT 1;
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at) VALUES (mei, 'borrow_request', '新的借入申请', '有人想借你的物品', currval(pg_get_serial_sequence('borrow_requests', 'id')), FALSE, now_ts);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, created_at, updated_at)
  SELECT id, fei, 'day', 3, '修家具需要全套工具', 'pending', CURRENT_DATE, now_ts - '1 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = nuan AND title = '工具箱套装' AND status = 'online' LIMIT 1;
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at) VALUES (nuan, 'borrow_request', '新的借入申请', '有人想借你的物品', currval(pg_get_serial_sequence('borrow_requests', 'id')), FALSE, now_ts);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, created_at, updated_at)
  SELECT id, mei, 'day', 4, '想借来试试效果', 'pending', CURRENT_DATE, now_ts - '1 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = nuan AND title = '小米空气净化器' AND status = 'online' LIMIT 1;
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at) VALUES (nuan, 'borrow_request', '新的借入申请', '有人想借你的物品', currval(pg_get_serial_sequence('borrow_requests', 'id')), FALSE, now_ts);

  -- ===== active 13条 =====
  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, created_at, updated_at)
  SELECT id, fei, 'day', 1, '换灯泡用', 'active', CURRENT_DATE - 1, now_ts - '5 days'::INTERVAL, now_ts - '6 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = xi AND title = '折叠梯子 4 步' AND status = 'online' LIMIT 1;
  UPDATE idle_items SET status = 'borrowing' WHERE id = (SELECT idle_id FROM borrow_requests WHERE id = currval(pg_get_serial_sequence('borrow_requests', 'id')));

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, created_at, updated_at)
  SELECT id, nuan, 'day', 3, '家里电饭煲坏了', 'active', CURRENT_DATE - 2, now_ts - '4 days'::INTERVAL, now_ts - '5 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = fei AND title = '电饭煲 5L' AND status = 'online' LIMIT 1;
  UPDATE idle_items SET status = 'borrowing' WHERE id = (SELECT idle_id FROM borrow_requests WHERE id = currval(pg_get_serial_sequence('borrow_requests', 'id')));

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, created_at, updated_at)
  SELECT id, mei, 'day', 5, '试做炸鸡', 'active', CURRENT_DATE - 3, now_ts - '3 days'::INTERVAL, now_ts - '4 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = lan AND title = '空气炸锅 4L' AND status = 'online' LIMIT 1;
  UPDATE idle_items SET status = 'borrowing' WHERE id = (SELECT idle_id FROM borrow_requests WHERE id = currval(pg_get_serial_sequence('borrow_requests', 'id')));

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, created_at, updated_at)
  SELECT id, xi, 'day', 7, '雾霾天用', 'active', CURRENT_DATE - 4, now_ts - '2 days'::INTERVAL, now_ts - '3 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = nuan AND title = '小米空气净化器' AND status = 'online' LIMIT 1;
  UPDATE idle_items SET status = 'borrowing' WHERE id = (SELECT idle_id FROM borrow_requests WHERE id = currval(pg_get_serial_sequence('borrow_requests', 'id')));

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, created_at, updated_at)
  SELECT id, lan, 'day', 7, '给新领养的猫咪试试', 'active', CURRENT_DATE - 5, now_ts - '2 days'::INTERVAL, now_ts - '3 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = mei AND title = '猫爬架' AND status = 'online' LIMIT 1;
  UPDATE idle_items SET status = 'borrowing' WHERE id = (SELECT idle_id FROM borrow_requests WHERE id = currval(pg_get_serial_sequence('borrow_requests', 'id')));

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, created_at, updated_at)
  SELECT id, fei, 'day', 3, '全家露营', 'active', CURRENT_DATE - 1, now_ts - '1 days'::INTERVAL, now_ts - '2 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = xi AND title = '露营帐篷 4 人' AND status = 'online' LIMIT 1;
  UPDATE idle_items SET status = 'borrowing' WHERE id = (SELECT idle_id FROM borrow_requests WHERE id = currval(pg_get_serial_sequence('borrow_requests', 'id')));

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, created_at, updated_at)
  SELECT id, mei, 'day', 5, '每天早起练瑜伽', 'active', CURRENT_DATE - 2, now_ts - '2 days'::INTERVAL, now_ts - '3 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = fei AND title = '瑜伽垫' AND status = 'online' LIMIT 1;
  UPDATE idle_items SET status = 'borrowing' WHERE id = (SELECT idle_id FROM borrow_requests WHERE id = currval(pg_get_serial_sequence('borrow_requests', 'id')));

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, created_at, updated_at)
  SELECT id, nuan, 'day', 5, '出差用', 'active', CURRENT_DATE - 3, now_ts - '2 days'::INTERVAL, now_ts - '4 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = lan AND title = '行李箱 24 寸' AND status = 'online' LIMIT 1;
  UPDATE idle_items SET status = 'borrowing' WHERE id = (SELECT idle_id FROM borrow_requests WHERE id = currval(pg_get_serial_sequence('borrow_requests', 'id')));

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, created_at, updated_at)
  SELECT id, fei, 'day', 3, '想试用意式咖啡', 'active', CURRENT_DATE - 1, now_ts - '1 days'::INTERVAL, now_ts - '2 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = nuan AND title = '德龙咖啡机' AND status = 'online' LIMIT 1;
  UPDATE idle_items SET status = 'borrowing' WHERE id = (SELECT idle_id FROM borrow_requests WHERE id = currval(pg_get_serial_sequence('borrow_requests', 'id')));

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, created_at, updated_at)
  SELECT id, lan, 'day', 2, '周末骑行', 'active', CURRENT_DATE - 1, now_ts - '1 days'::INTERVAL, now_ts - '2 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = fei AND title = '山地自行车 27 速' AND status = 'online' LIMIT 1;
  UPDATE idle_items SET status = 'borrowing' WHERE id = (SELECT idle_id FROM borrow_requests WHERE id = currval(pg_get_serial_sequence('borrow_requests', 'id')));

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, created_at, updated_at)
  SELECT id, mei, 'day', 2, '周末玩', 'active', CURRENT_DATE - 1, now_ts - '1 days'::INTERVAL, now_ts - '2 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = lan AND title = 'Switch 游戏机' AND status = 'online' LIMIT 1;
  UPDATE idle_items SET status = 'borrowing' WHERE id = (SELECT idle_id FROM borrow_requests WHERE id = currval(pg_get_serial_sequence('borrow_requests', 'id')));

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, created_at, updated_at)
  SELECT id, xi, 'day', 3, '通勤试试', 'active', CURRENT_DATE - 1, now_ts - '1 days'::INTERVAL, now_ts - '2 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = mei AND title = '折叠自行车' AND status = 'online' LIMIT 1;
  UPDATE idle_items SET status = 'borrowing' WHERE id = (SELECT idle_id FROM borrow_requests WHERE id = currval(pg_get_serial_sequence('borrow_requests', 'id')));

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, created_at, updated_at)
  SELECT id, nuan, 'day', 4, '给孩子用', 'active', CURRENT_DATE - 1, now_ts - '1 days'::INTERVAL, now_ts - '2 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = fei AND title = 'Kindle 电子书' AND status = 'online' LIMIT 1;
  UPDATE idle_items SET status = 'borrowing' WHERE id = (SELECT idle_id FROM borrow_requests WHERE id = currval(pg_get_serial_sequence('borrow_requests', 'id')));

  -- ===== rejected 12条 =====
  FOR uid IN SELECT unnest(ARRAY[xi, lan, fei, nuan, mei]) LOOP
    FOR i IN 1..2 LOOP
      INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, created_at, updated_at)
      SELECT id,
        CASE WHEN uid=xi AND i=1 THEN lan WHEN uid=xi AND i=2 THEN mei
             WHEN uid=lan AND i=1 THEN fei WHEN uid=lan AND i=2 THEN nuan
             WHEN uid=fei AND i=1 THEN xi WHEN uid=fei AND i=2 THEN mei
             WHEN uid=nuan AND i=1 THEN lan WHEN uid=nuan AND i=2 THEN fei
             WHEN uid=mei AND i=1 THEN xi WHEN uid=mei AND i=2 THEN lan END,
        'day', i + 1, '暂时不需要，抱歉', 'rejected',
        now_ts - ((i + 1) * 2 || ' days')::INTERVAL, now_ts
      FROM idle_items WHERE user_id = uid AND status = 'online' LIMIT 1;
    END LOOP;
  END LOOP;

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, created_at, updated_at)
  SELECT id, nuan, 'day', 3, '不方便出借', 'rejected', now_ts - '14 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = xi AND title = '戴森吸尘器 V10' AND status = 'online' LIMIT 1;

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, created_at, updated_at)
  SELECT id, xi, 'day', 5, '正在使用中', 'rejected', now_ts - '10 days'::INTERVAL, now_ts FROM idle_items WHERE user_id = fei AND title = '瑜伽垫' AND status = 'online' LIMIT 1;

  -- ===== returned 13条 + 评分 =====
  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, return_status, damage_type, is_on_time, created_at, updated_at)
  SELECT id, xi, 'day', 3, '周末游戏派对', 'returned', '2026-07-10', now_ts - '14 days'::INTERVAL, 'ontime', 'none', TRUE, now_ts - '17 days'::INTERVAL, now_ts - '12 days'::INTERVAL FROM idle_items WHERE user_id = lan AND title = 'Switch 游戏机' AND status = 'online' LIMIT 1;
  br_id := currval(pg_get_serial_sequence('borrow_requests', 'id'));
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, xi, lan, 5, 'Switch保养得很好，玩得很开心', now_ts - '12 days'::INTERVAL);
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, lan, xi, 5, '喜羊羊很守信用，按时归还', now_ts - '11 days'::INTERVAL);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, return_status, damage_type, is_on_time, created_at, updated_at)
  SELECT id, fei, 'day', 1, '墙上打孔装挂画', 'returned', '2026-07-05', now_ts - '20 days'::INTERVAL, 'ontime', 'none', TRUE, now_ts - '22 days'::INTERVAL, now_ts - '18 days'::INTERVAL FROM idle_items WHERE user_id = xi AND title = '博世冲击钻套装 GBH 2-20' AND status = 'online' LIMIT 1;
  br_id := currval(pg_get_serial_sequence('borrow_requests', 'id'));
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, fei, xi, 5, '工具很专业，钻头齐全', now_ts - '18 days'::INTERVAL);
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, xi, fei, 4, '归还及时，但钻头有一个轻微磨损', now_ts - '17 days'::INTERVAL);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, return_status, damage_type, is_on_time, created_at, updated_at)
  SELECT id, nuan, 'day', 10, '看电子书', 'returned', '2026-06-25', now_ts - '30 days'::INTERVAL, 'ontime', 'none', TRUE, now_ts - '32 days'::INTERVAL, now_ts - '27 days'::INTERVAL FROM idle_items WHERE user_id = fei AND title = 'Kindle 电子书' AND status = 'online' LIMIT 1;
  br_id := currval(pg_get_serial_sequence('borrow_requests', 'id'));
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, nuan, fei, 5, 'Kindle阅读体验很好，谢谢', now_ts - '27 days'::INTERVAL);
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, fei, nuan, 5, '暖羊羊很爱护设备，原样归还', now_ts - '26 days'::INTERVAL);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, return_status, damage_type, is_on_time, created_at, updated_at)
  SELECT id, mei, 'day', 2, '组装家具', 'returned', '2026-07-08', now_ts - '17 days'::INTERVAL, 'delayed', 'slight', FALSE, now_ts - '20 days'::INTERVAL, now_ts - '15 days'::INTERVAL FROM idle_items WHERE user_id = nuan AND title = '工具箱套装' AND status = 'online' LIMIT 1;
  br_id := currval(pg_get_serial_sequence('borrow_requests', 'id'));
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, mei, nuan, 4, '工具齐全好用', now_ts - '15 days'::INTERVAL);
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, nuan, mei, 3, '逾期了两天才还', now_ts - '14 days'::INTERVAL);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, return_status, damage_type, is_on_time, created_at, updated_at)
  SELECT id, lan, 'day', 1, '擦吊灯', 'returned', '2026-07-12', now_ts - '13 days'::INTERVAL, 'ontime', 'none', TRUE, now_ts - '15 days'::INTERVAL, now_ts - '11 days'::INTERVAL FROM idle_items WHERE user_id = xi AND title = '折叠梯子 4 步' AND status = 'online' LIMIT 1;
  br_id := currval(pg_get_serial_sequence('borrow_requests', 'id'));
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, lan, xi, 5, '梯子很稳当，用着放心', now_ts - '11 days'::INTERVAL);
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, xi, lan, 5, '用完擦干净才还的', now_ts - '10 days'::INTERVAL);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, return_status, damage_type, is_on_time, created_at, updated_at)
  SELECT id, fei, 'day', 14, '孩子想学琴先试弹', 'returned', '2026-06-20', now_ts - '35 days'::INTERVAL, 'ontime', 'none', TRUE, now_ts - '37 days'::INTERVAL, now_ts - '31 days'::INTERVAL FROM idle_items WHERE user_id = mei AND title = '电钢琴 Yamaha' AND status = 'online' LIMIT 1;
  br_id := currval(pg_get_serial_sequence('borrow_requests', 'id'));
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, fei, mei, 5, '琴音色很好', now_ts - '31 days'::INTERVAL);
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, mei, fei, 5, '完好归还', now_ts - '30 days'::INTERVAL);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, return_status, damage_type, is_on_time, created_at, updated_at)
  SELECT id, xi, 'day', 3, '做炸鸡试吃', 'returned', '2026-07-13', now_ts - '12 days'::INTERVAL, 'ontime', 'none', TRUE, now_ts - '14 days'::INTERVAL, now_ts - '10 days'::INTERVAL FROM idle_items WHERE user_id = lan AND title = '空气炸锅 4L' AND status = 'online' LIMIT 1;
  br_id := currval(pg_get_serial_sequence('borrow_requests', 'id'));
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, xi, lan, 5, '炸鸡外酥里嫩', now_ts - '10 days'::INTERVAL);
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, lan, xi, 4, '锅没洗干净', now_ts - '9 days'::INTERVAL);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, return_status, damage_type, is_on_time, damage_note, created_at, updated_at)
  SELECT id, nuan, 'day', 5, '春节大扫除', 'returned', '2026-07-01', now_ts - '25 days'::INTERVAL, 'delayed', 'moderate', FALSE, '滤网堵塞需要更换', now_ts - '30 days'::INTERVAL, now_ts - '22 days'::INTERVAL FROM idle_items WHERE user_id = xi AND title = '戴森吸尘器 V10' AND status = 'online' LIMIT 1;
  br_id := currval(pg_get_serial_sequence('borrow_requests', 'id'));
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, nuan, xi, 3, '不小心弄堵了滤网', now_ts - '22 days'::INTERVAL);
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, xi, nuan, 2, '逾期归还，滤网没清理', now_ts - '21 days'::INTERVAL);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, return_status, damage_type, is_on_time, created_at, updated_at)
  SELECT id, lan, 'day', 5, '跟着视频练瑜伽', 'returned', '2026-07-10', now_ts - '16 days'::INTERVAL, 'ontime', 'none', TRUE, now_ts - '18 days'::INTERVAL, now_ts - '13 days'::INTERVAL FROM idle_items WHERE user_id = fei AND title = '瑜伽垫' AND status = 'online' LIMIT 1;
  br_id := currval(pg_get_serial_sequence('borrow_requests', 'id'));
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, lan, fei, 5, '垫子防滑很好', now_ts - '13 days'::INTERVAL);
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, fei, lan, 5, '懒羊羊很干净', now_ts - '12 days'::INTERVAL);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, return_status, damage_type, is_on_time, created_at, updated_at)
  SELECT id, mei, 'day', 3, '家里来客人煮大份饭', 'returned', '2026-07-14', now_ts - '10 days'::INTERVAL, 'ontime', 'none', TRUE, now_ts - '12 days'::INTERVAL, now_ts - '8 days'::INTERVAL FROM idle_items WHERE user_id = fei AND title = '电饭煲 5L' AND status = 'online' LIMIT 1;
  br_id := currval(pg_get_serial_sequence('borrow_requests', 'id'));
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, mei, fei, 5, '大容量电饭煲太好用了', now_ts - '8 days'::INTERVAL);
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, fei, mei, 5, '美羊羊做饭水平一流', now_ts - '7 days'::INTERVAL);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, return_status, damage_type, is_on_time, created_at, updated_at)
  SELECT id, xi, 'day', 2, '请客聚餐', 'returned', '2026-07-15', now_ts - '8 days'::INTERVAL, 'ontime', 'none', TRUE, now_ts - '10 days'::INTERVAL, now_ts - '6 days'::INTERVAL FROM idle_items WHERE user_id = nuan AND title = '折叠餐桌' AND status = 'online' LIMIT 1;
  br_id := currval(pg_get_serial_sequence('borrow_requests', 'id'));
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, xi, nuan, 5, '折叠桌很方便不占地方', now_ts - '6 days'::INTERVAL);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, return_status, damage_type, is_on_time, created_at, updated_at)
  SELECT id, fei, 'day', 5, '出差用', 'returned', '2026-07-09', now_ts - '20 days'::INTERVAL, 'ontime', 'none', TRUE, now_ts - '22 days'::INTERVAL, now_ts - '17 days'::INTERVAL FROM idle_items WHERE user_id = lan AND title = '行李箱 24 寸' AND status = 'online' LIMIT 1;
  br_id := currval(pg_get_serial_sequence('borrow_requests', 'id'));
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, fei, lan, 5, '行李箱轮子顺滑容量刚好', now_ts - '17 days'::INTERVAL);
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, lan, fei, 5, '还回来还帮我擦了箱面', now_ts - '16 days'::INTERVAL);

  INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, start_date, approved_at, return_status, damage_type, is_on_time, created_at, updated_at)
  SELECT id, mei, 'day', 2, '家庭露营', 'returned', '2026-07-16', now_ts - '7 days'::INTERVAL, 'ontime', 'none', TRUE, now_ts - '9 days'::INTERVAL, now_ts - '5 days'::INTERVAL FROM idle_items WHERE user_id = xi AND title = '露营帐篷 4 人' AND status = 'online' LIMIT 1;
  br_id := currval(pg_get_serial_sequence('borrow_requests', 'id'));
  INSERT INTO ratings (borrow_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (br_id, mei, xi, 5, '帐篷空间大，一家四口完全够用', now_ts - '5 days'::INTERVAL);

END;
$$;
