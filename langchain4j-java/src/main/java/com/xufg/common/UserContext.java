package com.xufg.common;

/**
 * 当前登录用户上下文。
 */
public final class UserContext {

    /** 保存当前用户 ID 的线程变量。 */
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    /** 保存当前用户名的线程变量。 */
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 写入当前用户信息。
     */
    public static void set(Long userId, String username) {
        USER_ID.set(userId);
        USERNAME.set(username);
    }

    /**
     * 获取当前用户 ID。
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 获取当前用户名。
     */
    public static String getUsername() {
        return USERNAME.get();
    }

    /**
     * 清理线程变量， 避免线程池串号。
     */
    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
    }
}
