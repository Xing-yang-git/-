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
    }

    // ──────────────────────────────────────────────
    // 迁移定义
    // ──────────────────────────────────────────────

    // MIGRATIONS 已清空：所有表已在 schema.sql 中以 BIGINT IDENTITY 重建。
    // 后续 schema 变更时在此追加新迁移。
    private static final List<Migration> MIGRATIONS = List.of(
        // 评分表增加互助感想文本字段
        addColumn("ratings", "feedback", "VARCHAR(500)"),

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
