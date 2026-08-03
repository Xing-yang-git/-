package com.platform.model.entity.column;

/**
 * knowledge_items 表字段名常量 — 与数据库 schema（db/alter.sql）严格一致。
 * <p>所有使用 knowledge_items 表字段名的 JPA 注解必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class KnowledgeItemsColumn {

    /** 工具类，禁止实例化 */
    private KnowledgeItemsColumn() {
    }

    /** 表名 */
    public static final String TABLE_NAME = "knowledge_items";

    /** 知识条目 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 所属小区 ID，外键 → tenants.id */
    public static final String COL_TENANT_ID = "tenant_id";
    /** 分类：rules(规章制度)/service(服务手册)/help(平台帮助)/guide(办事指南) */
    public static final String COL_CATEGORY = "category";
    /** 条目标题 */
    public static final String COL_TITLE = "title";
    /** 条目正文 */
    public static final String COL_CONTENT = "content";
    /** 来源文档名 */
    public static final String COL_SOURCE = "source";
    /** 逗号分隔标签 */
    public static final String COL_TAGS = "tags";
    /** 1024 维语义向量字面量 */
    public static final String COL_EMBEDDING = "embedding";
    /** 状态：online(启用)/offline(停用) */
    public static final String COL_STATUS = "status";
    /** 录入管理员用户 ID */
    public static final String COL_CREATED_BY = "created_by";
    /** 创建时间 */
    public static final String COL_CREATED_AT = "created_at";
    /** 更新时间 */
    public static final String COL_UPDATED_AT = "updated_at";
}
