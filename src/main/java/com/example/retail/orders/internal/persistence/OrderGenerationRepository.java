/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders.internal.persistence;

import com.example.retail.orders.internal.domain.OrderGeneration;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OrderGenerationRepository {

    private final JdbcClient jdbcClient;

    public OrderGenerationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<OrderGeneration> findByIdempotencyKey(String idempotencyKey) {
        return jdbcClient
                .sql(
                        """
                        SELECT idempotency_key, batch_size, total_batches, completed_batches,
                               status, error_message, created_date, last_modified_date
                        FROM orders.order_generation
                        WHERE idempotency_key = :key
                        """)
                .param("key", idempotencyKey)
                .query(
                        (rs, rowNum) ->
                                new OrderGeneration(
                                        rs.getString("idempotency_key"),
                                        rs.getInt("batch_size"),
                                        rs.getInt("total_batches"),
                                        rs.getInt("completed_batches"),
                                        rs.getString("status"),
                                        rs.getString("error_message"),
                                        rs.getTimestamp("created_date") != null
                                                ? rs.getTimestamp("created_date").toLocalDateTime()
                                                : null,
                                        rs.getTimestamp("last_modified_date") != null
                                                ? rs.getTimestamp("last_modified_date")
                                                        .toLocalDateTime()
                                                : null))
                .optional();
    }

    public boolean createInitial(String idempotencyKey, int batchSize, int totalBatches) {
        return jdbcClient
                        .sql(
                                """
                                INSERT INTO orders.order_generation (
                                    idempotency_key, batch_size, total_batches, completed_batches,
                                    status, created_date, last_modified_date
                                ) VALUES (
                                    :key, :batchSize, :totalBatches, 0,
                                    :status, NOW(), NOW()
                                ) ON CONFLICT (idempotency_key) DO NOTHING
                                """)
                        .param("key", idempotencyKey)
                        .param("batchSize", batchSize)
                        .param("totalBatches", totalBatches)
                        .param("status", OrderGeneration.STATUS_IN_PROGRESS)
                        .update()
                == 1;
    }

    public void updateProgress(String idempotencyKey, int completedBatches) {
        jdbcClient
                .sql(
                        """
                        UPDATE orders.order_generation
                        SET completed_batches = :completedBatches,
                            last_modified_date = NOW()
                        WHERE idempotency_key = :key
                        """)
                .param("completedBatches", completedBatches)
                .param("key", idempotencyKey)
                .update();
    }

    public void markCompleted(String idempotencyKey, int completedBatches) {
        jdbcClient
                .sql(
                        """
                        UPDATE orders.order_generation
                        SET completed_batches = :completedBatches,
                            status = :status,
                            last_modified_date = NOW()
                        WHERE idempotency_key = :key
                        """)
                .param("completedBatches", completedBatches)
                .param("status", OrderGeneration.STATUS_COMPLETED)
                .param("key", idempotencyKey)
                .update();
    }

    public void markFailed(String idempotencyKey, String errorMessage) {
        String truncatedError =
                errorMessage != null
                        ? errorMessage.substring(0, Math.min(errorMessage.length(), 1000))
                        : null;
        jdbcClient
                .sql(
                        """
                        UPDATE orders.order_generation
                        SET status = :status,
                            error_message = :errorMessage,
                            last_modified_date = NOW()
                        WHERE idempotency_key = :key
                        """)
                .param("status", OrderGeneration.STATUS_FAILED)
                .param("errorMessage", truncatedError)
                .param("key", idempotencyKey)
                .update();
    }

    public void markResumed(String idempotencyKey) {
        jdbcClient
                .sql(
                        """
                        UPDATE orders.order_generation
                        SET status = :status,
                            error_message = NULL,
                            last_modified_date = NOW()
                        WHERE idempotency_key = :key
                        """)
                .param("status", OrderGeneration.STATUS_IN_PROGRESS)
                .param("key", idempotencyKey)
                .update();
    }
}
