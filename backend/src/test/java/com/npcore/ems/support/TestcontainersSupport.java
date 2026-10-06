package com.npcore.ems.support;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL 16 trong container cho build profile full (Flyway tự migrate khi app khởi động).
 * Build offline loại file này khỏi test-compile; {@link AbstractIntegrationTest} nạp nó bằng reflection.
 */
public final class TestcontainersSupport {

    private static PostgreSQLContainer<?> container;

    private TestcontainersSupport() {}

    /** Khởi động (một lần cho cả JVM) và trả về {jdbcUrl, username, password}. */
    public static synchronized String[] start() {
        if (container == null) {
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("ems_test")
                    .withUsername("ems")
                    .withPassword("ems");
            container.start();
            Runtime.getRuntime().addShutdownHook(new Thread(container::stop));
        }
        return new String[] {container.getJdbcUrl(), container.getUsername(), container.getPassword()};
    }
}
