-- =============================================================================
-- C端管理页（我的发布）种子数据 — 覆盖全部 4 个模块
-- 涵盖：发布(已下架) / 审批 / 进行中 / 已完成
-- 可安全重复执行：使用 INSERT ... ON CONFLICT DO NOTHING
-- =============================================================================

-- 目标用户：痞老板 (284879cc-fb93-44ce-8971-966b41431ca7, 3栋3单元3333号)
-- 其他用户：张三(4000...11, 1栋1单元101号), 李四(4000...12, 1栋2单元101号),
--           王五(4000...13), 钱七(4000...15, 1栋2单元102号)

-- =============================================================================
-- 1. 发布 → 已下架: status='draft' 的 idle_items（用户自行下架后的草稿态）
-- =============================================================================
INSERT INTO idle_items (id, user_id, post_type, title, description, category, condition, price, images,
    max_duration, duration_unit, pickup_method, status, delist_reason, is_proxy, created_at, updated_at)
VALUES
-- 痞老板的已下架物品
(gen_random_uuid(), '284879cc-fb93-44ce-8971-966b41431ca7', 'LEND',
 '闲置台灯LED护眼', '很少用，放久了有点灰，功能正常', '家居', 'normal', 0,
 '["/images/demo/lamp.jpg"]', 7, 'day', 'self_pickup', 'draft',
 '用户自行下架', false,
 NOW() - INTERVAL '10 days', NOW() - INTERVAL '2 days'),
(gen_random_uuid(), '284879cc-fb93-44ce-8971-966b41431ca7', 'LEND',
 '闲置书架4层', '搬家后不需要了，有点重需要自取', '家居', 'worn', 0,
 '["/images/demo/shelf.jpg"]', 14, 'day', 'self_pickup', 'draft',
 '用户自行下架', false,
 NOW() - INTERVAL '15 days', NOW() - INTERVAL '5 days')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 2. 审批 → 借入审批: 痞老板物品上 status='pending' 的 borrow_requests
-- =============================================================================
INSERT INTO borrow_requests (id, idle_id, borrower_id, duration_type, duration_days, start_date, note, status, created_at, updated_at)
VALUES
-- 张三想借入痞老板的电脑
(gen_random_uuid(), '26584688-4942-4386-a010-8ff0f066b110', '40000000-0000-0000-0000-000000000011',
 'day', 1, CURRENT_DATE + INTERVAL '1 day', '临时急需处理文档，用一天就还', 'pending',
 NOW() - INTERVAL '3 hours', NOW() - INTERVAL '3 hours'),
-- 李四想借入痞老板的手机
(gen_random_uuid(), '1b9cf7da-1e03-4f74-b21a-c316f98bb79b', '40000000-0000-0000-0000-000000000012',
 'day', 3, CURRENT_DATE, '手机坏了借来应急，修好就还', 'pending',
 NOW() - INTERVAL '5 hours', NOW() - INTERVAL '5 hours'),
