-- 02-idle-items.sql -- 闲置物品 + 违规通知
DO $$
DECLARE
  xi   BIGINT := 2;
  lan  BIGINT := 3;
  fei  BIGINT := 4;
  nuan BIGINT;
  mei  BIGINT;
  now_ts TIMESTAMP := NOW();
  i     INT;
BEGIN
  SELECT id INTO nuan FROM users WHERE name = '暖羊羊';
  SELECT id INTO mei  FROM users WHERE name = '美羊羊';

  -- 喜羊羊 LEND 4条
  FOR i IN 1..4 LOOP
    INSERT INTO idle_items (user_id, tenant_id, post_type, title, description, category, condition, price, images, max_duration, duration_unit, pickup_method, status, created_at, updated_at)
    VALUES (xi, 1, 'LEND',
      CASE i WHEN 1 THEN '博世冲击钻套装 GBH 2-20' WHEN 2 THEN '戴森吸尘器 V10' WHEN 3 THEN '折叠梯子 4 步' WHEN 4 THEN '露营帐篷 4 人' END,
      CASE i WHEN 1 THEN '九成新，附带原装钻头 6 支' WHEN 2 THEN '使用一年，吸力正常' WHEN 3 THEN '只用过两次，无锈蚀' WHEN 4 THEN '双层防雨，含地垫' END,
      CASE i WHEN 1 THEN '工具' WHEN 2 THEN '家居' WHEN 3 THEN '工具' WHEN 4 THEN '运动' END,
      CASE i WHEN 1 THEN 'like-new' WHEN 2 THEN 'normal' WHEN 3 THEN 'like-new' WHEN 4 THEN 'normal' END,
      CASE i WHEN 1 THEN 800 WHEN 2 THEN 1500 WHEN 3 THEN 200 WHEN 4 THEN 300 END,
      '[]', CASE i WHEN 1 THEN 3 WHEN 2 THEN 5 WHEN 3 THEN 2 WHEN 4 THEN 4 END,
      'day', CASE i WHEN 1 THEN 'self_pickup' WHEN 2 THEN 'both' WHEN 3 THEN 'self_pickup' WHEN 4 THEN 'both' END,
      'online', now_ts - (i || ' days')::INTERVAL, now_ts);
  END LOOP;

  -- 喜羊羊 WANTED 3条
  FOR i IN 1..3 LOOP
    INSERT INTO idle_items (user_id, tenant_id, post_type, title, description, category, price, max_duration, duration_unit, status, created_at, updated_at)
    VALUES (xi, 1, 'WANTED',
      CASE i WHEN 1 THEN '急需投影仪' WHEN 2 THEN '借用单反相机' WHEN 3 THEN '借用电钻' END,
      CASE i WHEN 1 THEN '周末孩子生日会放电影用' WHEN 2 THEN '旅游拍摄需要，借用 3 天' WHEN 3 THEN '家里的坏了，借用半天' END,
      CASE i WHEN 1 THEN '电子产品' WHEN 2 THEN '电子产品' WHEN 3 THEN '工具' END,
      0, CASE i WHEN 1 THEN 2 WHEN 2 THEN 3 WHEN 3 THEN 1 END,
      'day', 'online', now_ts - (i || ' days')::INTERVAL, now_ts);
  END LOOP;

  -- 喜羊羊 deleted 2条
  FOR i IN 1..2 LOOP
    INSERT INTO idle_items (user_id, tenant_id, post_type, title, description, category, condition, price, max_duration, duration_unit, status, delist_reason, created_at, updated_at)
    VALUES (xi, 1, 'LEND',
      CASE i WHEN 1 THEN '二手手机 iPhone 12' WHEN 2 THEN '电动滑板车' END,
      CASE i WHEN 1 THEN '屏幕有划痕但功能正常' WHEN 2 THEN '续航约 15 公里' END,
      '电子产品', 'worn', CASE i WHEN 1 THEN 1500 WHEN 2 THEN 800 END,
      CASE i WHEN 1 THEN 7 WHEN 2 THEN 3 END, 'day',
      'offline', '标题与实物不符，经核实为翻新机',
      now_ts - (i * 3 || ' days')::INTERVAL, now_ts);
    INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at)
    VALUES (xi, 'violation', '物品被管理员下架', '您的物品被下架，原因：标题与实物不符',
      currval(pg_get_serial_sequence('idle_items', 'id')), FALSE, now_ts);
  END LOOP;

  -- 沸羊羊 LEND 4条
  FOR i IN 1..4 LOOP
    INSERT INTO idle_items (user_id, tenant_id, post_type, title, description, category, condition, price, images, max_duration, duration_unit, pickup_method, status, created_at, updated_at)
    VALUES (fei, 1, 'LEND',
      CASE i WHEN 1 THEN '山地自行车 27 速' WHEN 2 THEN '电饭煲 5L' WHEN 3 THEN 'Kindle 电子书' WHEN 4 THEN '瑜伽垫' END,
      CASE i WHEN 1 THEN '去年买的，骑了不到十次' WHEN 2 THEN '闲置未拆封' WHEN 3 THEN '屏幕完好，已重置' WHEN 4 THEN '轻微使用痕迹' END,
      CASE i WHEN 1 THEN '运动' WHEN 2 THEN '家居' WHEN 3 THEN '电子产品' WHEN 4 THEN '运动' END,
      CASE i WHEN 1 THEN 'normal' WHEN 2 THEN 'like-new' WHEN 3 THEN 'normal' WHEN 4 THEN 'worn' END,
      CASE i WHEN 1 THEN 1200 WHEN 2 THEN 300 WHEN 3 THEN 500 WHEN 4 THEN 50 END,
      '[]', CASE i WHEN 1 THEN 7 WHEN 2 THEN 5 WHEN 3 THEN 14 WHEN 4 THEN 7 END,
      'day', CASE i WHEN 1 THEN 'both' WHEN 2 THEN 'self_pickup' WHEN 3 THEN 'self_pickup' WHEN 4 THEN 'self_pickup' END,
      'online', now_ts - (i || ' days')::INTERVAL, now_ts);
  END LOOP;

  -- 沸羊羊 WANTED 3条
  FOR i IN 1..3 LOOP
    INSERT INTO idle_items (user_id, tenant_id, post_type, title, description, category, price, max_duration, duration_unit, status, created_at, updated_at)
    VALUES (fei, 1, 'WANTED',
      CASE i WHEN 1 THEN '借婴儿推车' WHEN 2 THEN '借用冲击钻' WHEN 3 THEN '借 HDMI 线' END,
      CASE i WHEN 1 THEN '带娃出门用，借用一周' WHEN 2 THEN '墙上打几个孔' WHEN 3 THEN '会议演示用' END,
      CASE i WHEN 1 THEN '家居' WHEN 2 THEN '工具' WHEN 3 THEN '电子产品' END,
      0, CASE i WHEN 1 THEN 7 WHEN 2 THEN 1 WHEN 3 THEN 1 END,
      'day', 'online', now_ts - (i || ' days')::INTERVAL, now_ts);
  END LOOP;

  -- 沸羊羊 deleted 2条
  FOR i IN 1..2 LOOP
    INSERT INTO idle_items (user_id, tenant_id, post_type, title, description, category, condition, price, max_duration, duration_unit, status, delist_reason, created_at, updated_at)
    VALUES (fei, 1, 'LEND',
      CASE i WHEN 1 THEN '高仿名牌包' WHEN 2 THEN '游戏账号出租' END,
      CASE i WHEN 1 THEN '几乎全新，和正品一样' WHEN 2 THEN '王者荣耀满级号出租' END,
      '其他', 'normal', CASE i WHEN 1 THEN 3000 WHEN 2 THEN 100 END,
      CASE i WHEN 1 THEN 30 WHEN 2 THEN 7 END, 'day',
      'offline', CASE i WHEN 1 THEN '仿冒品禁止发布' WHEN 2 THEN '虚拟物品不在平台允许范围' END,
      now_ts - (i * 3 || ' days')::INTERVAL, now_ts);
    INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at)
    VALUES (fei, 'violation', '物品被管理员下架', '您的物品被下架，原因：违规',
      currval(pg_get_serial_sequence('idle_items', 'id')), FALSE, now_ts);
  END LOOP;

  -- 懒羊羊 LEND 4条
  FOR i IN 1..4 LOOP
    INSERT INTO idle_items (user_id, tenant_id, post_type, title, description, category, condition, price, images, max_duration, duration_unit, pickup_method, status, created_at, updated_at)
    VALUES (lan, 1, 'LEND',
      CASE i WHEN 1 THEN '空气炸锅 4L' WHEN 2 THEN 'Switch 游戏机' WHEN 3 THEN '行李箱 24 寸' WHEN 4 THEN '烧烤架' END,
      CASE i WHEN 1 THEN '用过几次，功能完好' WHEN 2 THEN '含 4 款游戏卡带' WHEN 3 THEN '短期出行用，很新' WHEN 4 THEN '去年夏天买的' END,
      CASE i WHEN 1 THEN '家居' WHEN 2 THEN '电子产品' WHEN 3 THEN '其他' WHEN 4 THEN '家居' END,
      CASE i WHEN 1 THEN 'normal' WHEN 2 THEN 'normal' WHEN 3 THEN 'like-new' WHEN 4 THEN 'normal' END,
      CASE i WHEN 1 THEN 200 WHEN 2 THEN 1800 WHEN 3 THEN 300 WHEN 4 THEN 150 END,
      '[]', CASE i WHEN 1 THEN 7 WHEN 2 THEN 5 WHEN 3 THEN 5 WHEN 4 THEN 3 END,
      'day', CASE i WHEN 1 THEN 'self_pickup' WHEN 2 THEN 'both' WHEN 3 THEN 'self_pickup' WHEN 4 THEN 'self_pickup' END,
      'online', now_ts - (i || ' days')::INTERVAL, now_ts);
  END LOOP;

  -- 懒羊羊 WANTED 3条
  FOR i IN 1..3 LOOP
    INSERT INTO idle_items (user_id, tenant_id, post_type, title, description, category, price, max_duration, duration_unit, status, created_at, updated_at)
    VALUES (lan, 1, 'WANTED',
      CASE i WHEN 1 THEN '借用手提电脑' WHEN 2 THEN '借用电磁炉' WHEN 3 THEN '借用三脚架' END,
      CASE i WHEN 1 THEN '出差汇报用，借用 2 天' WHEN 2 THEN '家里来客人做火锅' WHEN 3 THEN '拍全家福用' END,
      CASE i WHEN 1 THEN '电子产品' WHEN 2 THEN '家居' WHEN 3 THEN '电子产品' END,
      0, CASE i WHEN 1 THEN 2 WHEN 2 THEN 1 WHEN 3 THEN 1 END,
      'day', 'online', now_ts - (i || ' days')::INTERVAL, now_ts);
  END LOOP;

  -- 懒羊羊 deleted 1条
  INSERT INTO idle_items (user_id, tenant_id, post_type, title, description, category, condition, price, max_duration, duration_unit, status, delist_reason, created_at, updated_at)
  VALUES (lan, 1, 'LEND', '三无充电宝', '大容量充电宝，无品牌', '电子产品', 'worn', 30, 30, 'day',
    'offline', '三无产品存在安全隐患', now_ts - '4 days'::INTERVAL, now_ts);
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at)
  VALUES (lan, 'violation', '物品被管理员下架', '您的物品被下架，原因：三无产品存在安全隐患',
    currval(pg_get_serial_sequence('idle_items', 'id')), FALSE, now_ts);

  -- 暖羊羊 LEND 4条
  FOR i IN 1..4 LOOP
    INSERT INTO idle_items (user_id, tenant_id, post_type, title, description, category, condition, price, images, max_duration, duration_unit, pickup_method, status, created_at, updated_at)
    VALUES (nuan, 1, 'LEND',
      CASE i WHEN 1 THEN '德龙咖啡机' WHEN 2 THEN '小米空气净化器' WHEN 3 THEN '折叠餐桌' WHEN 4 THEN '工具箱套装' END,
      CASE i WHEN 1 THEN '意式全自动，买来很少用' WHEN 2 THEN '滤芯还有 80% 寿命' WHEN 3 THEN '可折叠收纳不占地方' WHEN 4 THEN '含扳手螺丝刀等 40 件' END,
      CASE i WHEN 1 THEN '家居' WHEN 2 THEN '家居' WHEN 3 THEN '家居' WHEN 4 THEN '工具' END,
      CASE i WHEN 1 THEN 'like-new' WHEN 2 THEN 'normal' WHEN 3 THEN 'normal' WHEN 4 THEN 'normal' END,
      CASE i WHEN 1 THEN 2500 WHEN 2 THEN 800 WHEN 3 THEN 400 WHEN 4 THEN 300 END,
      '[]', CASE i WHEN 1 THEN 7 WHEN 2 THEN 14 WHEN 3 THEN 5 WHEN 4 THEN 7 END,
      'day', CASE i WHEN 1 THEN 'both' WHEN 2 THEN 'self_pickup' WHEN 3 THEN 'self_pickup' WHEN 4 THEN 'both' END,
      'online', now_ts - (i || ' days')::INTERVAL, now_ts);
  END LOOP;

  -- 暖羊羊 WANTED 3条
  FOR i IN 1..3 LOOP
    INSERT INTO idle_items (user_id, tenant_id, post_type, title, description, category, price, max_duration, duration_unit, status, created_at, updated_at)
    VALUES (nuan, 1, 'WANTED',
      CASE i WHEN 1 THEN '借用打印机' WHEN 2 THEN '借用轮椅' WHEN 3 THEN '借用面包机' END,
      CASE i WHEN 1 THEN '打印孩子作业，借用一天' WHEN 2 THEN '老人腿脚不便临时用一周' WHEN 3 THEN '想试试再做购买决定' END,
      CASE i WHEN 1 THEN '电子产品' WHEN 2 THEN '其他' WHEN 3 THEN '家居' END,
      0, CASE i WHEN 1 THEN 1 WHEN 2 THEN 7 WHEN 3 THEN 3 END,
      'day', 'online', now_ts - (i || ' days')::INTERVAL, now_ts);
  END LOOP;

  -- 暖羊羊 deleted 1条
  INSERT INTO idle_items (user_id, tenant_id, post_type, title, description, category, condition, price, max_duration, duration_unit, status, delist_reason, created_at, updated_at)
  VALUES (nuan, 1, 'LEND', '宠物蛇出售', '玉米蛇一条，很温顺', '其他', 'normal', 500, 30, 'day',
    'offline', '活体动物禁止在平台发布', now_ts - '3 days'::INTERVAL, now_ts);
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at)
  VALUES (nuan, 'violation', '物品被管理员下架', '您的物品被下架，原因：活体动物禁止发布',
    currval(pg_get_serial_sequence('idle_items', 'id')), FALSE, now_ts);

  -- 美羊羊 LEND 4条
  FOR i IN 1..4 LOOP
    INSERT INTO idle_items (user_id, tenant_id, post_type, title, description, category, condition, price, images, max_duration, duration_unit, pickup_method, status, created_at, updated_at)
    VALUES (mei, 1, 'LEND',
      CASE i WHEN 1 THEN '猫爬架' WHEN 2 THEN '电钢琴 Yamaha' WHEN 3 THEN '挂烫机' WHEN 4 THEN '折叠自行车' END,
      CASE i WHEN 1 THEN '猫咪不爱玩，几乎全新' WHEN 2 THEN '初学琴，用了半年' WHEN 3 THEN '出差用，很便携' WHEN 4 THEN '16 寸折叠，放后备箱方便' END,
      CASE i WHEN 1 THEN '家居' WHEN 2 THEN '其他' WHEN 3 THEN '家居' WHEN 4 THEN '运动' END,
      CASE i WHEN 1 THEN 'like-new' WHEN 2 THEN 'normal' WHEN 3 THEN 'like-new' WHEN 4 THEN 'normal' END,
      CASE i WHEN 1 THEN 300 WHEN 2 THEN 2000 WHEN 3 THEN 200 WHEN 4 THEN 600 END,
      '[]', CASE i WHEN 1 THEN 14 WHEN 2 THEN 30 WHEN 3 THEN 7 WHEN 4 THEN 7 END,
      'day', CASE i WHEN 1 THEN 'self_pickup' WHEN 2 THEN 'both' WHEN 3 THEN 'self_pickup' WHEN 4 THEN 'both' END,
      'online', now_ts - (i || ' days')::INTERVAL, now_ts);
  END LOOP;

  -- 美羊羊 WANTED 3条
  FOR i IN 1..3 LOOP
    INSERT INTO idle_items (user_id, tenant_id, post_type, title, description, category, price, max_duration, duration_unit, status, created_at, updated_at)
    VALUES (mei, 1, 'WANTED',
      CASE i WHEN 1 THEN '借用手推车' WHEN 2 THEN '借用缝纫机' WHEN 3 THEN '借用榨汁机' END,
      CASE i WHEN 1 THEN '搬家需要搬运大件' WHEN 2 THEN '给孩子改衣服' WHEN 3 THEN '周末做果汁派对' END,
      CASE i WHEN 1 THEN '工具' WHEN 2 THEN '家居' WHEN 3 THEN '家居' END,
      0, CASE i WHEN 1 THEN 2 WHEN 2 THEN 5 WHEN 3 THEN 1 END,
      'day', 'online', now_ts - (i || ' days')::INTERVAL, now_ts);
  END LOOP;

  -- 美羊羊 deleted 1条
  INSERT INTO idle_items (user_id, tenant_id, post_type, title, description, category, condition, price, max_duration, duration_unit, status, delist_reason, created_at, updated_at)
  VALUES (mei, 1, 'LEND', '盗版 DVD 合集', '经典电影全集 100 张', '其他', 'normal', 50, 10, 'day',
    'offline', '盗版音像制品禁止发布', now_ts - '5 days'::INTERVAL, now_ts);
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at)
  VALUES (mei, 'violation', '物品被管理员下架', '您的物品被下架，原因：盗版音像制品禁止发布',
    currval(pg_get_serial_sequence('idle_items', 'id')), FALSE, now_ts);

END;
$$;
