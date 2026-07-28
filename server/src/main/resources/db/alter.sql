-- =============================================================================
-- Migration：将现有数据库对齐到当前 JPA Entity 定义。
-- 幂等 — 使用 IF NOT EXISTS / IF EXISTS 和 DO 块。
-- 执行方式：psql -U postgres -d community_platform -f alter.sql
-- =============================================================================

-- 0. users — 首先修复 CHECK 约束（先于其他所有变更）
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_user_type_check;
ALTER TABLE users ADD CONSTRAINT users_user_type_check CHECK (user_type IN ('业主','租客','物业','admin','senior_admin','super_admin'));

-- 6. verifications — 补充缺失列
ALTER TABLE verifications ADD COLUMN IF NOT EXISTS reject_reason TEXT;
ALTER TABLE verifications ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;

-- 7. idle_items — 补充缺失列 + 类型修复
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS condition VARCHAR(10) NOT NULL DEFAULT 'good';
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS max_duration INTEGER DEFAULT 7;
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS pickup_method VARCHAR(30) NOT NULL DEFAULT 'self_pickup';
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();
-- 修复 category：应为 NOT NULL
ALTER TABLE idle_items ALTER COLUMN category SET NOT NULL;
-- 修复 price：VARCHAR(50) → DECIMAL(10,2)。使用 USING 做类型转换。
DO $$ BEGIN
    ALTER TABLE idle_items ALTER COLUMN price TYPE DECIMAL(10,2) USING (NULLIF(price, '')::DECIMAL(10,2));
EXCEPTION WHEN others THEN NULL;
END $$;
ALTER TABLE idle_items ALTER COLUMN price SET DEFAULT 0;
ALTER TABLE idle_items ALTER COLUMN price SET NOT NULL;

-- 8. help_requests — 补充缺失列
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS time_start TIMESTAMP;
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS time_end TIMESTAMP;
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS location VARCHAR(200);
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS reward_type VARCHAR(20) NOT NULL DEFAULT 'free';
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS images TEXT;
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();
-- 修复 category：应为 NOT NULL
ALTER TABLE help_requests ALTER COLUMN category SET NOT NULL;

-- 9. help_applications — 补充缺失列
ALTER TABLE help_applications ADD COLUMN IF NOT EXISTS note TEXT;
ALTER TABLE help_applications ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();

-- 10. borrow_requests — 补充缺失列
ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS duration_type VARCHAR(10) NOT NULL DEFAULT 'day';
ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS duration_days INTEGER NOT NULL DEFAULT 7;
ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS start_date DATE;
ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS note TEXT;
ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS handoff_photos TEXT;
ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS return_status VARCHAR(20);
ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS return_note TEXT;
ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS damage_note TEXT;
ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS is_on_time BOOLEAN;
ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS return_photos TEXT;
ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

-- 11. chat_sessions — 补充缺失列 + 可空性修复
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS last_message TEXT;
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS last_message_at TIMESTAMP;
ALTER TABLE chat_sessions ALTER COLUMN post_type SET NOT NULL;
ALTER TABLE chat_sessions ALTER COLUMN post_id SET NOT NULL;

-- 12. chat_messages — 补充缺失列
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS message_type VARCHAR(10) NOT NULL DEFAULT 'text';
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS is_read BOOLEAN NOT NULL DEFAULT FALSE;

-- 13. notifications — 可空性修复
ALTER TABLE notifications ALTER COLUMN type SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN title SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN is_read SET NOT NULL;

-- 14. operation_logs — 可空性 + 长度修复
ALTER TABLE operation_logs ALTER COLUMN action SET NOT NULL;
ALTER TABLE operation_logs ALTER COLUMN target_type TYPE VARCHAR(30);

-- 15. ratings — 大幅对齐（列重命名、新增、删除）
-- 策略：先新增列，迁移数据，再删除旧列。

-- 第 1 步：新增列（先允许为空）
ALTER TABLE ratings ADD COLUMN IF NOT EXISTS help_application_id UUID REFERENCES help_applications(id);
ALTER TABLE ratings ADD COLUMN IF NOT EXISTS from_user_id UUID;
ALTER TABLE ratings ADD COLUMN IF NOT EXISTS to_user_id UUID;
ALTER TABLE ratings ADD COLUMN IF NOT EXISTS dimension_scores TEXT;

-- 第 2 步：若旧列存在则迁移数据
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'ratings' AND column_name = 'rater_id') THEN
        UPDATE ratings SET from_user_id = rater_id WHERE from_user_id IS NULL AND rater_id IS NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'ratings' AND column_name = 'target_id') THEN
        UPDATE ratings SET to_user_id = target_id WHERE to_user_id IS NULL AND target_id IS NOT NULL;
    END IF;
END $$;

-- 第 3 步：数据拷贝完成后为迁移列设置 NOT NULL
ALTER TABLE ratings ALTER COLUMN from_user_id SET NOT NULL;
ALTER TABLE ratings ALTER COLUMN to_user_id SET NOT NULL;

-- 第 4 步：添加外键约束
ALTER TABLE ratings ADD CONSTRAINT IF NOT EXISTS fk_ratings_from_user FOREIGN KEY (from_user_id) REFERENCES users(id);
ALTER TABLE ratings ADD CONSTRAINT IF NOT EXISTS fk_ratings_to_user FOREIGN KEY (to_user_id) REFERENCES users(id);

-- 第 5 步：将 borrow_id 改为可空（Entity 允许为空）
ALTER TABLE ratings ALTER COLUMN borrow_id DROP NOT NULL;

-- 第 6 步：删除旧列（安全 — 数据已迁移完毕）
ALTER TABLE ratings DROP COLUMN IF EXISTS rater_id;
ALTER TABLE ratings DROP COLUMN IF EXISTS target_id;
ALTER TABLE ratings DROP COLUMN IF EXISTS comment;

