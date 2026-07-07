package com.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Idempotent schema migration — aligns the live PostgreSQL database
 * to the current JPA entity definitions.  Runs BEFORE DataInitializer.
 *
 * Every statement is guarded so the migration is safe to re-run.
 */
@Component
@Order(0)   // before DataInitializer (which uses default order)
public class SchemaMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigration.class);

    private final JdbcTemplate jdbc;

    public SchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        log.info("=== SchemaMigration started ===");
        int applied = 0;
        int skipped = 0;

        for (Migration m : MIGRATIONS) {
            try {
                if (m.exists(jdbc)) {
                    skipped++;
                } else {
                    m.apply(jdbc);
                    applied++;
                    log.info("  ✓ {}", m.description);
                }
            } catch (Exception e) {
                log.warn("  ✗ {} — {}", m.description, e.getMessage());
            }
        }

        log.info("=== SchemaMigration done: {} applied, {} skipped ===", applied, skipped);
    }

    // ──────────────────────────────────────────────
    // Migration definitions
    // ──────────────────────────────────────────────

    private static final List<Migration> MIGRATIONS = List.of(

        // ── 6. verifications ──
        addColumn("verifications", "reject_reason", "TEXT"),
        addColumn("verifications", "reviewed_at", "TIMESTAMP"),

        // ── 7. idle_items ──
        addColumn("idle_items", "condition", "VARCHAR(10) NOT NULL DEFAULT 'normal'"),
        addColumn("idle_items", "max_duration", "INTEGER DEFAULT 7"),
        addColumn("idle_items", "duration_unit", "VARCHAR(10) NOT NULL DEFAULT 'day'"),
        addColumn("idle_items", "pickup_method", "VARCHAR(30) NOT NULL DEFAULT 'self_pickup'"),
        addColumn("idle_items", "updated_at", "TIMESTAMP NOT NULL DEFAULT NOW()"),
        setNotNull("idle_items", "category"),
        new Migration("idle_items: condition DEFAULT 'good'→'normal'") {
            public boolean exists(JdbcTemplate j) {
                String def = j.queryForObject(
                    "SELECT column_default FROM information_schema.columns WHERE table_name = 'idle_items' AND column_name = 'condition'",
                    String.class);
                return def != null && def.contains("'normal'");
            }
            public void apply(JdbcTemplate j) {
                j.execute("ALTER TABLE idle_items ALTER COLUMN condition SET DEFAULT 'normal'");
            }
        },
        new Migration("idle_items: price VARCHAR→DECIMAL(10,2)") {
            public boolean exists(JdbcTemplate j) { return isType(j, "idle_items", "price", "numeric"); }
            public void apply(JdbcTemplate j) {
                j.execute("ALTER TABLE idle_items ALTER COLUMN price TYPE DECIMAL(10,2) USING (NULLIF(price,'')::DECIMAL(10,2))");
                j.execute("ALTER TABLE idle_items ALTER COLUMN price SET DEFAULT 0");
                j.execute("ALTER TABLE idle_items ALTER COLUMN price SET NOT NULL");
            }
        },

        // ── 8. help_requests ──
        addColumn("help_requests", "time_start", "TIMESTAMP"),
        addColumn("help_requests", "time_end", "TIMESTAMP"),
        addColumn("help_requests", "location", "VARCHAR(200)"),
        addColumn("help_requests", "reward_type", "VARCHAR(20) NOT NULL DEFAULT 'free'"),
        addColumn("help_requests", "images", "TEXT"),
        addColumn("help_requests", "updated_at", "TIMESTAMP NOT NULL DEFAULT NOW()"),
        setNotNull("help_requests", "category"),

        // ── 9. help_applications ──
        addColumn("help_applications", "note", "TEXT"),
        addColumn("help_applications", "updated_at", "TIMESTAMP NOT NULL DEFAULT NOW()"),

        // ── 10. borrow_requests ──
        addColumn("borrow_requests", "duration_type", "VARCHAR(10) NOT NULL DEFAULT 'day'"),
        addColumn("borrow_requests", "duration_days", "INTEGER NOT NULL DEFAULT 7"),
        addColumn("borrow_requests", "start_date", "DATE"),
        addColumn("borrow_requests", "note", "TEXT"),
        addColumn("borrow_requests", "handoff_photos", "TEXT"),
        addColumn("borrow_requests", "return_status", "VARCHAR(20)"),
        addColumn("borrow_requests", "return_note", "TEXT"),
        addColumn("borrow_requests", "damage_note", "TEXT"),
        addColumn("borrow_requests", "is_on_time", "BOOLEAN"),
        addColumn("borrow_requests", "return_photos", "TEXT"),
        addColumn("borrow_requests", "updated_at", "TIMESTAMP NOT NULL DEFAULT NOW()"),

        // ── 11. chat_sessions ──
        addColumn("chat_sessions", "last_message", "TEXT"),
        addColumn("chat_sessions", "last_message_at", "TIMESTAMP"),
        setNotNull("chat_sessions", "post_type"),
        setNotNull("chat_sessions", "post_id"),

        // ── 12. chat_messages ──
        addColumn("chat_messages", "message_type", "VARCHAR(10) NOT NULL DEFAULT 'text'"),
        addColumn("chat_messages", "is_read", "BOOLEAN NOT NULL DEFAULT FALSE"),

        // ── 13. notifications ──
        setNotNull("notifications", "type"),
        setNotNull("notifications", "title"),
        setNotNull("notifications", "is_read"),

        // ── 14. operation_logs ──
        setNotNull("operation_logs", "action"),
        new Migration("operation_logs: target_type VARCHAR(20)→VARCHAR(30)") {
            public boolean exists(JdbcTemplate j) {
                return columnLength(j, "operation_logs", "target_type") >= 30;
            }
            public void apply(JdbcTemplate j) {
                j.execute("ALTER TABLE operation_logs ALTER COLUMN target_type TYPE VARCHAR(30)");
            }
        },

        // ── 15. ratings — major alignment ──
        new Migration("ratings: add help_application_id") {
            public boolean exists(JdbcTemplate j) { return columnExists(j, "ratings", "help_application_id"); }
            public void apply(JdbcTemplate j) {
                j.execute("ALTER TABLE ratings ADD COLUMN help_application_id UUID");
            }
        },
        new Migration("ratings: add from_user_id + to_user_id + dimension_scores") {
            public boolean exists(JdbcTemplate j) { return columnExists(j, "ratings", "from_user_id"); }
            public void apply(JdbcTemplate j) {
                j.execute("ALTER TABLE ratings ADD COLUMN IF NOT EXISTS from_user_id UUID");
                j.execute("ALTER TABLE ratings ADD COLUMN IF NOT EXISTS to_user_id UUID");
                j.execute("ALTER TABLE ratings ADD COLUMN IF NOT EXISTS dimension_scores TEXT");
                // Migrate data from old columns if they exist
                if (columnExists(j, "ratings", "rater_id")) {
                    j.execute("UPDATE ratings SET from_user_id = rater_id WHERE from_user_id IS NULL AND rater_id IS NOT NULL");
                }
                if (columnExists(j, "ratings", "target_id")) {
                    j.execute("UPDATE ratings SET to_user_id = target_id WHERE to_user_id IS NULL AND target_id IS NOT NULL");
                }
                j.execute("ALTER TABLE ratings ALTER COLUMN from_user_id SET NOT NULL");
                j.execute("ALTER TABLE ratings ALTER COLUMN to_user_id SET NOT NULL");
            }
        },
        new Migration("ratings: drop legacy columns (rater_id, target_id, comment)") {
            public boolean exists(JdbcTemplate j) { return !columnExists(j, "ratings", "rater_id") && !columnExists(j, "ratings", "target_id") && !columnExists(j, "ratings", "comment"); }
            public void apply(JdbcTemplate j) {
                if (columnExists(j, "ratings", "rater_id")) j.execute("ALTER TABLE ratings DROP COLUMN rater_id");
                if (columnExists(j, "ratings", "target_id")) j.execute("ALTER TABLE ratings DROP COLUMN target_id");
                if (columnExists(j, "ratings", "comment")) j.execute("ALTER TABLE ratings DROP COLUMN comment");
            }
        },
        new Migration("ratings: borrow_id set nullable") {
            public boolean exists(JdbcTemplate j) { return isNullable(j, "ratings", "borrow_id"); }
            public void apply(JdbcTemplate j) {
                j.execute("ALTER TABLE ratings ALTER COLUMN borrow_id DROP NOT NULL");
            }
        },

        // ── users: expand user_type CHECK constraint to use Chinese values (业主/租客/物业) ──
        new Migration("users: update user_type CHECK constraint to Chinese values") {
            public boolean exists(JdbcTemplate j) {
                String def = j.queryForObject(
                    "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'users'::regclass AND conname = 'users_user_type_check'",
                    String.class);
                return def != null && def.contains("'业主'") && def.contains("'租客'") && def.contains("'物业'");
            }
            public void apply(JdbcTemplate j) {
                j.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_user_type_check");
                j.execute("ALTER TABLE users ADD CONSTRAINT users_user_type_check CHECK (user_type IN ('业主','租客','物业','admin','super_admin'))");
                j.execute("ALTER TABLE users ALTER COLUMN user_type SET DEFAULT '业主'");
            }
        },

        // ── notifications: drop restrictive type CHECK constraint ──
        new Migration("notifications: drop type CHECK constraint") {
            public boolean exists(JdbcTemplate j) {
                Integer c = j.queryForObject(
                    "SELECT COUNT(*) FROM pg_constraint WHERE conrelid = 'notifications'::regclass AND conname = 'notifications_type_check'",
                    Integer.class);
                return c == null || c == 0;
            }
            public void apply(JdbcTemplate j) {
                j.execute("ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check");
            }
        },

        // ── users: add doc_images + reject_reason columns ──
        addColumn("users", "doc_images", "TEXT"),
        addColumn("users", "reject_reason", "TEXT"),

        // ── 7. idle_items — content management fields ──
        addColumn("idle_items", "is_proxy", "BOOLEAN NOT NULL DEFAULT FALSE"),
        addColumn("idle_items", "violation_type", "VARCHAR(20)"),
        addColumn("idle_items", "violation_reason", "TEXT"),
        addColumn("idle_items", "violated_by", "UUID REFERENCES users(id)"),
        addColumn("idle_items", "violated_at", "TIMESTAMP"),

        // ── 8. help_requests — content management fields ──
        addColumn("help_requests", "is_proxy", "BOOLEAN NOT NULL DEFAULT FALSE"),
        addColumn("help_requests", "violation_type", "VARCHAR(20)"),
        addColumn("help_requests", "violation_reason", "TEXT"),
        addColumn("help_requests", "violated_by", "UUID REFERENCES users(id)"),
        addColumn("help_requests", "violated_at", "TIMESTAMP"),

        // ── 9. help_applications — completed_at ──
        addColumn("help_applications", "completed_at", "TIMESTAMP")
    );

    // ── helpers ──

    private static Migration addColumn(String table, String column, String type) {
        return new Migration(table + "." + column + " " + type) {
            public boolean exists(JdbcTemplate j) { return columnExists(j, table, column); }
            public void apply(JdbcTemplate j) {
                j.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + column + " " + type);
            }
        };
    }

    private static Migration setNotNull(String table, String column) {
        return new Migration(table + "." + column + " SET NOT NULL") {
            public boolean exists(JdbcTemplate j) { return !isNullable(j, table, column); }
            public void apply(JdbcTemplate j) {
                j.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " SET NOT NULL");
            }
        };
    }

    static boolean columnExists(JdbcTemplate j, String table, String column) {
        Integer c = j.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
            Integer.class, table, column);
        return c != null && c > 0;
    }

    static boolean isNullable(JdbcTemplate j, String table, String column) {
        String v = j.queryForObject(
            "SELECT is_nullable FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
            String.class, table, column);
        return "YES".equals(v);
    }

    static boolean isType(JdbcTemplate j, String table, String column, String typeKeyword) {
        String v = j.queryForObject(
            "SELECT data_type FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
            String.class, table, column);
        return v != null && v.equalsIgnoreCase(typeKeyword);
    }

    static int columnLength(JdbcTemplate j, String table, String column) {
        Integer v = j.queryForObject(
            "SELECT character_maximum_length FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
            Integer.class, table, column);
        return v != null ? v : 0;
    }

    // ── abstract base ──

    abstract static class Migration {
        final String description;
        Migration(String d) { this.description = d; }
        abstract boolean exists(JdbcTemplate j);
        abstract void apply(JdbcTemplate j);
    }
}
