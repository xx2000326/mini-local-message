package com.xx.minilocalmessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xx.minilocalmessage.config.LocalMessageProperties;
import com.xx.minilocalmessage.domain.LocalMessageInvocation;
import com.xx.minilocalmessage.domain.LocalMessageRecord;
import com.xx.minilocalmessage.repository.LocalMessageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalMessageRepositoryTest {

    private EmbeddedDatabase database;

    @AfterEach
    void shutdownDatabase() {
        if (database != null) {
            database.shutdown();
        }
    }

    @Test
    void shouldSaveAndFindReadyRetryRecord() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("schema.sql")
                .build();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        LocalMessageRepository repository = new LocalMessageRepository(jdbcTemplate, new ObjectMapper(), new LocalMessageProperties());
        LocalMessageRecord record = new LocalMessageRecord();
        record.setInvocation(invocation());
        record.setNextRetryTime(new Date(System.currentTimeMillis() - 1000L));

        repository.save(record);

        List<LocalMessageRecord> records = repository.findReadyToRetry(new Date(), 10);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getInvocation().getMethodName()).isEqualTo("sendOrderPaidMessage");
    }

    private LocalMessageInvocation invocation() {
        LocalMessageInvocation invocation = new LocalMessageInvocation();
        invocation.setBeanTypeName("com.example.OrderNotifier");
        invocation.setMethodClassName("com.example.OrderNotifier");
        invocation.setMethodName("sendOrderPaidMessage");
        invocation.setParameterTypeNames(Arrays.asList("java.lang.Long"));
        invocation.setArgumentJson("[1001]");
        return invocation;
    }
}