-- 钱七想借入痞老板的瓶子
(gen_random_uuid(), 'bcc5533e-d86a-4d90-b548-cbd4e72378d9', '40000000-0000-0000-0000-000000000015',
 'day', 5, CURRENT_DATE + INTERVAL '2 days', '临时需要装东西', 'pending',
 NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 3. 审批 → 帮助审批: 痞老板求助上 status='pending' 的 help_applications
-- =============================================================================
INSERT INTO help_applications (id, help_id, helper_id, note, status, created_at, updated_at)
VALUES
-- 王五想帮助痞老板的"帮我搬家"
(gen_random_uuid(), '5008c145-733c-4519-9925-c781ae393ab5', '40000000-0000-0000-0000-000000000013',
 '我周六有空，可以来帮忙', 'pending', NOW() - INTERVAL '4 hours', NOW() - INTERVAL '4 hours'),
-- 李四想帮助痞老板的"帮我搬家"
(gen_random_uuid(), '5008c145-733c-4519-9925-c781ae393ab5', '40000000-0000-0000-0000-000000000012',
 '我也在3栋，很方便，需要帮忙随时说', 'pending', NOW() - INTERVAL '6 hours', NOW() - INTERVAL '6 hours')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 4. 进行中 → borrow (痞老板借入他人物品): 已通过的 borrow_requests
-- =============================================================================
INSERT INTO borrow_requests (id, idle_id, borrower_id, duration_type, duration_days, start_date, note, status, created_at, updated_at)
VALUES
-- 痞老板借入张三的戴森吸尘器V8
(gen_random_uuid(), '3b0a4ed2-cbca-49e0-a888-06d3a9c1af3f', '284879cc-fb93-44ce-8971-966b41431ca7',
 'day', 3, CURRENT_DATE - INTERVAL '1 day', '周末大扫除用', 'approved',
 NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
-- 痞老板借入李四的Switch游戏卡带
(gen_random_uuid(), '454e1277-2bff-490f-99cb-5bcd78b2ca8f', '284879cc-fb93-44ce-8971-966b41431ca7',
 'day', 7, CURRENT_DATE - INTERVAL '2 days', '想试试塞尔达再决定买不买', 'approved',
 NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),
-- 痞老板借入钱七的烤箱
(gen_random_uuid(), '08dd0407-490b-4754-8340-4d660047d4d3', '284879cc-fb93-44ce-8971-966b41431ca7',
 'day', 7, CURRENT_DATE - INTERVAL '4 days', '想学烤面包', 'approved',
 NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 5. 进行中 → lend (他人借入痞老板的物品): 已通过的 borrow_requests
-- =============================================================================
INSERT INTO borrow_requests (id, idle_id, borrower_id, duration_type, duration_days, start_date, note, status, created_at, updated_at)
VALUES
-- 王五借入痞老板的电脑
(gen_random_uuid(), '26584688-4942-4386-a010-8ff0f066b110', '40000000-0000-0000-0000-000000000013',
 'day', 1, CURRENT_DATE, '急用写论文', 'approved',
 NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 6. 进行中 → helpReq (他人正在帮助痞老板): 已通过的 help_applications
-- =============================================================================
INSERT INTO help_applications (id, help_id, helper_id, note, status, created_at, updated_at)
VALUES
-- 钱七正在帮助痞老板的"帮我搬家"
(gen_random_uuid(), '5008c145-733c-4519-9925-c781ae393ab5', '40000000-0000-0000-0000-000000000015',
 '我力气大，可以帮忙搬重物', 'approved', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 7. 进行中 → helpPro (痞老板正在帮助他人): 已通过的 help_applications
-- =============================================================================
INSERT INTO help_applications (id, help_id, helper_id, note, status, created_at, updated_at)
VALUES
-- 痞老板正在帮助李四的"帮忙辅导初二数学"
(gen_random_uuid(), '1c534106-2d86-4a8d-bd71-df60ada76158', '284879cc-fb93-44ce-8971-966b41431ca7',
 '我数学系毕业的，可以帮忙辅导', 'approved', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),
-- 痞老板正在帮助张三的"帮忙遛狗2天"
(gen_random_uuid(), '60000000-0000-0000-0000-000000000002', '284879cc-fb93-44ce-8971-966b41431ca7',
 '我喜欢狗，可以帮忙遛', 'approved', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 8. 已完成 → borrow (痞老板已归还借入的物品): 已归还的 borrow_requests
-- =============================================================================
INSERT INTO borrow_requests (id, idle_id, borrower_id, duration_type, duration_days, start_date, note,
    status, return_status, return_note, is_on_time, created_at, updated_at)
VALUES
-- 痞老板已归还李四的折叠梯子
(gen_random_uuid(), '345ca43d-5ecc-4984-8bec-cccbb240a4f7', '284879cc-fb93-44ce-8971-966b41431ca7',
 'day', 3, CURRENT_DATE - INTERVAL '12 days', '安装窗帘用', 'returned',
 'on_time', '很好用，谢谢！', true,
 NOW() - INTERVAL '15 days', NOW() - INTERVAL '12 days'),
-- 痞老板已归还张三的《三体》全集（逾期）
(gen_random_uuid(), 'c27bcbdc-e1b1-4bdf-8f4b-072eb09dc86e', '284879cc-fb93-44ce-8971-966b41431ca7',
 'day', 7, CURRENT_DATE - INTERVAL '20 days', '', 'returned',
 'late', '不好意思超时了几天', false,
 NOW() - INTERVAL '27 days', NOW() - INTERVAL '18 days')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 9. 已完成 → lend (他人已归还痞老板的物品): 已归还的 borrow_requests
-- =============================================================================
INSERT INTO borrow_requests (id, idle_id, borrower_id, duration_type, duration_days, start_date, note,
    status, return_status, return_note, is_on_time, created_at, updated_at)
VALUES
-- 张三已归还痞老板的手机（按时）
(gen_random_uuid(), '1b9cf7da-1e03-4f74-b21a-c316f98bb79b', '40000000-0000-0000-0000-000000000011',
 'day', 2, CURRENT_DATE - INTERVAL '8 days', '临时急用', 'returned',
 'on_time', '很干净，谢谢', true,
 NOW() - INTERVAL '10 days', NOW() - INTERVAL '8 days'),
-- 李四已归还痞老板的瓶子（逾期且有损坏）
(gen_random_uuid(), 'bcc5533e-d86a-4d90-b548-cbd4e72378d9', '40000000-0000-0000-0000-000000000012',
 'day', 3, CURRENT_DATE - INTERVAL '6 days', '', 'returned',
 'late', '不小心磕了个小缺口，抱歉', false,
 NOW() - INTERVAL '9 days', NOW() - INTERVAL '6 days')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 10. 已完成 → helpReq (对痞老板的帮助已完成): 已完成的 help_applications
-- =============================================================================
INSERT INTO help_applications (id, help_id, helper_id, note, status, completed_at, created_at, updated_at)
VALUES
-- 张三已完成对痞老板"帮我搬家"的帮助
(gen_random_uuid(), '5008c145-733c-4519-9925-c781ae393ab5', '40000000-0000-0000-0000-000000000011',
 '搬完了，挺顺利的', 'completed', NOW() - INTERVAL '7 days',
 NOW() - INTERVAL '10 days', NOW() - INTERVAL '7 days')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 11. 已完成 → helpPro (痞老板已完成对他人的帮助): 已完成的 help_applications
-- =============================================================================
INSERT INTO help_applications (id, help_id, helper_id, note, status, completed_at, created_at, updated_at)
VALUES
-- 痞老板已完成对张三"求代取快递3个"的帮助
(gen_random_uuid(), 'ea6cdd35-6e58-4ee2-ba84-ca2f5f39d63b', '284879cc-fb93-44ce-8971-966b41431ca7',
 '3个快递都取到了，放在门口', 'completed', NOW() - INTERVAL '2 days',
 NOW() - INTERVAL '3 days', NOW() - INTERVAL '2 days'),
-- 痞老板已完成对王五"急！求帮做晚饭"的帮助
(gen_random_uuid(), '43114a67-c34b-4142-8030-35d48b219c63', '284879cc-fb93-44ce-8971-966b41431ca7',
 '做了两菜一汤，老人吃得很开心', 'completed', NOW() - INTERVAL '4 days',
 NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 12. 评价数据 — 让已完成项能展示评价信息
-- =============================================================================
INSERT INTO ratings (id, borrow_id, help_application_id, from_user_id, to_user_id, score, dimension_scores, created_at)
SELECT gen_random_uuid(), br.id, NULL, '284879cc-fb93-44ce-8971-966b41431ca7', '40000000-0000-0000-0000-000000000012',
    5, '{"timeliness":5,"condition":5,"communication":5}', NOW()
FROM borrow_requests br
WHERE br.borrower_id = '284879cc-fb93-44ce-8971-966b41431ca7'
  AND br.idle_id = '345ca43d-5ecc-4984-8bec-cccbb240a4f7'
  AND br.status = 'returned'
  AND NOT EXISTS (SELECT 1 FROM ratings r WHERE r.borrow_id = br.id AND r.from_user_id = '284879cc-fb93-44ce-8971-966b41431ca7')
LIMIT 1;

INSERT INTO ratings (id, borrow_id, help_application_id, from_user_id, to_user_id, score, dimension_scores, created_at)
SELECT gen_random_uuid(), br.id, NULL, '40000000-0000-0000-0000-000000000012', '284879cc-fb93-44ce-8971-966b41431ca7',
    5, '{"timeliness":5,"condition":5,"communication":4}', NOW()
FROM borrow_requests br
WHERE br.borrower_id = '284879cc-fb93-44ce-8971-966b41431ca7'
  AND br.idle_id = '345ca43d-5ecc-4984-8bec-cccbb240a4f7'
  AND br.status = 'returned'
  AND NOT EXISTS (SELECT 1 FROM ratings r WHERE r.borrow_id = br.id AND r.from_user_id = '40000000-0000-0000-0000-000000000012')
LIMIT 1;

-- 逾期借用的评价（《三体》全集）
INSERT INTO ratings (id, borrow_id, help_application_id, from_user_id, to_user_id, score, dimension_scores, created_at)
SELECT gen_random_uuid(), br.id, NULL, '284879cc-fb93-44ce-8971-966b41431ca7', '40000000-0000-0000-0000-000000000011',
    3, '{"timeliness":2,"condition":4,"communication":3}', NOW()
FROM borrow_requests br
WHERE br.borrower_id = '284879cc-fb93-44ce-8971-966b41431ca7'
  AND br.idle_id = 'c27bcbdc-e1b1-4bdf-8f4b-072eb09dc86e'
  AND br.status = 'returned'
  AND NOT EXISTS (SELECT 1 FROM ratings r WHERE r.borrow_id = br.id AND r.from_user_id = '284879cc-fb93-44ce-8971-966b41431ca7')
LIMIT 1;

-- 借出归还的评价（张三归还痞老板的手机）
INSERT INTO ratings (id, borrow_id, help_application_id, from_user_id, to_user_id, score, dimension_scores, created_at)
SELECT gen_random_uuid(), br.id, NULL, '284879cc-fb93-44ce-8971-966b41431ca7', '40000000-0000-0000-0000-000000000011',
    4, '{"timeliness":4,"condition":4,"communication":5}', NOW()
FROM borrow_requests br
WHERE br.idle_id = '1b9cf7da-1e03-4f74-b21a-c316f98bb79b'
  AND br.borrower_id = '40000000-0000-0000-0000-000000000011'
  AND br.status = 'returned'
  AND NOT EXISTS (SELECT 1 FROM ratings r WHERE r.borrow_id = br.id AND r.from_user_id = '284879cc-fb93-44ce-8971-966b41431ca7')
LIMIT 1;

INSERT INTO ratings (id, borrow_id, help_application_id, from_user_id, to_user_id, score, dimension_scores, created_at)
SELECT gen_random_uuid(), br.id, NULL, '40000000-0000-0000-0000-000000000011', '284879cc-fb93-44ce-8971-966b41431ca7',
    5, '{"timeliness":5,"condition":5,"communication":5}', NOW()
FROM borrow_requests br
WHERE br.idle_id = '1b9cf7da-1e03-4f74-b21a-c316f98bb79b'
  AND br.borrower_id = '40000000-0000-0000-0000-000000000011'
  AND br.status = 'returned'
  AND NOT EXISTS (SELECT 1 FROM ratings r WHERE r.borrow_id = br.id AND r.from_user_id = '40000000-0000-0000-0000-000000000011')
LIMIT 1;

-- 借出归还的评价（李四归还痞老板的瓶子，有损坏）
INSERT INTO ratings (id, borrow_id, help_application_id, from_user_id, to_user_id, score, dimension_scores, created_at)
SELECT gen_random_uuid(), br.id, NULL, '284879cc-fb93-44ce-8971-966b41431ca7', '40000000-0000-0000-0000-000000000012',
    2, '{"timeliness":3,"condition":1,"communication":2}', NOW()
FROM borrow_requests br
WHERE br.idle_id = 'bcc5533e-d86a-4d90-b548-cbd4e72378d9'
  AND br.borrower_id = '40000000-0000-0000-0000-000000000012'
  AND br.status = 'returned'
  AND NOT EXISTS (SELECT 1 FROM ratings r WHERE r.borrow_id = br.id AND r.from_user_id = '284879cc-fb93-44ce-8971-966b41431ca7')
LIMIT 1;

-- 帮助申请（已完成）的评价
INSERT INTO ratings (id, borrow_id, help_application_id, from_user_id, to_user_id, score, dimension_scores, created_at)
SELECT gen_random_uuid(), NULL, ha.id, '284879cc-fb93-44ce-8971-966b41431ca7', '40000000-0000-0000-0000-000000000011',
    5, '{"helpfulness":5,"punctuality":5,"attitude":5}', NOW()
FROM help_applications ha
WHERE ha.help_id = 'ea6cdd35-6e58-4ee2-ba84-ca2f5f39d63b'
  AND ha.helper_id = '284879cc-fb93-44ce-8971-966b41431ca7'
  AND ha.status = 'completed'
  AND NOT EXISTS (SELECT 1 FROM ratings r WHERE r.help_application_id = ha.id AND r.from_user_id = '284879cc-fb93-44ce-8971-966b41431ca7')
LIMIT 1;

INSERT INTO ratings (id, borrow_id, help_application_id, from_user_id, to_user_id, score, dimension_scores, created_at)
SELECT gen_random_uuid(), NULL, ha.id, '40000000-0000-0000-0000-000000000011', '284879cc-fb93-44ce-8971-966b41431ca7',
    5, '{"helpfulness":5,"punctuality":5,"attitude":5}', NOW()
FROM help_applications ha
WHERE ha.help_id = 'ea6cdd35-6e58-4ee2-ba84-ca2f5f39d63b'
  AND ha.helper_id = '284879cc-fb93-44ce-8971-966b41431ca7'
  AND ha.status = 'completed'
  AND NOT EXISTS (SELECT 1 FROM ratings r WHERE r.help_application_id = ha.id AND r.from_user_id = '40000000-0000-0000-0000-000000000011')
LIMIT 1;

INSERT INTO ratings (id, borrow_id, help_application_id, from_user_id, to_user_id, score, dimension_scores, created_at)
SELECT gen_random_uuid(), NULL, ha.id, '284879cc-fb93-44ce-8971-966b41431ca7', '40000000-0000-0000-0000-000000000013',
    4, '{"helpfulness":4,"punctuality":5,"attitude":3}', NOW()
FROM help_applications ha
WHERE ha.help_id = '43114a67-c34b-4142-8030-35d48b219c63'
  AND ha.helper_id = '284879cc-fb93-44ce-8971-966b41431ca7'
  AND ha.status = 'completed'
  AND NOT EXISTS (SELECT 1 FROM ratings r WHERE r.help_application_id = ha.id AND r.from_user_id = '284879cc-fb93-44ce-8971-966b41431ca7')
LIMIT 1;

-- =============================================================================
-- 13. 确保痞老板的"帮我搬家"求助有未来的截止时间（用于进行中展示）
-- =============================================================================
UPDATE help_requests
SET time_start = CURRENT_TIMESTAMP + INTERVAL '2 days',
    time_end = CURRENT_TIMESTAMP + INTERVAL '3 days'
WHERE id = '5008c145-733c-4519-9925-c781ae393ab5'
  AND (time_start IS NULL OR time_start < NOW());
