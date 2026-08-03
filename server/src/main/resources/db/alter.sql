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

-- =============================================================================
-- 32. pgvector 扩展及向量列 — 语义搜索和供需匹配基础设施
-- =============================================================================
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS embedding vector(1024);
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS embedding vector(1024);

-- IVFFlat 向量索引（余弦相似度）
CREATE INDEX IF NOT EXISTS idx_idle_embedding ON idle_items USING ivfflat (embedding vector_cosine_ops) WITH (lists = 50);
CREATE INDEX IF NOT EXISTS idx_help_embedding ON help_requests USING ivfflat (embedding vector_cosine_ops) WITH (lists = 50);

-- =============================================================================
-- 33. AI 内容审核字段 — idle_items + help_requests
-- =============================================================================
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(10);
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS moderation_reason TEXT;
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS moderated_at TIMESTAMP;
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS reviewed_by BIGINT;
ALTER TABLE idle_items ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(10);
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS moderation_reason TEXT;
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS moderated_at TIMESTAMP;
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS reviewed_by BIGINT;
ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;

-- 列注释
COMMENT ON COLUMN idle_items.embedding IS '语义向量（TEXT存储pgvector字面量，1024维浮点数组），查询时通过CAST转为vector类型进行余弦距离计算，用于语义搜索和供需匹配';
COMMENT ON COLUMN idle_items.moderation_status IS 'AI内容审核状态：pending(待AI审核)/green(审核通过，自动上线)/yellow(待人工复核，AI判定疑似不合规)/red(审核驳回，AI确认违规自动拒绝)/reviewed(已人工复核，管理员已手动处理)/NULL(非审核流程下架，不参与moderation筛选)';
COMMENT ON COLUMN idle_items.moderation_reason IS 'AI审核原因简述，green时为空，yellow/red时记录AI判定的具体违规类型';
COMMENT ON COLUMN idle_items.moderated_at IS 'AI审核完成时间，AI判定出结果时写入，后续管理员操作不更新此字段';
COMMENT ON COLUMN idle_items.reviewed_by IS '人工复核的管理员用户ID，外键→users.id，NULL表示AI自动处理';
COMMENT ON COLUMN idle_items.reviewed_at IS '人工复核时间，管理员通过或驳回时写入，NULL表示尚未人工处理';
COMMENT ON COLUMN help_requests.embedding IS '语义向量（TEXT存储pgvector字面量，1024维浮点数组），查询时通过CAST转为vector类型进行余弦距离计算，用于语义搜索和供需匹配';
COMMENT ON COLUMN help_requests.moderation_status IS 'AI内容审核状态：pending(待AI审核)/green(审核通过，自动上线)/yellow(待人工复核，AI判定疑似不合规)/red(审核驳回，AI确认违规自动拒绝)/reviewed(已人工复核，管理员已手动处理)/NULL(非审核流程下架，不参与moderation筛选)';
COMMENT ON COLUMN help_requests.moderation_reason IS 'AI审核原因简述，green时为空，yellow/red时记录AI判定的具体违规类型';
COMMENT ON COLUMN help_requests.moderated_at IS 'AI审核完成时间，AI判定出结果时写入，后续管理员操作不更新此字段';
COMMENT ON COLUMN help_requests.reviewed_by IS '人工复核的管理员用户ID，外键→users.id，NULL表示AI自动处理';
COMMENT ON COLUMN help_requests.reviewed_at IS '人工复核时间，管理员通过或驳回时写入，NULL表示尚未人工处理';

-- 存量数据迁移：已上线的历史内容标记为 AI 审核通过
UPDATE idle_items SET moderation_status='green', moderated_at=NOW() WHERE moderation_status IS NULL AND status IN ('online','completed','offline');
UPDATE help_requests SET moderation_status='green', moderated_at=NOW() WHERE moderation_status IS NULL AND status IN ('online','completed','offline');

-- =============================================================================
-- 2026-07-31: 统一原因字段 + 删除冗余列 + DELETED → OFFLINE 迁移
-- =============================================================================

-- Step 1: 迁移 delistReason（violation_reason 优先，其次 moderation_reason）
UPDATE idle_items SET delist_reason = violation_reason
  WHERE delist_reason IS NULL AND violation_reason IS NOT NULL;
UPDATE idle_items SET delist_reason = moderation_reason
  WHERE delist_reason IS NULL AND moderation_reason IS NOT NULL;

UPDATE help_requests SET delist_reason = violation_reason
  WHERE delist_reason IS NULL AND violation_reason IS NOT NULL;
UPDATE help_requests SET delist_reason = moderation_reason
  WHERE delist_reason IS NULL AND moderation_reason IS NOT NULL;

