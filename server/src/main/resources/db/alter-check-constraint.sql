-- 扩展 user_type CHECK 约束以包含 senior_admin（高级管理员）
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_user_type_check;
ALTER TABLE users ADD CONSTRAINT users_user_type_check
    CHECK (user_type IN ('业主', '租客', '物业', 'admin', 'senior_admin', 'super_admin'));
