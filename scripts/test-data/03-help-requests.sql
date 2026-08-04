-- 03-help-requests.sql -- 求助信息 + 违规通知
DO $$
DECLARE
  xi   BIGINT := 2;
  lan  BIGINT := 3;
  fei  BIGINT := 4;
  nuan BIGINT;
  mei  BIGINT;
  uid  BIGINT;
  now_ts TIMESTAMP := NOW();
  i    INT;
BEGIN
  SELECT id INTO nuan FROM users WHERE name = '暖羊羊';
  SELECT id INTO mei  FROM users WHERE name = '美羊羊';

  FOR uid IN SELECT unnest(ARRAY[xi, lan, fei, nuan, mei]) LOOP
    FOR i IN 1..3 LOOP
      INSERT INTO help_requests (user_id, tenant_id, title, description, category, is_urgent, status, created_at, updated_at)
      VALUES (uid, 1,
        CASE WHEN uid=xi AND i=1 THEN '帮忙修水管' WHEN uid=xi AND i=2 THEN '代取快递' WHEN uid=xi AND i=3 THEN '陪老人去医院复诊'
             WHEN uid=lan AND i=1 THEN '搬家具需要帮手' WHEN uid=lan AND i=2 THEN '辅导孩子数学' WHEN uid=lan AND i=3 THEN '帮忙遛狗两天'
             WHEN uid=fei AND i=1 THEN '代购老人药品' WHEN uid=fei AND i=2 THEN '帮忙装窗帘' WHEN uid=fei AND i=3 THEN '帮忙看半天孩子'
             WHEN uid=nuan AND i=1 THEN '代收大件快递' WHEN uid=nuan AND i=2 THEN '帮忙擦窗' WHEN uid=nuan AND i=3 THEN '代取干洗衣物'
             WHEN uid=mei AND i=1 THEN '陪孕妇产检' WHEN uid=mei AND i=2 THEN '帮忙组装宜家柜子' WHEN uid=mei AND i=3 THEN '代扔大件垃圾'
        END,
        CASE WHEN uid=xi AND i=1 THEN '厨房水管漏水需要紧急处理' WHEN uid=xi AND i=2 THEN '快递在菜鸟驿站帮忙取一下' WHEN uid=xi AND i=3 THEN '老人腿脚不便需要人陪半天'
             WHEN uid=lan AND i=1 THEN '搬家需要搬沙发和冰箱至少两个人' WHEN uid=lan AND i=2 THEN '孩子三年级数学跟不上需要辅导' WHEN uid=lan AND i=3 THEN '出差两天需要帮忙遛狗早晚各一次'
             WHEN uid=fei AND i=1 THEN '老人降压药快吃完了帮忙去药房取' WHEN uid=fei AND i=2 THEN '新买的窗帘需要打孔安装' WHEN uid=fei AND i=3 THEN '临时有事出门需要帮忙看半天孩子'
             WHEN uid=nuan AND i=1 THEN '上班没法收快递需要白天在家的邻居' WHEN uid=nuan AND i=2 THEN '高层擦窗有点危险需要熟练的人' WHEN uid=nuan AND i=3 THEN '干洗店离得远顺路帮忙取一下'
             WHEN uid=mei AND i=1 THEN '怀孕7个月需要人陪去产检' WHEN uid=mei AND i=2 THEN '宜家买了PAX衣柜需要组装' WHEN uid=mei AND i=3 THEN '旧沙发需要扔到小区大件垃圾点'
        END,
        CASE WHEN uid=xi AND i=1 THEN '维修' WHEN uid=xi AND i=2 THEN '代取' WHEN uid=xi AND i=3 THEN '陪护'
             WHEN uid=lan AND i=1 THEN '搬运' WHEN uid=lan AND i=2 THEN '辅导' WHEN uid=lan AND i=3 THEN '遛宠'
             WHEN uid=fei AND i=1 THEN '代取' WHEN uid=fei AND i=2 THEN '维修' WHEN uid=fei AND i=3 THEN '陪护'
             WHEN uid=nuan AND i=1 THEN '代取' WHEN uid=nuan AND i=2 THEN '维修' WHEN uid=nuan AND i=3 THEN '代取'
             WHEN uid=mei AND i=1 THEN '陪护' WHEN uid=mei AND i=2 THEN '搬运' WHEN uid=mei AND i=3 THEN '搬运'
        END,
        (uid IN (xi, fei) AND i=1),
        'online', now_ts - (i || ' days')::INTERVAL, now_ts);
    END LOOP;

    INSERT INTO help_requests (user_id, tenant_id, title, description, category, is_urgent, status, delist_reason, created_at, updated_at)
    VALUES (uid, 1,
      CASE WHEN uid=xi THEN '帮忙代考驾照' WHEN uid=lan THEN '帮忙写论文' WHEN uid=fei THEN '代排队挂号' WHEN uid=nuan THEN '帮忙刷单' WHEN uid=mei THEN '代打卡上班' END,
      '违规求助内容', '其他', FALSE, 'offline', '代考代写代刷等违规求助不被平台允许',
      now_ts - '5 days'::INTERVAL, now_ts);

    INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at)
    VALUES (uid, 'violation', '求助被管理员下架', '您的求助因违规被下架',
      currval(pg_get_serial_sequence('help_requests', 'id')), FALSE, now_ts);
  END LOOP;

END;
$$;
