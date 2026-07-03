package com.xx.minilocalmessage.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次可靠方法调用的快照。
 *
 * <p>本地消息表里不直接存 Java 对象，而是把这些字段序列化成 JSON。
 * 重试任务读取 JSON 后，就能还原出“调用哪个 Spring Bean 的哪个方法，以及传入哪些参数”。</p>
 */
public class LocalMessageInvocation {

    /**
     * 用于从 Spring 容器中获取 Bean 的类型名称。
     */
    private String beanTypeName;

    /**
     * 用于反射查找方法的类名。多数情况下和 beanTypeName 一致；
     * 接口代理场景下，它可能是接口名。
     */
    private String methodClassName;

    /**
     * 方法名。
     */
    private String methodName;

    /**
     * 参数类型名称列表，用来区分重载方法。
     */
    private List<String> parameterTypeNames = new ArrayList<>();

    /**
     * 参数值 JSON。单独存成字符串，可以保留 Object[] 的原始数组结构。
     */
    private String argumentJson;

    public String getBeanTypeName() {
        return beanTypeName;
    }

    public void setBeanTypeName(String beanTypeName) {
        this.beanTypeName = beanTypeName;
    }

    public String getMethodClassName() {
        return methodClassName;
    }

    public void setMethodClassName(String methodClassName) {
        this.methodClassName = methodClassName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public List<String> getParameterTypeNames() {
        return parameterTypeNames;
    }

    public void setParameterTypeNames(List<String> parameterTypeNames) {
        this.parameterTypeNames = parameterTypeNames;
    }

    public String getArgumentJson() {
        return argumentJson;
    }

    public void setArgumentJson(String argumentJson) {
        this.argumentJson = argumentJson;
    }
}
