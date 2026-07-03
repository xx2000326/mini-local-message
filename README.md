# mini-local-message

> 当前分支：`spring-boot3-jdk21`。适配 JDK 21、Spring Boot 3.5.6、MySQL 8.x。
>
> Spring Boot 3 使用 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 做自动装配；老的 `spring.factories` 在本分支只保留为空注释，方便看迁移历史。

一个轻量级 Spring Boot starter，用来复用“本地消息表 / 可靠执行”方案。

它参考了 `E:\happy-chat\happy-chat\happy-chat-boot\happy-chat-boot` 中学过的 `SecureInvoke` 方案：业务事务内先把“要执行的方法”写入本地消息表，事务提交后再执行真实方法；如果失败，后台定时任务按退避策略重试。

## 方案一句话

把“业务数据”和“待发送消息记录”放在同一个数据库事务里提交。事务提交后再发 MQ 或调用外部系统，失败就靠本地消息表重试，避免业务提交成功但消息丢失。

## 快速接入

先在项目里安装当前 starter：

```bash
mvn clean install
```

业务项目引入依赖：

```xml
<dependency>
    <groupId>com.xx</groupId>
    <artifactId>mini-local-message-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

执行建表脚本：

```sql
-- 见 src/main/resources/db/mini_local_message_mysql.sql
CREATE TABLE IF NOT EXISTS `local_message_record` (...);
```

在需要可靠执行的方法上加注解：

```java
@Component
public class OrderMessageProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public OrderMessageProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @LocalMessage(maxRetryTimes = 3)
    public void sendOrderPaidMessage(Long orderId) {
        // 这里写真正的 MQ 发送逻辑。
        // 如果发送失败，starter 会保留本地消息记录并等待下次重试。
        rocketMQTemplate.convertAndSend("order-paid-topic", orderId);
    }
}
```

在业务事务里调用：

```java
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMessageProducer producer;

    public OrderService(OrderRepository orderRepository, OrderMessageProducer producer) {
        this.orderRepository = orderRepository;
        this.producer = producer;
    }

    @Transactional
    public void pay(Long orderId) {
        orderRepository.markPaid(orderId);

        // 当前存在 Spring 事务时，这里不会立刻发 MQ。
        // starter 会先落本地消息表，等事务提交后再执行 sendOrderPaidMessage。
        producer.sendOrderPaidMessage(orderId);
    }
}
```

## 配置项

```yaml
mini-local-message:
  enabled: true
  table-name: local_message_record
  retry-cron: "*/5 * * * * ?"
  retry-fetch-size: 50
  retry-delay-seconds: 120
  retry-multiplier: 2.0
  max-retry-delay-seconds: 3600
  executor:
    core-pool-size: 2
    max-pool-size: 4
    queue-capacity: 1000
    thread-name-prefix: local-message-
```

## 关键约束

被 `@LocalMessage` 标记的方法建议满足这些条件：

1. 返回值必须是 `void`。
2. 参数必须能被 Jackson 序列化和反序列化。
3. 方法要能幂等，因为本地消息表保证的是“至少一次执行”，不是“精确一次执行”。
4. 必须通过 Spring Bean 调用，不能在同一个类里 self-invocation。
5. 没有 Spring 事务时，方法会直接执行，不会落本地消息表。

## 和 happy-chat 版本的关系

happy-chat 里的核心类是 `SecureInvoke`、`SecureInvokeAspect`、`SecureInvokeService`、`SecureInvokeRecord`。这个 starter 保留了同样的执行链路：

1. AOP 拦截注解方法。
2. 事务内把方法快照写入本地消息表。
3. 注册 `afterCommit` 回调。
4. 事务提交后执行原方法。
5. 失败记录保留并定时重试。
6. 成功删除记录，最终失败保留原因。

新项目推荐使用 `@LocalMessage`。如果你从 happy-chat 迁移代码，也可以临时继续使用兼容注解 `@SecureInvoke`。

更详细的学习总结见 [docs/local-message-table-summary.md](docs/local-message-table-summary.md)。
