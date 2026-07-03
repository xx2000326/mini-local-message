package com.xx.minilocalmessage.domain;

import java.util.Date;

/**
 * 本地消息表记录。
 *
 * <p>这不是业务消息体，而是“需要可靠执行的一次方法调用”。业务消息体会作为方法参数，
 * 被放进 {@link LocalMessageInvocation#argumentJson}。</p>
 */
public class LocalMessageRecord {

    /**
     * 等待执行或等待下次重试。
     */
    public static final int STATUS_WAIT = 1;

    /**
     * 已达到最大失败次数，等待人工处理。
     */
    public static final int STATUS_FAIL = 2;

    private Long id;
    private LocalMessageInvocation invocation;
    private int status = STATUS_WAIT;
    private Date nextRetryTime = new Date();
    private int retryTimes = 0;
    private int maxRetryTimes = 3;
    private String failReason;
    private Date createTime = new Date();
    private Date updateTime = new Date();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalMessageInvocation getInvocation() {
        return invocation;
    }

    public void setInvocation(LocalMessageInvocation invocation) {
        this.invocation = invocation;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Date getNextRetryTime() {
        return nextRetryTime;
    }

    public void setNextRetryTime(Date nextRetryTime) {
        this.nextRetryTime = nextRetryTime;
    }

    public int getRetryTimes() {
        return retryTimes;
    }

    public void setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }

    public int getMaxRetryTimes() {
        return maxRetryTimes;
    }

    public void setMaxRetryTimes(int maxRetryTimes) {
        this.maxRetryTimes = maxRetryTimes;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
