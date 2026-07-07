-- =============================================================================
-- Community Platform Schema (PostgreSQL)
-- Auto-generated alignment: all 15 tables match JPA entities exactly.
-- Safe to re-run: uses IF NOT EXISTS on every table.
-- =============================================================================

-- 1. tenants
CREATE TABLE IF NOT EXISTS tenants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 2. buildings
CREATE TABLE IF NOT EXISTS buildings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    name        VARCHAR(50) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 3. units
CREATE TABLE IF NOT EXISTS units (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    building_id UUID NOT NULL REFERENCES buildings(id),
    name        VARCHAR(50) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 4. rooms
CREATE TABLE IF NOT EXISTS rooms (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_id     UUID NOT NULL REFERENCES units(id),
    room_number VARCHAR(10) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 5. users
CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id         UUID REFERENCES rooms(id),
    openid          VARCHAR(100) UNIQUE,
    username        VARCHAR(50) UNIQUE,
    password_hash   VARCHAR(255),
    user_type       VARCHAR(20) NOT NULL DEFAULT '业主',
    name            VARCHAR(50),
    phone           VARCHAR(20),
    avatar_url      VARCHAR(500),
    auth_status     VARCHAR(20) NOT NULL DEFAULT 'pending',
    banned_reason   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 6. verifications
CREATE TABLE IF NOT EXISTS verifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    real_name       VARCHAR(50),
    id_card         VARCHAR(20),
    id_card_front   VARCHAR(500),
    id_card_back    VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    reject_reason   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    reviewed_at     TIMESTAMP
);

-- 7. idle_items
CREATE TABLE IF NOT EXISTS idle_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    post_type       VARCHAR(10) NOT NULL DEFAULT 'LEND',
    title           VARCHAR(30) NOT NULL,
    description     TEXT,
    category        VARCHAR(20) NOT NULL,
    condition       VARCHAR(10) NOT NULL DEFAULT 'normal',
    price           DECIMAL(10,2) NOT NULL DEFAULT 0,
    images          TEXT,
    max_duration    INTEGER DEFAULT 7,
    duration_unit   VARCHAR(10) NOT NULL DEFAULT 'day',
    pickup_method   VARCHAR(30) NOT NULL DEFAULT 'self_pickup',
    status          VARCHAR(20) NOT NULL DEFAULT 'online',
    delist_reason   VARCHAR(200),
    is_proxy        BOOLEAN NOT NULL DEFAULT FALSE,
    violation_type  VARCHAR(20),
    violation_reason TEXT,
    violated_by     UUID REFERENCES users(id),
    violated_at     TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 8. help_requests
CREATE TABLE IF NOT EXISTS help_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    title           VARCHAR(30) NOT NULL,
    description     TEXT,
    category        VARCHAR(20) NOT NULL,
    is_urgent       BOOLEAN NOT NULL DEFAULT FALSE,
    time_start      TIMESTAMP,
    time_end        TIMESTAMP,
    location        VARCHAR(200),
    reward_type     VARCHAR(20) NOT NULL DEFAULT 'free',
    images          TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'online',
    delist_reason   VARCHAR(200),
    is_proxy        BOOLEAN NOT NULL DEFAULT FALSE,
    violation_type  VARCHAR(20),
    violation_reason TEXT,
    violated_by     UUID REFERENCES users(id),
    violated_at     TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 9. help_applications
CREATE TABLE IF NOT EXISTS help_applications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    help_id         UUID NOT NULL REFERENCES help_requests(id),
    helper_id       UUID NOT NULL REFERENCES users(id),
    note            TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 10. borrow_requests
CREATE TABLE IF NOT EXISTS borrow_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idle_id         UUID NOT NULL REFERENCES idle_items(id),
    borrower_id     UUID NOT NULL REFERENCES users(id),
    duration_type   VARCHAR(10) NOT NULL,
    duration_days   INTEGER NOT NULL,
    start_date      DATE,
    note            TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    handoff_photos  TEXT,
    return_status   VARCHAR(20),
    return_note     TEXT,
    damage_type     VARCHAR(20),
    damage_note     TEXT,
    is_on_time      BOOLEAN,
    return_photos   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 11. chat_sessions
CREATE TABLE IF NOT EXISTS chat_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_type       VARCHAR(10) NOT NULL,
    post_id         UUID NOT NULL,
    user1_id        UUID NOT NULL REFERENCES users(id),
    user2_id        UUID NOT NULL REFERENCES users(id),
    last_message    TEXT,
    last_message_at TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 12. chat_messages
CREATE TABLE IF NOT EXISTS chat_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID NOT NULL REFERENCES chat_sessions(id),
    sender_id       UUID NOT NULL REFERENCES users(id),
    content         TEXT NOT NULL,
    message_type    VARCHAR(10) NOT NULL DEFAULT 'text',
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 13. notifications
CREATE TABLE IF NOT EXISTS notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    type            VARCHAR(30) NOT NULL,
    title           VARCHAR(100) NOT NULL,
    content         TEXT,
    related_id      UUID,
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 14. operation_logs
CREATE TABLE IF NOT EXISTS operation_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id        UUID NOT NULL REFERENCES users(id),
    action          VARCHAR(50) NOT NULL,
    target_type     VARCHAR(30),
    target_id       UUID,
    detail          TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 15. ratings
CREATE TABLE IF NOT EXISTS ratings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    borrow_id           UUID REFERENCES borrow_requests(id),
    help_application_id UUID REFERENCES help_applications(id),
    from_user_id        UUID NOT NULL REFERENCES users(id),
    to_user_id          UUID NOT NULL REFERENCES users(id),
    score               INTEGER NOT NULL DEFAULT 5,
    dimension_scores    TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);