-- Step 2: DELETED → OFFLINE（idle_items 原 deleteItem 改为设置 OFFLINE）
UPDATE idle_items SET status = 'offline', delist_reason = COALESCE(delist_reason, '用户删除'), updated_at = NOW()
  WHERE status = 'deleted';
UPDATE help_requests SET status = 'offline', updated_at = NOW()
  WHERE status = 'deleted';

-- Step 3: 删除冗余列（idle_items 7 列）
ALTER TABLE idle_items DROP COLUMN IF EXISTS violation_type;
ALTER TABLE idle_items DROP COLUMN IF EXISTS violation_reason;
ALTER TABLE idle_items DROP COLUMN IF EXISTS violated_by;
ALTER TABLE idle_items DROP COLUMN IF EXISTS violated_at;
ALTER TABLE idle_items DROP COLUMN IF EXISTS moderation_reason;
ALTER TABLE idle_items DROP COLUMN IF EXISTS moderated_at;
ALTER TABLE idle_items DROP COLUMN IF EXISTS reviewed_at;

-- Step 4: 删除冗余列（help_requests 8 列 + 新增 location）
ALTER TABLE help_requests DROP COLUMN IF EXISTS violation_type;
ALTER TABLE help_requests DROP COLUMN IF EXISTS violation_reason;
ALTER TABLE help_requests DROP COLUMN IF EXISTS violated_by;
ALTER TABLE help_requests DROP COLUMN IF EXISTS violated_at;
ALTER TABLE help_requests DROP COLUMN IF EXISTS moderation_reason;
ALTER TABLE help_requests DROP COLUMN IF EXISTS moderated_at;
ALTER TABLE help_requests DROP COLUMN IF EXISTS reviewed_at;
ALTER TABLE help_requests DROP COLUMN IF EXISTS embedding;

ALTER TABLE help_requests ADD COLUMN IF NOT EXISTS location    VARCHAR(200);

-- Step 5: 更新列注释
COMMENT ON COLUMN idle_items.status        IS '状态：online(在线)/draft(草稿)/pending_review(待AI审核)/pending(待审批)/active(进行中)/completed(已完成)/offline(已下架)';
COMMENT ON COLUMN idle_items.delist_reason IS '统一下架原因：AI审核原因、管理员驳回/下架原因、用户自行下架原因';
COMMENT ON COLUMN help_requests.status        IS '状态：online(在线)/draft(草稿)/pending_review(待AI审核)/pending(待审批)/active(进行中)/completed(已完成)/offline(已下架)';
COMMENT ON COLUMN help_requests.delist_reason IS '统一下架原因：AI审核原因、管理员驳回/下架原因、用户自行下架原因';
COMMENT ON COLUMN help_requests.location      IS '求助地点';

-- =============================================================================
-- 2026-07-31: 删除 reward_type 列（字段预留后确认不需要）
-- =============================================================================
ALTER TABLE help_requests DROP COLUMN IF EXISTS reward_type;

-- =============================================================================
-- 34. knowledge_items — AI Agent 小区知识库（RAG 数据源）
-- =============================================================================
CREATE TABLE IF NOT EXISTS knowledge_items (
    id          BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,  -- 知识条目ID
    tenant_id   BIGINT       NOT NULL REFERENCES tenants(id),               -- 所属小区ID
    category    VARCHAR(20)  NOT NULL,                                      -- 分类: rules(规章制度)/service(服务手册)/help(平台帮助)/guide(办事指南)
    title       VARCHAR(200) NOT NULL,                                      -- 条目标题（如"装修施工时间规定"）
    content     TEXT         NOT NULL,                                      -- 条目正文（检索与问答来源）
    source      VARCHAR(100),                                               -- 来源文档名（如《小区规章制度》）
    tags        VARCHAR(500),                                               -- 逗号分隔标签，关键词检索兜底
    embedding   TEXT,                                                       -- 1024维向量字面量 '[0.1,0.2,...]'（RAG，智谱 embedding-3 dimensions=1024）
    status      VARCHAR(10)  NOT NULL DEFAULT 'online',                     -- online(启用)/offline(停用)
    created_by  BIGINT,                                                     -- 录入管理员用户ID → users.id
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),                        -- 创建时间
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()                         -- 更新时间
);
COMMENT ON TABLE  knowledge_items IS 'AI Agent 小区知识库条目（RAG 检索源）';
COMMENT ON COLUMN knowledge_items.category IS '分类: rules(规章制度)/service(服务手册)/help(平台帮助)/guide(办事指南)';
COMMENT ON COLUMN knowledge_items.embedding IS '1024维语义向量字面量，查询时 CAST 转为 vector 做余弦距离（智谱 embedding-3 dimensions=1024）';

