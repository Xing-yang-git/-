package com.platform.model.entity.column;

/**
 * knowledge_documents 表字段名常量 — 与数据库 schema（db/alter.sql）严格一致。
 * <p>所有使用 knowledge_documents 表字段名的 JPA 注解必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class KnowledgeDocumentsColumn {

    /** 工具类，禁止实例化 */
    private KnowledgeDocumentsColumn() {
    }

    /** 表名 */
    public static final String TABLE_NAME = "knowledge_documents";

    /** 源文档 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 所属小区 ID，外键 → tenants.id */
    public static final String COL_TENANT_ID = "tenant_id";
    /** 分类：rules/service/help/guide */
    public static final String COL_CATEGORY = "category";
    /** 原始文件名（含扩展名） */
    public static final String COL_FILE_NAME = "file_name";
    /** 文件类型：md/txt/pdf/docx/xlsx/csv */
    public static final String COL_FILE_TYPE = "file_type";
    /** 文件 MD5（重复上传拦截） */
    public static final String COL_FILE_MD5 = "file_md5";
    /** 展示来源名 */
    public static final String COL_SOURCE = "source";
    /** 逗号分隔标签 */
    public static final String COL_TAGS = "tags";
    /** 相对 file.knowledge-dir 的落盘路径 */
    public static final String COL_STORAGE_PATH = "storage_path";
    /** 处理状态：parsing/ready/failed */
    public static final String COL_STATUS = "status";
    /** 生成的切片数 */
    public static final String COL_CHUNK_COUNT = "chunk_count";
    /** 失败原因 / 部分内容未处理警告 */
    public static final String COL_ERROR_MESSAGE = "error_message";
    /** 上传管理员用户 ID */
    public static final String COL_CREATED_BY = "created_by";
    /** 创建时间 */
    public static final String COL_CREATED_AT = "created_at";
    /** 更新时间 */
    public static final String COL_UPDATED_AT = "updated_at";
}
