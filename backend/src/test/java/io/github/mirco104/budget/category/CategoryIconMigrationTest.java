package io.github.mirco104.budget.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * US28: categories that exist before the icons get the icon of their default name, all others "tag". Runs the
 * migrations on its own in-memory database, so data can be inserted between version 4 and version 5.
 */
class CategoryIconMigrationTest {

    // H2 binds check constraints to the session that created them, so Flyway and the test share one connection
    private final SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
            "jdbc:h2:mem:icon-migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
            "sa", "", true);

    @AfterEach
    void closeDatabase() {
        dataSource.destroy();
    }

    @Test
    void setsIconsOfExistingCategories() {
        Flyway.configure().dataSource(dataSource).target("4").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                insert into users (email, password_hash, display_name, role, created_at)
                values ('person-a@example.test', 'x', 'Person A', 'USER', current_timestamp)""");
        jdbc.update("""
                insert into category (name, sort_order, owner_id)
                select n.name, n.sort_order, u.id from users u,
                    (values ('Wohnen', 10), ('Gehalt/Lohn (netto)', 20), ('Eigene', 30)) as n(name, sort_order)""");

        Flyway.configure().dataSource(dataSource).load().migrate();

        Map<String, String> icons = new LinkedHashMap<>();
        jdbc.query("select name, icon from category order by sort_order",
                row -> {
                    icons.put(row.getString("name"), row.getString("icon"));
                });
        assertThat(icons).containsExactly(
                Map.entry("Wohnen", "home"), Map.entry("Gehalt/Lohn (netto)", "wallet"), Map.entry("Eigene", "tag"));
    }
}