-- 常规过滤索引（tenant + category + status）
CREATE INDEX IF NOT EXISTS idx_knowledge_tenant_category ON knowledge_items (tenant_id, category, status);
-- HNSW 向量索引：text 列表达式 CAST 必须声明固定维度 vector(1024)
-- （实测：无维度 CAST 报 "column does not have dimensions"；KnowledgeService 统一用智谱 embedding-3 dimensions=1024 生成，维度严格一致）
CREATE INDEX IF NOT EXISTS idx_knowledge_embedding_hnsw ON knowledge_items USING hnsw (((embedding)::vector(1024)) vector_cosine_ops);

-- =============================================================================
-- 35. agent_conversations — AI Agent 会话归档表（长期记忆）
-- =============================================================================
CREATE TABLE IF NOT EXISTS agent_conversations (
    id              BIGINT      GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,  -- 会话ID
    user_id         BIGINT      NOT NULL REFERENCES users(id),                 -- 住户用户ID
    tenant_id       BIGINT      NOT NULL REFERENCES tenants(id),               -- 所属小区ID
    title           VARCHAR(200),                                              -- 会话标题（归档时由首条消息生成）
    message_count   INT         NOT NULL DEFAULT 0,                            -- 消息条数（归档阈值判断）
    status          VARCHAR(10) NOT NULL DEFAULT 'active',                     -- active(进行中)/archived(已归档)/deleted(已软删)
    last_message_at TIMESTAMP,                                                 -- 最后一条消息时间（空闲归档判断，区别于 updated_at 审计时间）
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),                        -- 创建时间
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW()                         -- 更新时间（任意变更，审计用）
);
COMMENT ON TABLE  agent_conversations IS 'AI Agent 会话归档表（长期记忆，Redis 热会话 + PG 异步归档）';
COMMENT ON COLUMN agent_conversations.status IS 'active(进行中)/archived(已归档)/deleted(已软删，保留审计)';

CREATE INDEX IF NOT EXISTS idx_conversations_user ON agent_conversations (user_id, status, updated_at);

-- =============================================================================
-- 36. agent_messages — AI Agent 消息归档表（会话+消息分离）
-- =============================================================================
CREATE TABLE IF NOT EXISTS agent_messages (
    id                 BIGINT      GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,  -- 消息ID
    conversation_id    BIGINT      NOT NULL REFERENCES agent_conversations(id),   -- 所属会话ID（软删不物理删，故不加 CASCADE）
    role               VARCHAR(20) NOT NULL,                                      -- 消息角色: user(住户)/assistant(AI)/tool(工具调用结果)，不归档 system
    content            TEXT,                                                      -- 消息内容
    sources            TEXT,                                                      -- 引用来源（JSON数组，后端检索结果防幻觉）
    actions            TEXT,                                                      -- 动作卡片（JSON数组，需用户确认的写操作）
    moderation_status  VARCHAR(10) NOT NULL DEFAULT 'pending',                    -- 文本审核结果: pending/pass/fail
    moderation_reason  TEXT,                                                      -- 违规原因（fail 时填充）
    created_at         TIMESTAMP   NOT NULL DEFAULT NOW()                         -- 创建时间
);
COMMENT ON TABLE  agent_messages IS 'AI Agent 消息归档表（会话+消息分离，软删不物理删）';
COMMENT ON COLUMN agent_messages.role IS '消息角色: user(住户消息)/assistant(AI回复)/tool(工具调用结果)；system prompt 动态构建不归档';
COMMENT ON COLUMN agent_messages.moderation_status IS '文本审核结果: pending(待审核)/pass(通过)/fail(违规标记，异步审核不阻塞回复)';

CREATE INDEX IF NOT EXISTS idx_messages_conversation ON agent_messages (conversation_id, created_at);

-- =============================================================================
-- 37. 修正 embedding 列声明 — idle_items.embedding 统一为 TEXT（2048 维字面量）
-- =============================================================================
-- 背景：早期声明 idle_items.embedding 为 vector(1024) + ivfflat 索引，
-- 但数据库实际为 TEXT 列（智谱 embedding-3 默认 2048 维，EmbeddingService 未指定 dimensions）。
-- 语义搜索用 CAST + 顺序扫描（数据量小，且匹配查询先按小区/时间/状态过滤后候选集更小）。
-- 此段将声明对齐实际，并清理未生效的 ivfflat 索引（IF EXISTS 幂等）。
-- 顺序：先删索引再改类型——若旧库存在 ivfflat 索引，ALTER TYPE 会尝试在 TEXT 上重建 vector 索引而失败
DROP INDEX IF EXISTS idx_idle_embedding;
DROP INDEX IF EXISTS idx_help_embedding;
ALTER TABLE idle_items ALTER COLUMN embedding TYPE TEXT USING (embedding::text);
COMMENT ON COLUMN idle_items.embedding IS '语义向量（TEXT存储pgvector字面量，2048维浮点数组），查询时通过CAST转为vector类型进行余弦距离计算，用于语义搜索和供需匹配';
