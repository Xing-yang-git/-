package com.platform.model.entity.column;

/**
 * sensitive_word 表字段名常量 — 与数据库 schema（db/alter.sql）严格一致。
 * <p>所有使用 sensitive_word 表字段名的 JPA 注解必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class SensitiveWordsColumn {

    /** 工具类，禁止实例化 */
    private SensitiveWordsColumn() {
    }

    /** 表名 */
    public static final String TABLE_NAME = "sensitive_word";

    /** 敏感词 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 敏感词原文（唯一） */
    public static final String COL_WORD = "word";
    /** 状态：ENABLED(启用)/DISABLED(停用) */
    public static final String COL_STATUS = "status";
    /** 创建时间 */
    public static final String COL_CREATED_AT = "created_at";
    /** 更新时间 */
    public static final String COL_UPDATED_AT = "updated_at";
}
