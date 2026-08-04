-- 05-help-applications.sql -- 帮忙申请 pending/accepted/completed/rejected + 通知 + 评分
DO $$
DECLARE
  xi   BIGINT := 2;
  lan  BIGINT := 3;
  fei  BIGINT := 4;
  nuan BIGINT;
  mei  BIGINT;
  uid  BIGINT;
  ha_id BIGINT;
  now_ts TIMESTAMP := NOW();
  i    INT;
BEGIN
  SELECT id INTO nuan FROM users WHERE name = '暖羊羊';
  SELECT id INTO mei  FROM users WHERE name = '美羊羊';

  -- ===== pending 12条 =====
  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, fei, '我是水管工可以帮你修', 'pending', now_ts - '1 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = xi AND title = '帮忙修水管' AND status = 'online' LIMIT 1;
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at) VALUES (xi, 'help_application', '新的帮助申请', '有人想要帮助你', currval(pg_get_serial_sequence('help_applications', 'id')), FALSE, now_ts);

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, nuan, '我顺路可以帮你取', 'pending', now_ts - '1 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = xi AND title = '代取快递' AND status = 'online' LIMIT 1;

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, mei, '我有电钻可以帮忙', 'pending', now_ts - '1 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = fei AND title = '帮忙装窗帘' AND status = 'online' LIMIT 1;
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at) VALUES (fei, 'help_application', '新的帮助申请', '有人想要帮助你', currval(pg_get_serial_sequence('help_applications', 'id')), FALSE, now_ts);

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, xi, '我家也有孩子可以一起玩', 'pending', now_ts - '1 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = fei AND title = '帮忙看半天孩子' AND status = 'online' LIMIT 1;

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, lan, '我有擦窗工具', 'pending', now_ts - '1 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = nuan AND title = '帮忙擦窗' AND status = 'online' LIMIT 1;
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at) VALUES (nuan, 'help_application', '新的帮助申请', '有人想要帮助你', currval(pg_get_serial_sequence('help_applications', 'id')), FALSE, now_ts);

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, fei, '我白天在家可以代收', 'pending', now_ts - '1 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = nuan AND title = '代收大件快递' AND status = 'online' LIMIT 1;

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, xi, '我组装宜家家具很有经验', 'pending', now_ts - '1 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = mei AND title = '帮忙组装宜家柜子' AND status = 'online' LIMIT 1;
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at) VALUES (mei, 'help_application', '新的帮助申请', '有人想要帮助你', currval(pg_get_serial_sequence('help_applications', 'id')), FALSE, now_ts);

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, nuan, '我老婆也在孕期有经验', 'pending', now_ts - '1 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = mei AND title = '陪孕妇产检' AND status = 'online' LIMIT 1;

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, lan, '我刚好要去医院', 'pending', now_ts - '1 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = fei AND title = '代购老人药品' AND status = 'online' LIMIT 1;
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at) VALUES (fei, 'help_application', '新的帮助申请', '有人想要帮助你', currval(pg_get_serial_sequence('help_applications', 'id')), FALSE, now_ts);

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, mei, '我家有小推车可以帮忙', 'pending', now_ts - '1 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = lan AND title = '搬家具需要帮手' AND status = 'online' LIMIT 1;

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, fei, '我数学专业毕业的', 'pending', now_ts - '1 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = lan AND title = '辅导孩子数学' AND status = 'online' LIMIT 1;
  INSERT INTO notifications (user_id, type, title, content, related_id, is_read, created_at) VALUES (lan, 'help_application', '新的帮助申请', '有人想要帮助你', currval(pg_get_serial_sequence('help_applications', 'id')), FALSE, now_ts);

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, xi, '我下班顺路经过干洗店', 'pending', now_ts - '1 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = nuan AND title = '代取干洗衣物' AND status = 'online' LIMIT 1;

  -- ===== accepted 12条 =====
  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, fei, '可以帮忙修理', 'accepted', now_ts - '5 days'::INTERVAL, now_ts - '4 days'::INTERVAL FROM help_requests WHERE user_id = xi AND title = '陪老人去医院复诊' AND status = 'online' LIMIT 1;
  UPDATE help_requests SET status = 'helping' WHERE id = (SELECT help_id FROM help_applications WHERE id = currval(pg_get_serial_sequence('help_applications', 'id')));

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, lan, '我帮你搬', 'accepted', now_ts - '4 days'::INTERVAL, now_ts - '3 days'::INTERVAL FROM help_requests WHERE user_id = xi AND title = '帮忙修水管' AND status = 'online' LIMIT 1;
  UPDATE help_requests SET status = 'helping' WHERE id = (SELECT help_id FROM help_applications WHERE id = currval(pg_get_serial_sequence('help_applications', 'id')));

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, nuan, '我来帮你辅导', 'accepted', now_ts - '3 days'::INTERVAL, now_ts - '2 days'::INTERVAL FROM help_requests WHERE user_id = lan AND title = '辅导孩子数学' AND status = 'online' LIMIT 1;
  UPDATE help_requests SET status = 'helping' WHERE id = (SELECT help_id FROM help_applications WHERE id = currval(pg_get_serial_sequence('help_applications', 'id')));

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, mei, '我遛狗很熟练', 'accepted', now_ts - '2 days'::INTERVAL, now_ts - '1 days'::INTERVAL FROM help_requests WHERE user_id = lan AND title = '帮忙遛狗两天' AND status = 'online' LIMIT 1;
  UPDATE help_requests SET status = 'helping' WHERE id = (SELECT help_id FROM help_applications WHERE id = currval(pg_get_serial_sequence('help_applications', 'id')));

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, xi, '我帮你买药', 'accepted', now_ts - '2 days'::INTERVAL, now_ts - '1 days'::INTERVAL FROM help_requests WHERE user_id = fei AND title = '代购老人药品' AND status = 'online' LIMIT 1;
  UPDATE help_requests SET status = 'helping' WHERE id = (SELECT help_id FROM help_applications WHERE id = currval(pg_get_serial_sequence('help_applications', 'id')));

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, lan, '我帮你装', 'accepted', now_ts - '2 days'::INTERVAL, now_ts - '1 days'::INTERVAL FROM help_requests WHERE user_id = fei AND title = '帮忙装窗帘' AND status = 'online' LIMIT 1;
  UPDATE help_requests SET status = 'helping' WHERE id = (SELECT help_id FROM help_applications WHERE id = currval(pg_get_serial_sequence('help_applications', 'id')));

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, fei, '我帮你擦', 'accepted', now_ts - '1 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = nuan AND title = '帮忙擦窗' AND status = 'online' LIMIT 1;
  UPDATE help_requests SET status = 'helping' WHERE id = (SELECT help_id FROM help_applications WHERE id = currval(pg_get_serial_sequence('help_applications', 'id')));

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, mei, '我来帮忙看孩子', 'accepted', now_ts - '1 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = fei AND title = '帮忙看半天孩子' AND status = 'online' LIMIT 1;
  UPDATE help_requests SET status = 'helping' WHERE id = (SELECT help_id FROM help_applications WHERE id = currval(pg_get_serial_sequence('help_applications', 'id')));

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, nuan, '我来帮搬', 'accepted', now_ts - '1 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = lan AND title = '搬家具需要帮手' AND status = 'online' LIMIT 1;
  UPDATE help_requests SET status = 'helping' WHERE id = (SELECT help_id FROM help_applications WHERE id = currval(pg_get_serial_sequence('help_applications', 'id')));

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, xi, '我帮你扔', 'accepted', now_ts - '1 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = mei AND title = '代扔大件垃圾' AND status = 'online' LIMIT 1;
  UPDATE help_requests SET status = 'helping' WHERE id = (SELECT help_id FROM help_applications WHERE id = currval(pg_get_serial_sequence('help_applications', 'id')));

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, lan, '我帮你收', 'accepted', now_ts - '1 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = nuan AND title = '代收大件快递' AND status = 'online' LIMIT 1;
  UPDATE help_requests SET status = 'helping' WHERE id = (SELECT help_id FROM help_applications WHERE id = currval(pg_get_serial_sequence('help_applications', 'id')));

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, fei, '我来帮你组装', 'accepted', now_ts - '1 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = mei AND title = '帮忙组装宜家柜子' AND status = 'online' LIMIT 1;
  UPDATE help_requests SET status = 'helping' WHERE id = (SELECT help_id FROM help_applications WHERE id = currval(pg_get_serial_sequence('help_applications', 'id')));

  -- ===== rejected 14条 =====
  FOR uid IN SELECT unnest(ARRAY[xi, lan, fei, nuan, mei]) LOOP
    FOR i IN 1..2 LOOP
      INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
      SELECT id,
        CASE WHEN uid=xi AND i=1 THEN lan WHEN uid=xi AND i=2 THEN mei
             WHEN uid=lan AND i=1 THEN fei WHEN uid=lan AND i=2 THEN nuan
             WHEN uid=fei AND i=1 THEN xi WHEN uid=fei AND i=2 THEN mei
             WHEN uid=nuan AND i=1 THEN lan WHEN uid=nuan AND i=2 THEN fei
             WHEN uid=mei AND i=1 THEN xi WHEN uid=mei AND i=2 THEN lan END,
        '暂时不需要了，谢谢', 'rejected',
        now_ts - ((i + 2) * 3 || ' days')::INTERVAL, now_ts
      FROM help_requests WHERE user_id = uid AND status = 'online' LIMIT 1;
    END LOOP;
  END LOOP;

  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, fei, '不凑巧没空', 'rejected', now_ts - '20 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = nuan AND title = '代取干洗衣物' AND status = 'online' LIMIT 1;
  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, mei, '已经找到别人了', 'rejected', now_ts - '15 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = xi AND title = '代取快递' AND status = 'online' LIMIT 1;
  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, xi, '最近太忙了', 'rejected', now_ts - '12 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = fei AND title = '帮忙看半天孩子' AND status = 'online' LIMIT 1;
  INSERT INTO help_applications (help_id, helper_id, note, status, created_at, updated_at)
  SELECT id, nuan, '已经解决了', 'rejected', now_ts - '8 days'::INTERVAL, now_ts FROM help_requests WHERE user_id = lan AND title = '搬家具需要帮手' AND status = 'online' LIMIT 1;

  -- ===== completed 13条 + 评分 =====
  INSERT INTO help_applications (help_id, helper_id, note, status, completed_at, created_at, updated_at)
  SELECT id, fei, '修好了，换了新垫圈', 'completed', now_ts - '10 days'::INTERVAL, now_ts - '12 days'::INTERVAL, now_ts - '10 days'::INTERVAL FROM help_requests WHERE user_id = xi AND title = '帮忙修水管' AND status = 'online' LIMIT 1;
  ha_id := currval(pg_get_serial_sequence('help_applications', 'id'));
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, xi, fei, 5, '沸羊羊手艺很好，水管完全修好了', now_ts - '10 days'::INTERVAL);
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, fei, xi, 5, '喜羊羊很配合，主动帮我递工具', now_ts - '9 days'::INTERVAL);

  INSERT INTO help_applications (help_id, helper_id, note, status, completed_at, created_at, updated_at)
  SELECT id, lan, '搬家完成', 'completed', now_ts - '15 days'::INTERVAL, now_ts - '17 days'::INTERVAL, now_ts - '15 days'::INTERVAL FROM help_requests WHERE user_id = xi AND title = '搬家具需要帮手' AND status = 'online' LIMIT 1;
  ha_id := currval(pg_get_serial_sequence('help_applications', 'id'));
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, xi, lan, 5, '懒羊羊力气大，搬家效率很高', now_ts - '15 days'::INTERVAL);
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, lan, xi, 4, '喜羊羊提前收拾好了但忘了标记箱子', now_ts - '14 days'::INTERVAL);

  INSERT INTO help_applications (help_id, helper_id, note, status, completed_at, created_at, updated_at)
  SELECT id, nuan, '辅导完孩子考了A', 'completed', now_ts - '20 days'::INTERVAL, now_ts - '25 days'::INTERVAL, now_ts - '20 days'::INTERVAL FROM help_requests WHERE user_id = lan AND title = '辅导孩子数学' AND status = 'online' LIMIT 1;
  ha_id := currval(pg_get_serial_sequence('help_applications', 'id'));
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, lan, nuan, 5, '暖羊羊辅导非常耐心，孩子进步很大', now_ts - '20 days'::INTERVAL);
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, nuan, lan, 5, '懒羊羊的孩子很聪明一点就通', now_ts - '19 days'::INTERVAL);

  INSERT INTO help_applications (help_id, helper_id, note, status, completed_at, created_at, updated_at)
  SELECT id, mei, '狗狗遛得很开心', 'completed', now_ts - '12 days'::INTERVAL, now_ts - '14 days'::INTERVAL, now_ts - '12 days'::INTERVAL FROM help_requests WHERE user_id = lan AND title = '帮忙遛狗两天' AND status = 'online' LIMIT 1;
  ha_id := currval(pg_get_serial_sequence('help_applications', 'id'));
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, lan, mei, 5, '美羊羊对狗很好，回来还带了小零食', now_ts - '12 days'::INTERVAL);
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, mei, lan, 5, '狗狗很乖，遛起来也轻松', now_ts - '11 days'::INTERVAL);

  INSERT INTO help_applications (help_id, helper_id, note, status, completed_at, created_at, updated_at)
  SELECT id, xi, '买好了', 'completed', now_ts - '18 days'::INTERVAL, now_ts - '20 days'::INTERVAL, now_ts - '18 days'::INTERVAL FROM help_requests WHERE user_id = fei AND title = '代购老人药品' AND status = 'online' LIMIT 1;
  ha_id := currval(pg_get_serial_sequence('help_applications', 'id'));
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, fei, xi, 5, '喜羊羊帮忙跑了好几家药店才买到', now_ts - '18 days'::INTERVAL);
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, xi, fei, 5, '沸羊羊家的老人很和蔼，能帮到忙很高兴', now_ts - '17 days'::INTERVAL);

  INSERT INTO help_applications (help_id, helper_id, note, status, completed_at, created_at, updated_at)
  SELECT id, lan, '窗帘装好了', 'completed', now_ts - '13 days'::INTERVAL, now_ts - '15 days'::INTERVAL, now_ts - '13 days'::INTERVAL FROM help_requests WHERE user_id = fei AND title = '帮忙装窗帘' AND status = 'online' LIMIT 1;
  ha_id := currval(pg_get_serial_sequence('help_applications', 'id'));
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, fei, lan, 5, '懒羊羊装的窗帘很整齐，导轨也很顺畅', now_ts - '13 days'::INTERVAL);
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, lan, fei, 4, '沸羊羊的工具不太全还好最后装好了', now_ts - '12 days'::INTERVAL);

  INSERT INTO help_applications (help_id, helper_id, note, status, completed_at, created_at, updated_at)
  SELECT id, fei, '窗户擦干净了', 'completed', now_ts - '8 days'::INTERVAL, now_ts - '10 days'::INTERVAL, now_ts - '8 days'::INTERVAL FROM help_requests WHERE user_id = nuan AND title = '帮忙擦窗' AND status = 'online' LIMIT 1;
  ha_id := currval(pg_get_serial_sequence('help_applications', 'id'));
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, nuan, fei, 5, '沸羊羊擦窗特别认真连窗框都擦了', now_ts - '8 days'::INTERVAL);
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, fei, nuan, 5, '暖羊羊家窗户不多很快就搞定了', now_ts - '7 days'::INTERVAL);

  INSERT INTO help_applications (help_id, helper_id, note, status, completed_at, created_at, updated_at)
  SELECT id, nuan, '快递已取回', 'completed', now_ts - '5 days'::INTERVAL, now_ts - '7 days'::INTERVAL, now_ts - '5 days'::INTERVAL FROM help_requests WHERE user_id = xi AND title = '代取快递' AND status = 'online' LIMIT 1;
  ha_id := currval(pg_get_serial_sequence('help_applications', 'id'));
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, xi, nuan, 5, '暖羊羊很细心，快递包裹保护得很好', now_ts - '5 days'::INTERVAL);
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, nuan, xi, 5, '喜羊羊提前设好了临时码很方便', now_ts - '4 days'::INTERVAL);

  INSERT INTO help_applications (help_id, helper_id, note, status, completed_at, created_at, updated_at)
  SELECT id, mei, '产检顺利', 'completed', now_ts - '6 days'::INTERVAL, now_ts - '8 days'::INTERVAL, now_ts - '6 days'::INTERVAL FROM help_requests WHERE user_id = mei AND title = '陪孕妇产检' AND status = 'online' LIMIT 1;
  ha_id := currval(pg_get_serial_sequence('help_applications', 'id'));
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, mei, nuan, 5, '暖羊羊陪我产检很耐心还帮忙拿东西', now_ts - '6 days'::INTERVAL);

  INSERT INTO help_applications (help_id, helper_id, note, status, completed_at, created_at, updated_at)
  SELECT id, xi, '垃圾已扔', 'completed', now_ts - '3 days'::INTERVAL, now_ts - '5 days'::INTERVAL, now_ts - '3 days'::INTERVAL FROM help_requests WHERE user_id = mei AND title = '代扔大件垃圾' AND status = 'online' LIMIT 1;
  ha_id := currval(pg_get_serial_sequence('help_applications', 'id'));
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, mei, xi, 5, '喜羊羊帮忙联系了清运车效率很高', now_ts - '3 days'::INTERVAL);

  INSERT INTO help_applications (help_id, helper_id, note, status, completed_at, created_at, updated_at)
  SELECT id, lan, '组装好了很结实', 'completed', now_ts - '4 days'::INTERVAL, now_ts - '6 days'::INTERVAL, now_ts - '4 days'::INTERVAL FROM help_requests WHERE user_id = mei AND title = '帮忙组装宜家柜子' AND status = 'online' LIMIT 1;
  ha_id := currval(pg_get_serial_sequence('help_applications', 'id'));
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, mei, lan, 5, '懒羊羊手很巧柜子装得比说明书还标准', now_ts - '4 days'::INTERVAL);
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, lan, mei, 5, '美羊羊准备的工具很齐全组装很顺', now_ts - '3 days'::INTERVAL);

  INSERT INTO help_applications (help_id, helper_id, note, status, completed_at, created_at, updated_at)
  SELECT id, fei, '送医顺利', 'completed', now_ts - '2 days'::INTERVAL, now_ts - '4 days'::INTERVAL, now_ts - '2 days'::INTERVAL FROM help_requests WHERE user_id = xi AND title = '陪老人去医院复诊' AND status = 'online' LIMIT 1;
  ha_id := currval(pg_get_serial_sequence('help_applications', 'id'));
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, xi, fei, 5, '沸羊羊陪老人复诊非常细心感激不尽', now_ts - '2 days'::INTERVAL);
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, fei, xi, 5, '喜羊羊家的老人很健谈陪诊过程很愉快', now_ts - '1 days'::INTERVAL);

  INSERT INTO help_applications (help_id, helper_id, note, status, completed_at, created_at, updated_at)
  SELECT id, xi, '帮忙看了一下午', 'completed', now_ts - '1 days'::INTERVAL, now_ts - '3 days'::INTERVAL, now_ts - '1 days'::INTERVAL FROM help_requests WHERE user_id = fei AND title = '帮忙看半天孩子' AND status = 'online' LIMIT 1;
  ha_id := currval(pg_get_serial_sequence('help_applications', 'id'));
  INSERT INTO ratings (help_application_id, from_user_id, to_user_id, score, feedback, created_at) VALUES (ha_id, fei, xi, 5, '喜羊羊带孩子很有经验孩子玩得很开心', now_ts - '1 days'::INTERVAL);

END;
$$;