-- =============================================================================
-- 16. users — 扩展 user_type CHECK 约束 + 新增 doc_images、reject_reason
-- =============================================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS doc_images TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS reject_reason TEXT;

-- 扩展 user_type CHECK 约束以接受 业主/租客/物业
DO $$ BEGIN
    ALTER TABLE users DROP CONSTRAINT IF EXISTS users_user_type_check;
EXCEPTION WHEN others THEN NULL;
END $$;
ALTER TABLE users ADD CONSTRAINT users_user_type_check
    CHECK (user_type IN ('业主','租客','物业','admin','senior_admin','super_admin'));

-- =============================================================================
-- 18. idle_items — 新增 duration_unit 列
-- =============================================================================
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS duration_unit VARCHAR(10) NOT NULL DEFAULT 'day';

-- 19. idle_items — 将 condition 默认值从 'good' 改为 'normal'
ALTER TABLE idle_items ALTER COLUMN condition SET DEFAULT 'normal';

-- 20. users — 将 user_type 默认值从 'resident' 改为 '业主'，并更新 CHECK 约束
ALTER TABLE users ALTER COLUMN user_type SET DEFAULT '业主';
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_user_type_check;
ALTER TABLE users ADD CONSTRAINT users_user_type_check
    CHECK (user_type IN ('业主','租客','物业','admin','senior_admin','super_admin'));

-- 21. idle_items — 修复为 NULL 的 max_duration
UPDATE idle_items SET max_duration = 7 WHERE max_duration IS NULL;

-- 22. idle_items — 修复 condition CHECK 约束（旧值：like_new/good/fair → 新值：like-new/normal/worn）
ALTER TABLE idle_items DROP CONSTRAINT IF EXISTS idle_items_condition_check;
UPDATE idle_items SET condition = 'like-new' WHERE condition = 'like_new';
UPDATE idle_items SET condition = 'normal' WHERE condition = 'good';
UPDATE idle_items SET condition = 'worn' WHERE condition = 'fair';
ALTER TABLE idle_items ADD CONSTRAINT idle_items_condition_check
    CHECK (condition IN ('like-new', 'normal', 'worn'));

-- =============================================================================
-- 23. idle_items — 内容管理字段 (B端 内容管理)
-- =============================================================================
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS is_proxy BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS violation_type VARCHAR(20);
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS violation_reason TEXT;
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS violated_by UUID REFERENCES users(id);
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS violated_at TIMESTAMP;

-- =============================================================================
-- 24. help_requests — 内容管理字段 (B端 内容管理)
-- =============================================================================
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS is_proxy BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS violation_type VARCHAR(20);
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS violation_reason TEXT;
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS violated_by UUID REFERENCES users(id);
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS violated_at TIMESTAMP;

-- =============================================================================
-- 25. help_applications — completed_at (C端 帮助完成确认)
-- =============================================================================
ALTER TABLE help_applications ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;

-- =============================================================================
-- 26. users — token_version (单会话登录控制)
-- =============================================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;

-- =============================================================================
-- 27. export_logs — 新增 helps_count（互助记录 Sheet）
-- =============================================================================
ALTER TABLE export_logs ADD COLUMN IF NOT EXISTS helps_count INTEGER NOT NULL DEFAULT 0;

-- =============================================================================
-- 28. borrow_requests — 补充缺失的 damage_type 列
-- =============================================================================
ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS damage_type VARCHAR(20);

-- =============================================================================
-- 29. borrow_requests — 归一化 damage_type 存量数据（统一为 normal/severe/broken 三类）
-- =============================================================================
-- none → normal：return-detail 旧版"无损坏"及 test-data 的无损坏值
UPDATE borrow_requests SET damage_type = 'normal' WHERE damage_type IN ('none');
-- minor → severe：return-detail 旧版"轻微损坏"
UPDATE borrow_requests SET damage_type = 'severe' WHERE damage_type IN ('minor');
-- slight → severe：旧版后端 mapDamageType 的"轻微损坏"（test-data 中存在）
UPDATE borrow_requests SET damage_type = 'severe' WHERE damage_type IN ('slight');
-- moderate → severe：旧版后端 mapDamageType 的"中度损坏"（test-data 中存在）
UPDATE borrow_requests SET damage_type = 'severe' WHERE damage_type IN ('moderate');

-- =============================================================================
-- 30. borrow_requests — 归一化 return_status 存量数据（清理混入的物品状况值）
-- =============================================================================
-- perfect → ontime：schema 设计遗留值，语义等同于按时
UPDATE borrow_requests SET return_status = 'ontime' WHERE return_status IN ('perfect');
-- damaged / lost → NULL：物品损坏描述值误入 return_status 列，置空
UPDATE borrow_requests SET return_status = NULL WHERE return_status IN ('damaged', 'lost');
-- normal → ontime：my-posts 硬编码值，语义是物品正常 + 按时归还
UPDATE borrow_requests SET return_status = 'ontime' WHERE return_status = 'normal';

-- =============================================================================
-- 31. 状态值归一化：reserved/borrowing/helping → pending/active
-- =============================================================================
-- idle_items: reserved → pending（统一待审批语义）
UPDATE idle_items SET status = 'pending' WHERE status = 'reserved';
-- idle_items: borrowing → active（统一进行中语义）
UPDATE idle_items SET status = 'active' WHERE status = 'borrowing';
-- help_requests: reserved → pending
UPDATE help_requests SET status = 'pending' WHERE status = 'reserved';
-- help_requests: helping → active
UPDATE help_requests SET status = 'active' WHERE status = 'helping';
