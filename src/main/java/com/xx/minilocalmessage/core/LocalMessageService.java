package com.xx.minilocalmessage.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xx.minilocalmessage.config.LocalMessageProperties;
import com.xx.minilocalmessage.domain.LocalMessageInvocation;
import com.xx.minilocalmessage.domain.LocalMessageRecord;
import com.xx.minilocalmessage.repository.LocalMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 本地消息表的核心执行器。
 *
 * <p>它负责三件事：</p>
 * <p>1. 在业务事务内保存方法快照；</p>
 * <p>2. 在事务提交后执行真实方法；</p>
 * <p>3. 对失败记录做定时重试，直到成功删除或达到最大失败次数。</p>
 */
public class LocalMessageService implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(LocalMessageService.class);

    private final LocalMessageRepository repository;
    private final LocalMessageProperties properties;
    private final Executor executor;
    private final ObjectMapper objectMapper;

    private ApplicationContext applicationContext;

    public LocalMessageService(LocalMessageRepository repository,
                               LocalMessageProperties properties,
                               Executor executor,
                               ObjectMapper objectMapper) {
        this.repository = repository;
        this.properties = properties;
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 保存本地消息，并把真实执行注册到事务提交之后。
     *
     * <p>只有业务事务提交成功，afterCommit 才会触发；如果业务事务回滚，
     * 本地消息记录也会一起回滚，不会产生“业务失败但消息发出”的问题。</p>
     */
    public void recordAfterCommit(LocalMessageRecord record, boolean async) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Local message must be recorded inside a Spring transaction");
        }

        repository.save(record);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (async) {
                    executeAsync(record);
                } else {
                    execute(record);
                }
            }
        });
    }

    /**
     * 定时扫描到期的 WAIT 记录。
     *
     * <p>本 starter 提供的是至少一次执行语义。集群部署时，不同节点可能短时间内重复执行同一条记录，
     * 因此被调用的方法仍然要按业务唯一键做好幂等。</p>
     */
    @Scheduled(cron = "${mini-local-message.retry-cron:*/5 * * * * ?}")
    public void retryReadyRecords() {
        if (!properties.isEnabled()) {
            return;
        }
        List<LocalMessageRecord> records = repository.findReadyToRetry(new Date(), properties.getRetryFetchSize());
        for (LocalMessageRecord record : records) {
            executeAsync(record);
        }
    }

    public Date calculateNextRetryTime(int failedTimes) {
        int retryIndex = Math.max(failedTimes - 1, 0);
        double delay = properties.getRetryDelaySeconds() * Math.pow(properties.getRetryMultiplier(), retryIndex);
        long boundedDelay = Math.min((long) delay, properties.getMaxRetryDelaySeconds());
        return new Date(System.currentTimeMillis() + boundedDelay * 1000L);
    }

    public void executeAsync(LocalMessageRecord record) {
        try {
            executor.execute(() -> execute(record));
        } catch (RuntimeException ex) {
            log.warn("Submit local message execution failed, record will be retried by scheduler. id={}", record.getId(), ex);
        }
    }

    public void execute(LocalMessageRecord record) {
        try {
            invokeOriginalMethod(record.getInvocation());
            repository.deleteById(record.getId());
        } catch (Throwable ex) {
            Throwable root = unwrapInvocationException(ex);
            log.error("Local message execution failed. id={}", record.getId(), root);
            markRetryOrFail(record, root);
        }
    }

    private void invokeOriginalMethod(LocalMessageInvocation invocation) throws Throwable {
        ClassLoader classLoader = ClassUtils.getDefaultClassLoader();
        Class<?> beanType = ClassUtils.forName(invocation.getBeanTypeName(), classLoader);
        Object bean = applicationContext.getBean(beanType);

        List<String> parameterTypeNames = invocation.getParameterTypeNames();
        Class<?>[] parameterTypes = new Class<?>[parameterTypeNames.size()];
        for (int i = 0; i < parameterTypeNames.size(); i++) {
            parameterTypes[i] = ClassUtils.forName(parameterTypeNames.get(i), classLoader);
        }

        Method method = findMethod(invocation, bean, parameterTypes, classLoader);
        Object[] args = readArguments(invocation, parameterTypes);

        try {
            LocalMessageContext.markReplaying();
            ReflectionUtils.makeAccessible(method);
            method.invoke(bean, args);
        } finally {
            LocalMessageContext.clear();
        }
    }

    private Method findMethod(LocalMessageInvocation invocation,
                              Object bean,
                              Class<?>[] parameterTypes,
                              ClassLoader classLoader) throws ClassNotFoundException {
        Class<?> methodClass = ClassUtils.forName(invocation.getMethodClassName(), classLoader);
        Method method = ReflectionUtils.findMethod(methodClass, invocation.getMethodName(), parameterTypes);
        if (method != null) {
            return method;
        }

        Class<?> targetClass = AopUtils.getTargetClass(bean);
        method = ReflectionUtils.findMethod(targetClass, invocation.getMethodName(), parameterTypes);
        if (method != null) {
            return method;
        }

        throw new IllegalStateException("Cannot find local message method: "
                + invocation.getMethodClassName() + "#" + invocation.getMethodName());
    }

    private Object[] readArguments(LocalMessageInvocation invocation, Class<?>[] parameterTypes) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(invocation.getArgumentJson());
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            args[i] = objectMapper.treeToValue(jsonNode.get(i), parameterTypes[i]);
        }
        return args;
    }

    private void markRetryOrFail(LocalMessageRecord record, Throwable ex) {
        int failedTimes = record.getRetryTimes() + 1;
        String failReason = buildFailReason(ex);
        if (failedTimes >= record.getMaxRetryTimes()) {
            repository.markFail(record.getId(), failedTimes, failReason, new Date());
            return;
        }
        repository.markWaitRetry(record.getId(), failedTimes, calculateNextRetryTime(failedTimes + 1), failReason, new Date());
    }

    private String buildFailReason(Throwable ex) {
        String message = ex.getClass().getName() + ": " + ex.getMessage();
        if (message.length() > 1800) {
            return message.substring(0, 1800);
        }
        return message;
    }

    private Throwable unwrapInvocationException(Throwable ex) {
        if (ex instanceof InvocationTargetException && ((InvocationTargetException) ex).getTargetException() != null) {
            return ((InvocationTargetException) ex).getTargetException();
        }
        return ex;
    }
}
