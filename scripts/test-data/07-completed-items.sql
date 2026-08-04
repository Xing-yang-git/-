-- 07-completed-items.sql -- 已完成闲置+求助补充（B端内容管理"已完成"tab）
-- 归还/完成后物品和求助的状态需同步为 completed

-- 将有过 returned borrow 的闲置物品状态改为 completed
UPDATE idle_items SET status = 'completed', updated_at = NOW()
WHERE id IN (SELECT DISTINCT idle_id FROM borrow_requests WHERE status = 'returned')
  AND status NOT IN ('offline', 'completed');

-- 将有过 completed help_applications 的求助状态改为 completed（若仍为helping/online）
UPDATE help_requests SET status = 'completed', updated_at = NOW()
WHERE id IN (SELECT DISTINCT help_id FROM help_applications WHERE status = 'completed')
  AND status NOT IN ('offline', 'completed');

-- 额外补几条直接完成的记录（绕过 borrowing/helping 流程的）
INSERT INTO idle_items (user_id, tenant_id, post_type, title, description, category, condition, price, max_duration, duration_unit, pickup_method, status, created_at, updated_at)
SELECT id, 1, 'LEND', '已完成测试物品-' || id, '直接完成的测试数据', '工具', 'normal', 100, 7, 'day', 'self_pickup', 'completed', NOW() - (id || ' days')::INTERVAL, NOW()
FROM users WHERE name IN ('喜羊羊','懒羊羊','沸羊羊','暖羊羊','美羊羊') AND id IS NOT NULL;

INSERT INTO help_requests (user_id, tenant_id, title, description, category, is_urgent, status, created_at, updated_at)
SELECT id, 1, '已完成求助测试-' || id, '直接完成的测试数据', '维修', FALSE, 'completed', NOW() - (id || ' days')::INTERVAL, NOW()
FROM users WHERE name IN ('喜羊羊','懒羊羊','沸羊羊','暖羊羊','美羊羊') AND id IS NOT NULL;
