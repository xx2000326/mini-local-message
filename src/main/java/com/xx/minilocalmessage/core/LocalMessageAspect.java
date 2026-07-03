package com.xx.minilocalmessage.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xx.minilocalmessage.annotation.LocalMessage;
import com.xx.minilocalmessage.annotation.SecureInvoke;
import com.xx.minilocalmessage.config.LocalMessageProperties;
import com.xx.minilocalmessage.domain.LocalMessageInvocation;
import com.xx.minilocalmessage.domain.LocalMessageRecord;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 拦截被 {@link LocalMessage} 或 {@link SecureInvoke} 标记的方法。
 *
 * <p>切面只在“当前线程存在 Spring 事务”时接管调用。没有事务时，方法会正常执行，
 * 这样可以避免测试代码或非事务场景被意外改成异步行为。</p>
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class LocalMessageAspect {

    private final LocalMessageService localMessageService;
    private final LocalMessageProperties properties;
    private final ObjectMapper objectMapper;

    public LocalMessageAspect(LocalMessageService localMessageService,
                              LocalMessageProperties properties,
                              ObjectMapper objectMapper) {
        this.localMessageService = localMessageService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(localMessage)")
    public Object aroundLocalMessage(ProceedingJoinPoint joinPoint, LocalMessage localMessage) throws Throwable {
        LocalMessageOptions options = new LocalMessageOptions(localMessage.maxRetryTimes(), localMessage.async());
        return around(joinPoint, options);
    }

    @Around("@annotation(secureInvoke)")
    public Object aroundSecureInvoke(ProceedingJoinPoint joinPoint, SecureInvoke secureInvoke) throws Throwable {
        LocalMessageOptions options = new LocalMessageOptions(secureInvoke.maxRetryTimes(), secureInvoke.async());
        return around(joinPoint, options);
    }

    private Object around(ProceedingJoinPoint joinPoint, LocalMessageOptions options) throws Throwable {
        boolean inTransaction = TransactionSynchronizationManager.isActualTransactionActive();

        // 重放本地消息时会再次进入同一个方法，必须放行，避免递归落库。
        if (LocalMessageContext.isReplaying() || !inTransaction) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        if (signature.getReturnType() != Void.TYPE) {
            throw new IllegalStateException("@LocalMessage method must return void: " + signature.toLongString());
        }

        LocalMessageInvocation invocation = buildInvocation(joinPoint, signature);
        LocalMessageRecord record = new LocalMessageRecord();
        record.setInvocation(invocation);
        record.setStatus(LocalMessageRecord.STATUS_WAIT);
        record.setRetryTimes(0);
        record.setMaxRetryTimes(options.getMaxRetryTimes());
        record.setNextRetryTime(localMessageService.calculateNextRetryTime(1));
        Date now = new Date();
        record.setCreateTime(now);
        record.setUpdateTime(now);

        localMessageService.recordAfterCommit(record, options.isAsync());
        return null;
    }

    private LocalMessageInvocation buildInvocation(ProceedingJoinPoint joinPoint,
                                                   MethodSignature signature) throws JsonProcessingException {
        Method signatureMethod = signature.getMethod();
        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(joinPoint.getTarget());
        Method specificMethod = AopUtils.getMostSpecificMethod(signatureMethod, targetClass);

        List<String> parameterTypeNames = new ArrayList<>();
        for (Class<?> parameterType : specificMethod.getParameterTypes()) {
            parameterTypeNames.add(parameterType.getName());
        }

        LocalMessageInvocation invocation = new LocalMessageInvocation();
        invocation.setBeanTypeName(resolveBeanTypeName(signatureMethod, targetClass));
        invocation.setMethodClassName(resolveMethodClassName(signatureMethod, specificMethod, targetClass));
        invocation.setMethodName(specificMethod.getName());
        invocation.setParameterTypeNames(parameterTypeNames);
        invocation.setArgumentJson(objectMapper.writeValueAsString(joinPoint.getArgs()));
        return invocation;
    }

    private String resolveBeanTypeName(Method signatureMethod, Class<?> targetClass) {
        Class<?> declaringClass = signatureMethod.getDeclaringClass();
        if (declaringClass.isInterface()) {
            return declaringClass.getName();
        }
        return targetClass.getName();
    }

    private String resolveMethodClassName(Method signatureMethod, Method specificMethod, Class<?> targetClass) {
        if (signatureMethod.getDeclaringClass().isInterface()) {
            return signatureMethod.getDeclaringClass().getName();
        }
        if (specificMethod.getDeclaringClass() != null) {
            return specificMethod.getDeclaringClass().getName();
        }
        return targetClass.getName();
    }

    /**
     * 注解参数的内部统一模型，避免 LocalMessage 和 SecureInvoke 两套逻辑重复。
     */
    private static class LocalMessageOptions {
        private final int maxRetryTimes;
        private final boolean async;

        LocalMessageOptions(int maxRetryTimes, boolean async) {
            this.maxRetryTimes = maxRetryTimes;
            this.async = async;
        }

        int getMaxRetryTimes() {
            return maxRetryTimes;
        }

        boolean isAsync() {
            return async;
        }
    }
}
