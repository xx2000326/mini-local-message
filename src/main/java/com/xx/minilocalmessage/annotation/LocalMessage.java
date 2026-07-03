package com.xx.minilocalmessage.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个需要“事务提交后可靠执行”的方法。
 *
 * <p>典型用法是把发送 MQ、通知远端服务、刷新外部缓存这类副作用方法加上该注解。
 * 当方法在 Spring 事务内被调用时，starter 不会立刻执行原方法，而是先把方法快照写入本地消息表；
 * 等外层事务提交成功后，再执行原方法。执行失败时，定时任务会按退避策略继续重试。</p>
 *
 * <p>被标记的方法应设计为 void。因为事务内调用时，真正的方法执行发生在事务提交之后，
 * 调用方拿不到同步返回值。</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LocalMessage {

    /**
     * 最大失败次数。
     *
     * <p>比如默认值 3 表示：事务提交后的首次执行失败算第 1 次，后续最多再重试到第 3 次；
     * 第 3 次仍失败后，记录会被标记为最终失败，等待人工排查或业务补偿。</p>
     */
    int maxRetryTimes() default 3;

    /**
     * 事务提交后是否异步执行原方法。
     *
     * <p>默认异步执行，适合“业务主流程已经完成，通知动作可以稍后完成”的场景。
     * 如果希望 afterCommit 阶段同步执行，可以设置为 false。</p>
     */
    boolean async() default true;
}
