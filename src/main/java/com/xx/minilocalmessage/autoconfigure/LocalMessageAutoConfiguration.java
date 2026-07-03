package com.xx.minilocalmessage.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xx.minilocalmessage.config.LocalMessageConfigurer;
import com.xx.minilocalmessage.config.LocalMessageProperties;
import com.xx.minilocalmessage.core.LocalMessageAspect;
import com.xx.minilocalmessage.core.LocalMessageService;
import com.xx.minilocalmessage.repository.LocalMessageRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;

/**
 * 本地消息表 starter 的自动装配入口。
 *
 * <p>应用只要引入 starter、准备好 JdbcTemplate 和本地消息表，就会自动启用
 * {@code @LocalMessage} 切面、记录仓库、可靠执行服务和重试调度任务。</p>
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(LocalMessageProperties.class)
@ConditionalOnClass({JdbcTemplate.class, ProceedingJoinPoint.class, TransactionSynchronizationManager.class})
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "mini-local-message", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(JdbcTemplateAutoConfiguration.class)
public class LocalMessageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LocalMessageRepository localMessageRepository(JdbcTemplate jdbcTemplate,
                                                         ObjectProvider<ObjectMapper> objectMapperProvider,
                                                         LocalMessageProperties properties) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new LocalMessageRepository(jdbcTemplate, objectMapper, properties);
    }

    @Bean(name = "localMessageExecutor")
    @ConditionalOnMissingBean(name = "localMessageExecutor")
    public Executor localMessageExecutor(ObjectProvider<LocalMessageConfigurer> configurers,
                                         LocalMessageProperties properties) {
        LocalMessageConfigurer configurer = configurers.orderedStream().findFirst().orElse(null);
        if (configurer != null && configurer.getLocalMessageExecutor() != null) {
            return configurer.getLocalMessageExecutor();
        }

        LocalMessageProperties.ExecutorProperties executorProperties = properties.getExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(executorProperties.getCorePoolSize());
        executor.setMaxPoolSize(executorProperties.getMaxPoolSize());
        executor.setQueueCapacity(executorProperties.getQueueCapacity());
        executor.setThreadNamePrefix(executorProperties.getThreadNamePrefix());
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalMessageService localMessageService(LocalMessageRepository repository,
                                                   LocalMessageProperties properties,
                                                   Executor localMessageExecutor,
                                                   ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new LocalMessageService(repository, properties, localMessageExecutor, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalMessageAspect localMessageAspect(LocalMessageService localMessageService,
                                                 LocalMessageProperties properties,
                                                 ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new LocalMessageAspect(localMessageService, properties, objectMapper);
    }
}
