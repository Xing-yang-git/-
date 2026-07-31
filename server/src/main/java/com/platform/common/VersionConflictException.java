package com.platform.common;

/**
 * 版本冲突异常 — 管理员操作时检测到数据已被其他管理员或用户修改。
 *
 * <p>当管理员提交操作（通过/驳回/下架）时，后端对比请求中的 updatedAt 与数据库当前值。
 * 若不等，说明数据在管理员查看后被他人修改，抛出此异常。
 * 前端收到 HTTP 409 后应提示用户并刷新最新数据。</p>
 */
public class VersionConflictException extends RuntimeException {

    public VersionConflictException(String message) {
        super(message);
    }
}
