package com.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 幂等的 schema 迁移 — 将线上 PostgreSQL 数据库
 * 对齐到当前 JPA Entity 定义。在 DataInitializer 之前运行。
 *
 * 每条语句都有存在性判断保护，可安全重复执行。
 */
@Component
@Order(0)   // 在 DataInitializer（使用默认顺序）之前执行
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

        // 清洗脏数据：物品/求助被下架但关联的申请未同步处理
        cleanupOrphanPendingBorrows(jdbc);
        cleanupOrphanPendingHelpApps(jdbc);
        cleanupOrphanActiveBorrows(jdbc);
        cleanupOrphanActiveHelpApps(jdbc);
    }

    /** 清洗脏数据：拒绝那些关联物品已被删除的待审批借入申请 */
    private void cleanupOrphanPendingBorrows(JdbcTemplate j) {
        try {
            Integer count = j.queryForObject(
                "SELECT COUNT(*) FROM borrow_requests br" +
                " JOIN idle_items i ON i.id = br.idle_id" +
                " WHERE br.status = 'pending' AND i.status = 'deleted'",
                Integer.class);
            if (count != null && count > 0) {
                int updated = j.update(
                    "UPDATE borrow_requests" +
                    " SET status = 'rejected'" +
                    " FROM idle_items" +
                    " WHERE borrow_requests.idle_id = idle_items.id" +
                    " AND borrow_requests.status = 'pending'" +
                    " AND idle_items.status = 'deleted'");
                log.info("  ✓ 清洗脏数据：已拒绝 {} 条孤儿借入申请", updated);
            }
        } catch (Exception e) {
            log.warn("  ✗ 清洗借入脏数据失败 — {}", e.getMessage());
        }
    }

    /** 清洗脏数据：拒绝那些关联求助已被删除的待审批帮助申请 */
    private void cleanupOrphanPendingHelpApps(JdbcTemplate j) {
        try {
            Integer count = j.queryForObject(
                "SELECT COUNT(*) FROM help_applications ha" +
                " JOIN help_requests hr ON hr.id = ha.help_id" +
                " WHERE ha.status = 'pending' AND hr.status = 'deleted'",
                Integer.class);
            if (count != null && count > 0) {
                int updated = j.update(
                    "UPDATE help_applications" +
                    " SET status = 'rejected'" +
                    " FROM help_requests" +
                    " WHERE help_applications.help_id = help_requests.id" +
                    " AND help_applications.status = 'pending'" +
                    " AND help_requests.status = 'deleted'");
                log.info("  ✓ 清洗脏数据：已拒绝 {} 条孤儿帮助申请", updated);
            }
        } catch (Exception e) {
            log.warn("  ✗ 清洗帮助脏数据失败 — {}", e.getMessage());
        }
    }

    /** 清洗脏数据：强制结束关联物品已删除的进行中借入申请 */
    private void cleanupOrphanActiveBorrows(JdbcTemplate j) {
        try {
            Integer count = j.queryForObject(
                "SELECT COUNT(*) FROM borrow_requests br" +
                " JOIN idle_items i ON i.id = br.idle_id" +
                " WHERE br.status = 'approved' AND i.status = 'deleted'",
                Integer.class);
            if (count != null && count > 0) {
                int updated = j.update(
                    "UPDATE borrow_requests" +
                    " SET status = 'returned', returned_at = NOW()" +
                    " FROM idle_items" +
                    " WHERE borrow_requests.idle_id = idle_items.id" +
                    " AND borrow_requests.status = 'approved'" +
                    " AND idle_items.status = 'deleted'");
                log.info("  ✓ 清洗脏数据：已强制结束 {} 条孤儿进行中借入", updated);
            }
        } catch (Exception e) {
            log.warn("  ✗ 清洗进行中借入脏数据失败 — {}", e.getMessage());
        }
    }

    /** 清洗脏数据：强制结束关联求助已删除的进行中帮助申请 */
    private void cleanupOrphanActiveHelpApps(JdbcTemplate j) {
        try {
            Integer count = j.queryForObject(
                "SELECT COUNT(*) FROM help_applications ha" +
                " JOIN help_requests hr ON hr.id = ha.help_id" +
                " WHERE ha.status = 'approved' AND hr.status = 'deleted'",
                Integer.class);
            if (count != null && count > 0) {
                int updated = j.update(
                    "UPDATE help_applications" +
                    " SET status = 'completed', completed_at = NOW()" +
                    " FROM help_requests" +
                    " WHERE help_applications.help_id = help_requests.id" +
                    " AND help_applications.status = 'approved'" +
                    " AND help_requests.status = 'deleted'");
                log.info("  ✓ 清洗脏数据：已强制结束 {} 条孤儿进行中帮助", updated);
            }
        } catch (Exception e) {
            log.warn("  ✗ 清洗进行中帮助脏数据失败 — {}", e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    // 迁移定义
    // ──────────────────────────────────────────────

    // MIGRATIONS 已清空：所有表已在 schema.sql 中以 BIGINT IDENTITY 重建。
    // 后续 schema 变更时在此追加新迁移。
    private static final List<Migration> MIGRATIONS = List.of(
        // 导出日志表 tenant_id 允许 NULL（super_admin 平台级导出）
        new Migration("export_logs.tenant_id DROP NOT NULL") {
            public boolean exists(JdbcTemplate j) { return isNullable(j, "export_logs", "tenant_id"); }
            public void apply(JdbcTemplate j) {
                j.execute("ALTER TABLE export_logs ALTER COLUMN tenant_id DROP NOT NULL");
            }
        },

        // 评分表增加互助感想文本字段
        addColumn("ratings", "feedback", "VARCHAR(500)"),

        // 导出日志表增加互助记录（技能求助）Sheet 计数
        addColumn("export_logs", "helps_count", "INTEGER NOT NULL DEFAULT 0"),

        /** 借用记录表增加归还完成时间专用字段，替代 updated_at 作为完成时间的语义 */
        new Migration("borrow_requests.returned_at TIMESTAMP + backfill") {
            public boolean exists(JdbcTemplate j) {
                return columnExists(j, "borrow_requests", "returned_at");
            }
            public void apply(JdbcTemplate j) {
                j.execute("ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS returned_at TIMESTAMP");
                // 存量已归还记录：用 updated_at 补齐 returned_at
                int updated = j.update(
                    "UPDATE borrow_requests SET returned_at = updated_at" +
                    " WHERE status = 'returned' AND returned_at IS NULL");
                log.info("  → 已补齐 {} 条存量记录的 returned_at", updated);
            }
        },

        /** 新建导出日志表 */
        new Migration("table: export_logs") {
            public boolean exists(JdbcTemplate j) {
                Integer c = j.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'export_logs'",
                    Integer.class);
                return c != null && c > 0;
            }
            public void apply(JdbcTemplate j) {
                j.execute("""
                    CREATE TABLE IF NOT EXISTS export_logs (
                        id               BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        admin_id         BIGINT       NOT NULL REFERENCES users(id),
                        tenant_id        BIGINT       NOT NULL REFERENCES tenants(id),
                        export_format    VARCHAR(10)  NOT NULL DEFAULT 'xlsx',
                        selected_options TEXT         NOT NULL,
                        date_range_start VARCHAR(10),
                        date_range_end   VARCHAR(10),
                        residents_count  INTEGER      NOT NULL DEFAULT 0,
                        posts_count      INTEGER      NOT NULL DEFAULT 0,
                        borrows_count    INTEGER      NOT NULL DEFAULT 0,
                        removals_count   INTEGER      NOT NULL DEFAULT 0,
                        ratings_count    INTEGER      NOT NULL DEFAULT 0,
                        file_name        VARCHAR(200) NOT NULL,
                        created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
                    )""");
            }
        },

        /** 清洗旧通知：下架导致的被拒通知，文案改为自然语序 + 标题改为"已失效" */
        new Migration("data: clean old delist rejection notification text") {
            public boolean exists(JdbcTemplate j) {
                Integer c = j.queryForObject(
                    "SELECT COUNT(*) FROM notifications WHERE content LIKE '%因求助下架被自动拒绝%'",
                    Integer.class);
                return c == null || c == 0;
            }
            public void apply(JdbcTemplate j) {
                int updated = j.update(
                    "UPDATE notifications SET type = 'help_rejected'," +
                    " title = '帮助申请已失效'," +
                    " content = regexp_replace(content," +
                    " '您对求助「(.+)」的帮助申请因求助下架被自动拒绝'," +
                    " '求助「\\1」已下架，您的帮助申请已自动失效')" +
                    " WHERE content LIKE '%因求助下架被自动拒绝%'");
                log.info("  → 已更新 {} 条旧通知文案", updated);
            }
        }
    );

    // ── 辅助方法 ──

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

    // ── 抽象基类 ──

    abstract static class Migration {
        final String description;
        Migration(String d) { this.description = d; }
        abstract boolean exists(JdbcTemplate j);
        abstract void apply(JdbcTemplate j);
    }
}
