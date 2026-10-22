/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory.internal.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class GeneratedInventoryRepository {
    private final JdbcClient jdbcClient;

    public GeneratedInventoryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Inserts a generation key unless it already exists, participating in the caller's transaction.
     * A concurrent claim waits for the competing transaction to commit or roll back.
     *
     * @return true if this call inserted the key; false if the key already exists
     * @throws org.springframework.dao.DataAccessException if the insert fails
     */
    public boolean claim(String idempotencyKey) {
        // Shares the inventory transaction: the key becomes durable only if stock updates commit.
        // Concurrent inserts wait for commit/rollback before deciding whether the key is a
        // duplicate.
        return jdbcClient
                        .sql(
                                """
                INSERT INTO inventory.generated_inventory_keys (idempotency_key)
                VALUES (:key) ON CONFLICT (idempotency_key) DO NOTHING
                """)
                        .param("key", idempotencyKey)
                        .update()
                == 1;
    }
}
