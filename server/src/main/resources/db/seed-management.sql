-- =============================================================================
-- Seed data for C端 management page (my-posts) — all 4 modules
-- Covers: 发布(已下架) / 审批 / 进行中 / 已完成
-- Safe to re-run: uses INSERT ... ON CONFLICT DO NOTHING
-- =============================================================================

-- Target user: 痞老板 (284879cc-fb93-44ce-8971-966b41431ca7, 3栋3单元3333号)
-- Other users: 张三(4000...11, 1栋1单元101号), 李四(4000...12, 1栋2单元101号),
--              王五(4000...13), 钱七(4000...15, 1栋2单元102号)

-- =============================================================================
-- 1. 发布 → 已下架: idle_items with status='offline'
-- =============================================================================
INSERT INTO idle_items (id, user_id, post_type, title, description, category, condition, price, images,
    max_duration, duration_unit, pickup_method, status, delist_reason, is_proxy, created_at, updated_at)
VALUES
-- 痞老板's delisted items
(gen_random_uuid(), '284879cc-fb93-44ce-8971-966b41431ca7', 'LEND',
 '闲置台灯LED护眼', '很少用，放久了有点灰，功能正常', '家居', 'normal', 0,
 '["/images/demo/lamp.jpg"]', 7, 'day', 'self_pickup', 'offline',
 'cancelled', false,
 NOW() - INTERVAL '10 days', NOW() - INTERVAL '2 days'),
(gen_random_uuid(), '284879cc-fb93-44ce-8971-966b41431ca7', 'LEND',
 '闲置书架4层', '搬家后不需要了，有点重需要自取', '家居', 'worn', 0,
 '["/images/demo/shelf.jpg"]', 14, 'day', 'self_pickup', 'offline',
 'cancelled', false,
 NOW() - INTERVAL '15 days', NOW() - INTERVAL '5 days')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 2. 审批 → 借入审批: borrow_requests with status='pending' on 痞老板's items
-- =============================================================================
INSERT INTO borrow_requests (id, idle_id, borrower_id, duration_type, duration_days, start_date, note, status, created_at, updated_at)
VALUES
-- 张三 wants to borrow 痞老板's 电脑
(gen_random_uuid(), '26584688-4942-4386-a010-8ff0f066b110', '40000000-0000-0000-0000-000000000011',
 'day', 1, CURRENT_DATE + INTERVAL '1 day', '临时急需处理文档，用一天就还', 'pending',
 NOW() - INTERVAL '3 hours', NOW() - INTERVAL '3 hours'),
-- 李四 wants to borrow 痞老板's 手机
(gen_random_uuid(), '1b9cf7da-1e03-4f74-b21a-c316f98bb79b', '40000000-0000-0000-0000-000000000012',
 'day', 3, CURRENT_DATE, '手机坏了借来应急，修好就还', 'pending',
 NOW() - INTERVAL '5 hours', NOW() - INTERVAL '5 hours'),
