package com.xx.minilocalmessage.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xx.minilocalmessage.config.LocalMessageProperties;
import com.xx.minilocalmessage.domain.LocalMessageInvocation;
import com.xx.minilocalmessage.domain.LocalMessageRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 本地消息表的 JDBC 访问层。
 *
 * <p>这里故意只依赖 JdbcTemplate，不依赖 MyBatis 或 MyBatis-Plus。
 * 这样 starter 可以放到更多 Spring Boot 项目中使用；业务项目只要有 DataSource 即可。</p>
 */
public class LocalMessageRepository {

    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("[A-Za-z0-9_]+");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String tableName;

    public LocalMessageRepository(JdbcTemplate jdbcTemplate,
                                  ObjectMapper objectMapper,
                                  LocalMessageProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.tableName = requireSafeTableName(properties.getTableName());
    }

    public LocalMessageRecord save(LocalMessageRecord record) {
        String sql = "INSERT INTO " + tableName
                + " (invoke_json, status, next_retry_time, retry_times, max_retry_times, fail_reason, create_time, update_time)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, writeInvocation(record.getInvocation()));
            ps.setInt(2, record.getStatus());
            ps.setTimestamp(3, toTimestamp(record.getNextRetryTime()));
            ps.setInt(4, record.getRetryTimes());
            ps.setInt(5, record.getMaxRetryTimes());
            ps.setString(6, record.getFailReason());
            ps.setTimestamp(7, toTimestamp(record.getCreateTime()));
            ps.setTimestamp(8, toTimestamp(record.getUpdateTime()));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            record.setId(key.longValue());
        }
        return record;
    }

    public List<LocalMessageRecord> findReadyToRetry(Date now, int limit) {
        String sql = "SELECT id, invoke_json, status, next_retry_time, retry_times, max_retry_times,"
                + " fail_reason, create_time, update_time FROM " + tableName
                + " WHERE status = ? AND next_retry_time <= ?"
                + " ORDER BY next_retry_time ASC LIMIT ?";
        return jdbcTemplate.query(sql, rowMapper(), LocalMessageRecord.STATUS_WAIT, toTimestamp(now), limit);
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM " + tableName + " WHERE id = ?", id);
    }

    public void markWaitRetry(Long id, int retryTimes, Date nextRetryTime, String failReason, Date updateTime) {
        String sql = "UPDATE " + tableName
                + " SET status = ?, retry_times = ?, next_retry_time = ?, fail_reason = ?, update_time = ?"
                + " WHERE id = ?";
        jdbcTemplate.update(sql, LocalMessageRecord.STATUS_WAIT, retryTimes, toTimestamp(nextRetryTime),
                failReason, toTimestamp(updateTime), id);
    }

    public void markFail(Long id, int retryTimes, String failReason, Date updateTime) {
        String sql = "UPDATE " + tableName
                + " SET status = ?, retry_times = ?, fail_reason = ?, update_time = ?"
                + " WHERE id = ?";
        jdbcTemplate.update(sql, LocalMessageRecord.STATUS_FAIL, retryTimes, failReason, toTimestamp(updateTime), id);
    }

    private RowMapper<LocalMessageRecord> rowMapper() {
        return (rs, rowNum) -> {
            LocalMessageRecord record = new LocalMessageRecord();
            record.setId(rs.getLong("id"));
            record.setInvocation(readInvocation(rs.getString("invoke_json")));
            record.setStatus(rs.getInt("status"));
            record.setNextRetryTime(rs.getTimestamp("next_retry_time"));
            record.setRetryTimes(rs.getInt("retry_times"));
            record.setMaxRetryTimes(rs.getInt("max_retry_times"));
            record.setFailReason(rs.getString("fail_reason"));
            record.setCreateTime(rs.getTimestamp("create_time"));
            record.setUpdateTime(rs.getTimestamp("update_time"));
            return record;
        };
    }

    private String writeInvocation(LocalMessageInvocation invocation) {
        try {
            return objectMapper.writeValueAsString(invocation);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Serialize local message invocation failed", e);
        }
    }

    private LocalMessageInvocation readInvocation(String json) {
        try {
            return objectMapper.readValue(json, LocalMessageInvocation.class);
        } catch (Exception e) {
            throw new IllegalStateException("Deserialize local message invocation failed", e);
        }
    }

    private Timestamp toTimestamp(Date date) {
        return new Timestamp(date.getTime());
    }

    private String requireSafeTableName(String configuredTableName) {
        if (configuredTableName == null || !SAFE_TABLE_NAME.matcher(configuredTableName).matches()) {
            throw new IllegalArgumentException("mini-local-message.table-name only supports letters, numbers and underscore");
        }
        return configuredTableName;
    }
}
