package io.github.mirco104.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * Ticket #147: with H2 2.4.240 every connection opened after the migrations, while the database stays open (the
 * connection pool of the local profiles), failed on tables with a check constraint ("Check constraint invalid").
 */
class H2MigrationTest {

    @Test
    void newConnectionCanWriteTablesWithCheckConstraintsRightAfterTheMigrations() throws SQLException {
        String url = "jdbc:h2:mem:migration-" + System.nanoTime()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        // Keeps the database open like the pool does
        try (Connection pool = DriverManager.getConnection(url, "sa", "")) {
            Flyway.configure().dataSource(url, "sa", "").load().migrate();

            try (Connection fresh = DriverManager.getConnection(url, "sa", "");
                    Statement statement = fresh.createStatement()) {
                assertThatCode(() -> statement.executeUpdate("insert into users "
                        + "(email, display_name, password_hash, role, created_at) values "
                        + "('person-a@example.test', 'Person A', 'x', 'USER', current_timestamp)"))
                        .doesNotThrowAnyException();
                assertThat(statement.executeUpdate("update users set last_login_at = current_timestamp, role = 'ADMIN'"))
                        .isEqualTo(1);
            }
        }
    }
}
