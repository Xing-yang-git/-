package com.platform.common;

/**
 * 知识库条目分类常量 — knowledge_items.category 字段的唯一合法取值。
 *
 * <p>与 B端 admin/src/utils/constants.ts 的 KNOWLEDGE_CATEGORY 保持一致。
 * 值不可修改；新增分类需前后端同步。</p>
 */
public final class KnowledgeCategory {

    /** 工具类，禁止实例化 */
    private KnowledgeCategory() {
    }

    /** 规章制度（小区管理规定、垃圾投放、装修时段等） */
    public static final String RULES = "rules";

    /** 服务手册（物业联系方式、报修渠道、FAQ） */
    public static final String SERVICE = "service";

    /** 平台帮助（注册认证、发布/借入/求助流程、AI 审核规则） */
    public static final String HELP = "help";

    /** 办事指南（居住证、装修备案、搬家出入证等） */
    public static final String GUIDE = "guide";
}
