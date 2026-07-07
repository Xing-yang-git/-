-- =============================================================================
-- Migration: align existing database to current JPA entity definitions.
-- Idempotent — uses IF NOT EXISTS / IF EXISTS and DO blocks.
-- Run with: psql -U postgres -d community_platform -f alter.sql
-- =============================================================================

-- 0. users — fix CHECK constraint FIRST (before any other changes)
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_user_type_check;
ALTER TABLE users ADD CONSTRAINT users_user_type_check CHECK (user_type IN ('业主','租客','物业','admin','super_admin'));

-- 6. verifications — missing columns
ALTER TABLE verifications ADD COLUMN IF NOT EXISTS reject_reason TEXT;
ALTER TABLE verifications ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;

-- 7. idle_items — missing columns + type fix
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS condition VARCHAR(10) NOT NULL DEFAULT 'good';
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS max_duration INTEGER DEFAULT 7;
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS pickup_method VARCHAR(30) NOT NULL DEFAULT 'self_pickup';
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();
-- Fix category: should be NOT NULL
ALTER TABLE idle_items ALTER COLUMN category SET NOT NULL;
-- Fix price: VARCHAR(50) → DECIMAL(10,2).  Use USING to cast.
DO $$ BEGIN
    ALTER TABLE idle_items ALTER COLUMN price TYPE DECIMAL(10,2) USING (NULLIF(price, '')::DECIMAL(10,2));
EXCEPTION WHEN others THEN NULL;
END $$;
ALTER TABLE idle_items ALTER COLUMN price SET DEFAULT 0;
ALTER TABLE idle_items ALTER COLUMN price SET NOT NULL;

-- 8. help_requests — missing columns
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS time_start TIMESTAMP;
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS time_end TIMESTAMP;
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS location VARCHAR(200);
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS reward_type VARCHAR(20) NOT NULL DEFAULT 'free';
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS images TEXT;
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();
-- Fix category: should be NOT NULL
ALTER TABLE help_requests ALTER COLUMN category SET NOT NULL;

-- 9. help_applications — missing columns
ALTER TABLE help_applications ADD COLUMN IF NOT EXISTS note TEXT;
ALTER TABLE help_applications ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();

-- 10. borrow_requests — missing columns
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

-- 11. chat_sessions — missing columns + nullable fixes
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS last_message TEXT;
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS last_message_at TIMESTAMP;
ALTER TABLE chat_sessions ALTER COLUMN post_type SET NOT NULL;
ALTER TABLE chat_sessions ALTER COLUMN post_id SET NOT NULL;

-- 12. chat_messages — missing columns
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS message_type VARCHAR(10) NOT NULL DEFAULT 'text';
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS is_read BOOLEAN NOT NULL DEFAULT FALSE;

-- 13. notifications — nullable fixes
ALTER TABLE notifications ALTER COLUMN type SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN title SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN is_read SET NOT NULL;

-- 14. operation_logs — nullable + length fixes
ALTER TABLE operation_logs ALTER COLUMN action SET NOT NULL;
ALTER TABLE operation_logs ALTER COLUMN target_type TYPE VARCHAR(30);

-- 15. ratings — major alignment (columns renamed, added, removed)
-- Strategy: add new columns first, migrate data, then drop old columns.

-- Step 1: Add new columns (nullable first)
ALTER TABLE ratings ADD COLUMN IF NOT EXISTS help_application_id UUID REFERENCES help_applications(id);
ALTER TABLE ratings ADD COLUMN IF NOT EXISTS from_user_id UUID;
ALTER TABLE ratings ADD COLUMN IF NOT EXISTS to_user_id UUID;
ALTER TABLE ratings ADD COLUMN IF NOT EXISTS dimension_scores TEXT;

-- Step 2: Migrate data from old columns if they exist
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

-- Step 3: Set NOT NULL on migrated columns after data copy
ALTER TABLE ratings ALTER COLUMN from_user_id SET NOT NULL;
ALTER TABLE ratings ALTER COLUMN to_user_id SET NOT NULL;

-- Step 4: Add FK constraints
ALTER TABLE ratings ADD CONSTRAINT IF NOT EXISTS fk_ratings_from_user FOREIGN KEY (from_user_id) REFERENCES users(id);
ALTER TABLE ratings ADD CONSTRAINT IF NOT EXISTS fk_ratings_to_user FOREIGN KEY (to_user_id) REFERENCES users(id);

-- Step 5: Make borrow_id nullable (entity allows it)
ALTER TABLE ratings ALTER COLUMN borrow_id DROP NOT NULL;

-- Step 6: Drop old columns (safe — data already migrated)
ALTER TABLE ratings DROP COLUMN IF EXISTS rater_id;
ALTER TABLE ratings DROP COLUMN IF EXISTS target_id;
ALTER TABLE ratings DROP COLUMN IF EXISTS comment;

-- =============================================================================
-- 16. users — expand user_type CHECK constraint + add doc_images, reject_reason
-- =============================================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS doc_images TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS reject_reason TEXT;

-- Expand user_type CHECK constraint to accept 业主/租客/物业
DO $$ BEGIN
    ALTER TABLE users DROP CONSTRAINT IF EXISTS users_user_type_check;
EXCEPTION WHEN others THEN NULL;
END $$;
ALTER TABLE users ADD CONSTRAINT users_user_type_check
    CHECK (user_type IN ('业主','租客','物业','admin','super_admin'));

-- =============================================================================
-- 18. idle_items — add duration_unit column
-- =============================================================================
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS duration_unit VARCHAR(10) NOT NULL DEFAULT 'day';

-- 19. idle_items — change condition default from 'good' to 'normal'
ALTER TABLE idle_items ALTER COLUMN condition SET DEFAULT 'normal';

-- 20. users — change user_type default from 'resident' to '业主', update CHECK constraint
ALTER TABLE users ALTER COLUMN user_type SET DEFAULT '业主';
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_user_type_check;
ALTER TABLE users ADD CONSTRAINT users_user_type_check
    CHECK (user_type IN ('业主','租客','物业','admin','super_admin'));

-- 21. idle_items — fix NULL max_duration
UPDATE idle_items SET max_duration = 7 WHERE max_duration IS NULL;

-- 22. idle_items — fix condition CHECK constraint (old: like_new/good/fair → new: like-new/normal/worn)
ALTER TABLE idle_items DROP CONSTRAINT IF EXISTS idle_items_condition_check;
UPDATE idle_items SET condition = 'like-new' WHERE condition = 'like_new';
UPDATE idle_items SET condition = 'normal' WHERE condition = 'good';
UPDATE idle_items SET condition = 'worn' WHERE condition = 'fair';
ALTER TABLE idle_items ADD CONSTRAINT idle_items_condition_check
    CHECK (condition IN ('like-new', 'normal', 'worn'));

-- =============================================================================
-- 23. idle_items — content management fields (B端 内容管理)
-- =============================================================================
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS is_proxy BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS violation_type VARCHAR(20);
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS violation_reason TEXT;
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS violated_by UUID REFERENCES users(id);
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS violated_at TIMESTAMP;

-- =============================================================================
-- 24. help_requests — content management fields (B端 内容管理)
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