-- 钱七 wants to borrow 痞老板's 瓶子
(gen_random_uuid(), 'bcc5533e-d86a-4d90-b548-cbd4e72378d9', '40000000-0000-0000-0000-000000000015',
 'day', 5, CURRENT_DATE + INTERVAL '2 days', '临时需要装东西', 'pending',
 NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 3. 审批 → 帮助审批: help_applications with status='pending' on 痞老板's help request
-- =============================================================================
INSERT INTO help_applications (id, help_id, helper_id, note, status, created_at, updated_at)
VALUES
-- 王五 wants to help 痞老板's "帮我搬家"
(gen_random_uuid(), '5008c145-733c-4519-9925-c781ae393ab5', '40000000-0000-0000-0000-000000000013',
 '我周六有空，可以来帮忙', 'pending', NOW() - INTERVAL '4 hours', NOW() - INTERVAL '4 hours'),
-- 李四 wants to help 痞老板's "帮我搬家"
(gen_random_uuid(), '5008c145-733c-4519-9925-c781ae393ab5', '40000000-0000-0000-0000-000000000012',
 '我也在3栋，很方便，需要帮忙随时说', 'pending', NOW() - INTERVAL '6 hours', NOW() - INTERVAL '6 hours')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 4. 进行中 → borrow (痞老板 borrowing others' items): approved borrow_requests
-- =============================================================================
INSERT INTO borrow_requests (id, idle_id, borrower_id, duration_type, duration_days, start_date, note, status, created_at, updated_at)
VALUES
-- 痞老板 borrows 张三's 戴森吸尘器V8
(gen_random_uuid(), '3b0a4ed2-cbca-49e0-a888-06d3a9c1af3f', '284879cc-fb93-44ce-8971-966b41431ca7',
 'day', 3, CURRENT_DATE - INTERVAL '1 day', '周末大扫除用', 'approved',
 NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
-- 痞老板 borrows 李四's Switch游戏卡带
(gen_random_uuid(), '454e1277-2bff-490f-99cb-5bcd78b2ca8f', '284879cc-fb93-44ce-8971-966b41431ca7',
 'day', 7, CURRENT_DATE - INTERVAL '2 days', '想试试塞尔达再决定买不买', 'approved',
 NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),
-- 痞老板 borrows 钱七's 烤箱
(gen_random_uuid(), '08dd0407-490b-4754-8340-4d660047d4d3', '284879cc-fb93-44ce-8971-966b41431ca7',
 'day', 7, CURRENT_DATE - INTERVAL '4 days', '想学烤面包', 'approved',
 NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 5. 进行中 → lend (others borrowing 痞老板's items): approved borrow_requests
-- =============================================================================
INSERT INTO borrow_requests (id, idle_id, borrower_id, duration_type, duration_days, start_date, note, status, created_at, updated_at)
VALUES
-- 王五 borrows 痞老板's 电脑
(gen_random_uuid(), '26584688-4942-4386-a010-8ff0f066b110', '40000000-0000-0000-0000-000000000013',
 'day', 1, CURRENT_DATE, '急用写论文', 'approved',
 NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 6. 进行中 → helpReq (others helping 痞老板): approved help_applications
-- =============================================================================
INSERT INTO help_applications (id, help_id, helper_id, note, status, created_at, updated_at)
VALUES
-- 钱七 helping 痞老板's "帮我搬家"
(gen_random_uuid(), '5008c145-733c-4519-9925-c781ae393ab5', '40000000-0000-0000-0000-000000000015',
 '我力气大，可以帮忙搬重物', 'approved', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 7. 进行中 → helpPro (痞老板 helping others): approved help_applications
-- =============================================================================
INSERT INTO help_applications (id, help_id, helper_id, note, status, created_at, updated_at)
VALUES
-- 痞老板 helping 李四's "帮忙辅导初二数学"
(gen_random_uuid(), '1c534106-2d86-4a8d-bd71-df60ada76158', '284879cc-fb93-44ce-8971-966b41431ca7',
 '我数学系毕业的，可以帮忙辅导', 'approved', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),
-- 痞老板 helping 张三's "帮忙遛狗2天"
(gen_random_uuid(), '60000000-0000-0000-0000-000000000002', '284879cc-fb93-44ce-8971-966b41431ca7',
 '我喜欢狗，可以帮忙遛', 'approved', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 8. 已完成 → borrow (痞老板 returned borrowed items): returned borrow_requests
-- =============================================================================
INSERT INTO borrow_requests (id, idle_id, borrower_id, duration_type, duration_days, start_date, note,
    status, return_status, return_note, is_on_time, created_at, updated_at)
VALUES
-- 痞老板 returned 李四's 折叠梯子
(gen_random_uuid(), '345ca43d-5ecc-4984-8bec-cccbb240a4f7', '284879cc-fb93-44ce-8971-966b41431ca7',
 'day', 3, CURRENT_DATE - INTERVAL '12 days', '安装窗帘用', 'returned',
 'on_time', '很好用，谢谢！', true,
 NOW() - INTERVAL '15 days', NOW() - INTERVAL '12 days'),
-- 痞老板 returned 张三's 《三体》全集 (overdue)
(gen_random_uuid(), 'c27bcbdc-e1b1-4bdf-8f4b-072eb09dc86e', '284879cc-fb93-44ce-8971-966b41431ca7',
 'day', 7, CURRENT_DATE - INTERVAL '20 days', '', 'returned',
 'late', '不好意思超时了几天', false,
 NOW() - INTERVAL '27 days', NOW() - INTERVAL '18 days')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 9. 已完成 → lend (others returned 痞老板's items): returned borrow_requests
-- =============================================================================
INSERT INTO borrow_requests (id, idle_id, borrower_id, duration_type, duration_days, start_date, note,
    status, return_status, return_note, is_on_time, created_at, updated_at)
VALUES
-- 张三 returned 痞老板's 手机 (on time)
(gen_random_uuid(), '1b9cf7da-1e03-4f74-b21a-c316f98bb79b', '40000000-0000-0000-0000-000000000011',
 'day', 2, CURRENT_DATE - INTERVAL '8 days', '临时急用', 'returned',
 'on_time', '很干净，谢谢', true,
 NOW() - INTERVAL '10 days', NOW() - INTERVAL '8 days'),
-- 李四 returned 痞老板's 瓶子 (overdue with damage)
(gen_random_uuid(), 'bcc5533e-d86a-4d90-b548-cbd4e72378d9', '40000000-0000-0000-0000-000000000012',
 'day', 3, CURRENT_DATE - INTERVAL '6 days', '', 'returned',
 'late', '不小心磕了个小缺口，抱歉', false,
 NOW() - INTERVAL '9 days', NOW() - INTERVAL '6 days')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 10. 已完成 → helpReq (completed help for 痞老板): completed help_applications
-- =============================================================================
INSERT INTO help_applications (id, help_id, helper_id, note, status, completed_at, created_at, updated_at)
VALUES
-- 张三 completed helping 痞老板's "帮我搬家"
(gen_random_uuid(), '5008c145-733c-4519-9925-c781ae393ab5', '40000000-0000-0000-0000-000000000011',
 '搬完了，挺顺利的', 'completed', NOW() - INTERVAL '7 days',
 NOW() - INTERVAL '10 days', NOW() - INTERVAL '7 days')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 11. 已完成 → helpPro (痞老板 completed helping others): completed help_applications
-- =============================================================================
INSERT INTO help_applications (id, help_id, helper_id, note, status, completed_at, created_at, updated_at)
VALUES
-- 痞老板 completed helping 张三's "求代取快递3个"
(gen_random_uuid(), 'ea6cdd35-6e58-4ee2-ba84-ca2f5f39d63b', '284879cc-fb93-44ce-8971-966b41431ca7',
 '3个快递都取到了，放在门口', 'completed', NOW() - INTERVAL '2 days',
 NOW() - INTERVAL '3 days', NOW() - INTERVAL '2 days'),
-- 痞老板 completed helping 王五's "急！求帮做晚饭"
(gen_random_uuid(), '43114a67-c34b-4142-8030-35d48b219c63', '284879cc-fb93-44ce-8971-966b41431ca7',
 '做了两菜一汤，老人吃得很开心', 'completed', NOW() - INTERVAL '4 days',
 NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 12. Ratings — to make completed items show rating data
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

-- Rating for overdue borrow (《三体》全集)
INSERT INTO ratings (id, borrow_id, help_application_id, from_user_id, to_user_id, score, dimension_scores, created_at)
SELECT gen_random_uuid(), br.id, NULL, '284879cc-fb93-44ce-8971-966b41431ca7', '40000000-0000-0000-0000-000000000011',
    3, '{"timeliness":2,"condition":4,"communication":3}', NOW()
FROM borrow_requests br
WHERE br.borrower_id = '284879cc-fb93-44ce-8971-966b41431ca7'
  AND br.idle_id = 'c27bcbdc-e1b1-4bdf-8f4b-072eb09dc86e'
  AND br.status = 'returned'
  AND NOT EXISTS (SELECT 1 FROM ratings r WHERE r.borrow_id = br.id AND r.from_user_id = '284879cc-fb93-44ce-8971-966b41431ca7')
LIMIT 1;

-- Rating for lend return (痞老板's 手机 returned by 张三)
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

-- Rating for lend return (痞老板's 瓶子 returned by 李四, damaged)
INSERT INTO ratings (id, borrow_id, help_application_id, from_user_id, to_user_id, score, dimension_scores, created_at)
SELECT gen_random_uuid(), br.id, NULL, '284879cc-fb93-44ce-8971-966b41431ca7', '40000000-0000-0000-0000-000000000012',
    2, '{"timeliness":3,"condition":1,"communication":2}', NOW()
FROM borrow_requests br
WHERE br.idle_id = 'bcc5533e-d86a-4d90-b548-cbd4e72378d9'
  AND br.borrower_id = '40000000-0000-0000-0000-000000000012'
  AND br.status = 'returned'
  AND NOT EXISTS (SELECT 1 FROM ratings r WHERE r.borrow_id = br.id AND r.from_user_id = '284879cc-fb93-44ce-8971-966b41431ca7')
LIMIT 1;

-- Ratings for help applications (completed)
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
-- 13. Make sure 痞老板's "帮我搬家" help request has a future end time (for in-progress)
-- =============================================================================
UPDATE help_requests
SET time_start = CURRENT_TIMESTAMP + INTERVAL '2 days',
    time_end = CURRENT_TIMESTAMP + INTERVAL '3 days'
WHERE id = '5008c145-733c-4519-9925-c781ae393ab5'
  AND (time_start IS NULL OR time_start < NOW());
