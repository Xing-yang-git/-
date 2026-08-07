package com.platform.model.entity.column;

/**
 * agent_memory_segments 表字段名常量 — 与数据库 schema（db/alter.sql）严格一致。
 * <p>所有使用 agent_memory_segments 表字段名的 JPA 注解必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class AgentMemorySegmentsColumn {

    /** 工具类，禁止实例化 */
    private AgentMemorySegmentsColumn() {
    }

    /** 表名 */
    public static final String TABLE_NAME = "agent_memory_segments";

    /** 压缩段 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 住户用户 ID，外键 → users.id（记忆检索强制按用户过滤） */
    public static final String COL_USER_ID = "user_id";
    /** 所属小区 ID，外键 → tenants.id */
    public static final String COL_TENANT_ID = "tenant_id";
    /** 会话级 id（多段归档共享同一会话，供记忆检索与联动删除） */
    public static final String COL_CONVERSATION_ID = "conversation_id";
    /** 对应归档行 id（供回溯原始消息/补压） */
    public static final String COL_ARCHIVE_ROW_ID = "archive_row_id";
    /** 压缩段序号（会话内递增，从 1 起） */
    public static final String COL_SEGMENT_NO = "segment_no";
    /** 标题（≤20 字） */
    public static final String COL_TITLE = "title";
    /** 摘要（目标 100~200 字，上限 300 字） */
    public static final String COL_SUMMARY = "summary";
    /** 摘要向量（embedding-3，1024 维） */
    public static final String COL_EMBEDDING = "embedding";
    /** 状态：SUCCESS(成功)/RETRY(待补压)，引用 {@link com.platform.common.MemorySegmentStatus} */
    public static final String COL_STATUS = "status";
    /** 创建时间 */
    public static final String COL_CREATED_AT = "created_at";
}
