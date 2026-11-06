package io.github.mirco104.budget.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Decision 36: version 6 drops the percent of a target. An entry that had only a percent stays with an empty amount
 * (no target from its month), euro targets keep their amount. Runs the migrations on its own in-memory database.
 */
class TargetPercentMigrationTest {

    // H2 binds check constraints to the session that created them, so Flyway and the test share one connection
    private final SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
            "jdbc:h2:mem:target-percent-migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
            "sa", "", true);

    @AfterEach
    void closeDatabase() {
        dataSource.destroy();
    }

    @Test
    void dropsThePercentAndKeepsTheEntries() {
        Flyway.configure().dataSource(dataSource).target("5").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                insert into users (email, password_hash, display_name, role, created_at)
                values ('person-a@example.test', 'x', 'Person A', 'USER', current_timestamp)""");
        jdbc.update("""
                insert into category (name, sort_order, owner_id, icon)
                select n.name, n.sort_order, u.id, 'tag' from users u,
                    (values ('Wohnen', 10), ('Freizeit', 20), ('Mobilität', 30)) as n(name, sort_order)""");
        jdbc.update("""
                insert into category_target (category_id, valid_from, target_amount, target_percent)
                select c.id, date '2026-10-01', t.amount, t.percent from category c join
                    (values ('Wohnen', 900.00, null), ('Freizeit', null, 0.0500), ('Mobilität', 60.00, 0.5000))
                    as t(name, amount, percent) on c.name = t.name""");

        Flyway.configure().dataSource(dataSource).load().migrate();

        Map<String, Object> amounts = new LinkedHashMap<>();
        jdbc.query("""
                select c.name, t.target_amount from category_target t join category c on c.id = t.category_id
                order by c.sort_order""",
                row -> {
                    amounts.put(row.getString("name"), row.getBigDecimal("target_amount"));
                });
        assertThat(amounts).containsOnlyKeys("Wohnen", "Freizeit", "Mobilität");
        assertThat(amounts.get("Wohnen")).isEqualTo(new BigDecimal("900.00"));
        assertThat(amounts.get("Freizeit")).isNull();
        assertThat(amounts.get("Mobilität")).isEqualTo(new BigDecimal("60.00"));
        Integer percentColumns = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'category_target' and column_name = 'target_percent'""", Integer.class);
        assertThat(percentColumns).isZero();
    }
}
