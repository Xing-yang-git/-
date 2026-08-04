-- 06d -- 补充 rejected borrow (+1)
INSERT INTO borrow_requests (idle_id, borrower_id, duration_type, duration_days, note, status, created_at, updated_at)
SELECT id, (SELECT id FROM users WHERE name='美羊羊'), 'day', 2, '不需要了', 'rejected', NOW() - '3 days'::INTERVAL, NOW()
FROM idle_items WHERE user_id = 2 AND status = 'online' LIMIT 1;
