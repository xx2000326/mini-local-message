package com.xx.minilocalmessage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * starter 配置项。
 *
 * <p>配置前缀是 {@code mini-local-message}。默认值尽量贴近 happy-chat 里的学习版本：
 * 每 5 秒扫描一次，首次失败后约 2 分钟重试，最多失败 3 次。</p>
 */
@ConfigurationProperties(prefix = "mini-local-message")
public class LocalMessageProperties {

    /**
     * 是否启用本地消息表 starter。
     */
    private boolean enabled = true;

    /**
     * 本地消息表表名。为了避免把配置直接拼成危险 SQL，只允许字母、数字和下划线。
     */
    private String tableName = "local_message_record";

    /**
     * 扫描待重试记录的 cron 表达式。
     */
    private String retryCron = "*/5 * * * * ?";

    /**
     * 每次调度最多拉取多少条待重试记录。
     */
    private int retryFetchSize = 50;

    /**
     * 事务提交后首次执行失败时，下一次重试需要等待的秒数。
     */
    private long retryDelaySeconds = 120L;

    /**
     * 失败退避倍率。默认 2 表示 2 分钟、4 分钟、8 分钟这样逐步拉开间隔。
     */
    private double retryMultiplier = 2.0D;

    /**
     * 单次重试等待的最大秒数，避免倍率过大导致等待时间不可控。
     */
    private long maxRetryDelaySeconds = 3600L;

    /**
     * 默认线程池配置。
     */
    private ExecutorProperties executor = new ExecutorProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getRetryCron() {
        return retryCron;
    }

    public void setRetryCron(String retryCron) {
        this.retryCron = retryCron;
    }

    public int getRetryFetchSize() {
        return retryFetchSize;
    }

    public void setRetryFetchSize(int retryFetchSize) {
        this.retryFetchSize = retryFetchSize;
    }

    public long getRetryDelaySeconds() {
        return retryDelaySeconds;
    }

    public void setRetryDelaySeconds(long retryDelaySeconds) {
        this.retryDelaySeconds = retryDelaySeconds;
    }

    public double getRetryMultiplier() {
        return retryMultiplier;
    }

    public void setRetryMultiplier(double retryMultiplier) {
        this.retryMultiplier = retryMultiplier;
    }

    public long getMaxRetryDelaySeconds() {
        return maxRetryDelaySeconds;
    }

    public void setMaxRetryDelaySeconds(long maxRetryDelaySeconds) {
        this.maxRetryDelaySeconds = maxRetryDelaySeconds;
    }

    public ExecutorProperties getExecutor() {
        return executor;
    }

    public void setExecutor(ExecutorProperties executor) {
        this.executor = executor;
    }

    /**
     * 默认异步执行线程池参数。
     */
    public static class ExecutorProperties {
        private int corePoolSize = 2;
        private int maxPoolSize = 4;
        private int queueCapacity = 1000;
        private String threadNamePrefix = "local-message-";

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }

        public void setThreadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }
    }
}
