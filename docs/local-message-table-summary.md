# 本地消息表方案总结

> 当前分支：`spring-boot3-jdk21`。本分支把 starter 基线升级到 JDK 21、Spring Boot 3.5.6、MySQL 8.x。
>
> 主要变化：Maven 使用 `--release 21` 编译；自动装配入口改为 Spring Boot 3 推荐的 `AutoConfiguration.imports`；MySQL 脚本使用 JSON 字段保存方法快照。

这份总结来自 happy-chat 项目里 `SecureInvoke` 的学习版本，并把它抽成更通用的 starter 思路。

## 要解决的问题

业务里经常会有这样的链路：

1. 开启数据库事务。
2. 写业务表，比如订单支付成功、好友申请通过。
3. 发送 MQ 或调用外部系统，通知其他模块处理。
4. 提交事务。

如果第 2 步成功但第 3 步失败，其他系统收不到通知；如果第 3 步先成功但第 4 步回滚，又会出现“消息发出去了，业务数据却不存在”的问题。

本地消息表的目标是：让“业务数据变更”和“待发送消息记录”在同一个数据库事务里提交，然后由后台任务保证消息最终被执行。

## happy-chat 里的核心链路

happy-chat 中的命名是 `SecureInvoke`：

1. 业务方法在事务内调用 `rocketMqProducer.sendSecureMsg(...)`。
2. `@SecureInvoke` 切面发现当前有 Spring 事务，不直接发送 RocketMQ。
3. 切面把类名、方法名、参数类型、参数值序列化成 JSON，写入 `secure_invoke_record`。
4. 注册 `TransactionSynchronization.afterCommit()`。
5. 外层事务提交后，afterCommit 异步调用原方法，真正发送 RocketMQ。
6. 如果发送失败，记录保留在表中，并更新失败原因、下次重试时间、重试次数。
7. `@Scheduled` 定时任务扫描到期记录，再次反射执行原方法。
8. 执行成功后删除本地消息记录；达到最大失败次数后标记为失败，交给人工排查或补偿。

## starter 的调整

这个仓库保留了原来的思想，但做了几处泛化：

1. 注解推荐名改为 `@LocalMessage`，旧名 `@SecureInvoke` 作为兼容注解保留。
2. 不绑定 RocketMQ。任何“事务提交后可靠执行”的 void 方法都可以使用。
3. 不绑定 MyBatis / MyBatis-Plus。底层只依赖 `JdbcTemplate`，更容易放进不同项目。
4. 配置项统一放在 `mini-local-message.*` 下。
5. 表名会做安全校验，只允许字母、数字、下划线，避免配置被拼进 SQL 后产生风险。

## 语义边界

本方案提供的是“至少一次执行”，不是“精确一次执行”。

也就是说，starter 能尽力保证方法最终被调用，但网络抖动、进程重启、集群并发扫描等情况下，业务方法可能被重复调用。因此被 `@LocalMessage` 标记的方法必须按业务唯一键做好幂等，比如：

1. MQ 消息带业务 key，消费端用 key 去重。
2. 外部通知记录请求流水号，重复请求直接返回成功。
3. 写状态类数据时使用唯一索引或状态机防重。

## 适合场景

1. 事务提交后发送 RocketMQ / RabbitMQ / Kafka。
2. 事务提交后调用第三方接口。
3. 事务提交后刷新搜索索引、缓存、通知中心。
4. 对主流程延迟不敏感，但需要失败重试的副作用动作。

## 不适合场景

1. 调用方必须马上拿到返回值的方法。
2. 参数无法被 Jackson 序列化和反序列化的方法。
3. 无法幂等的外部副作用。
4. 没有 Spring 事务的纯内存流程。
