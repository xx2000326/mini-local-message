package com.xx.minilocalmessage.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 兼容 happy-chat 项目里学习过的 SecureInvoke 命名。
 *
 * <p>新项目推荐直接使用 {@link LocalMessage}，语义更贴近本地消息表模式。
 * 这个注解保留下来，是为了从旧项目迁移代码时可以少改一些业务方法。</p>
 */
@Deprecated
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SecureInvoke {

    /**
     * 最大失败次数，含事务提交后的首次执行失败。
     */
    int maxRetryTimes() default 3;

    /**
     * 事务提交后是否异步执行原方法。
     */
    boolean async() default true;
}
