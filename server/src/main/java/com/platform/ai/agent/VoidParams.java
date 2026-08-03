package com.platform.ai.agent;

/**
 * 无参数工具的空参数 POJO — deepseek 要求工具参数 schema 必须是 object 类型，
 * 空 record 生成 {"type":"object","properties":{}} 满足要求（PoC 已验证 String 会被拒）。
 */
public record VoidParams() {
}
