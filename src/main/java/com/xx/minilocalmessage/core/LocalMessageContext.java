package com.xx.minilocalmessage.core;

/**
 * 本地消息重放上下文。
 *
 * <p>本地消息最终还是通过反射调用原来的业务方法。若原方法也带有 {@code @LocalMessage}，
 * 调用时会再次经过 AOP 切面。这个 ThreadLocal 用来告诉切面“当前是重放阶段”，从而直接执行原方法。</p>
 */
public final class LocalMessageContext {

    private static final ThreadLocal<Boolean> REPLAYING = new ThreadLocal<>();

    private LocalMessageContext() {
    }

    public static boolean isReplaying() {
        return Boolean.TRUE.equals(REPLAYING.get());
    }

    public static void markReplaying() {
        REPLAYING.set(Boolean.TRUE);
    }

    public static void clear() {
        REPLAYING.remove();
    }
}
