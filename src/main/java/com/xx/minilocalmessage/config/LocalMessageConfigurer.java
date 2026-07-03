package com.xx.minilocalmessage.config;

import java.util.concurrent.Executor;

/**
 * 允许业务项目覆盖 starter 的少量扩展点。
 *
 * <p>当前主要用于自定义执行本地消息的线程池。业务项目可以声明一个 Bean：</p>
 *
 * <pre>
 * {@code
 * @Bean
 * public LocalMessageConfigurer localMessageConfigurer(Executor myExecutor) {
 *     return () -> myExecutor;
 * }
 * }
 * </pre>
 */
public interface LocalMessageConfigurer {

    /**
     * 返回用于执行本地消息的线程池。
     *
     * <p>返回 null 时，starter 会使用默认的 ThreadPoolTaskExecutor。</p>
     */
    default Executor getLocalMessageExecutor() {
        return null;
    }
}
