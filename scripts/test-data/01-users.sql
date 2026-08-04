-- 01-users.sql -- 新增住户
INSERT INTO users (room_id, tenant_id, user_type, name, phone, auth_status, token_version, created_at, updated_at)
VALUES
  (3, 1, 'owner', '暖羊羊', '13800000007', 'approved', 0, NOW(), NOW()),
  (7, 1, 'owner', '美羊羊', '13800000008', 'approved', 0, NOW(), NOW())
ON CONFLICT DO NOTHING;
