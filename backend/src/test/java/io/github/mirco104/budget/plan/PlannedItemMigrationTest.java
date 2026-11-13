package io.github.mirco104.budget.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Section 19.3: V13 to V15 turn series into planned items, past occurrences into bookings marked "auto" and
 * exceptions into terms, then drop the old tables. Runs the migrations on its own in-memory database.
 */
class PlannedItemMigrationTest {

    // H2 binds check constraints to the session that created them, so Flyway and the test share one connection
    private final SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
            "jdbc:h2:mem:planned-item-migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
            "sa", "", true);

    @AfterEach
    void closeDatabase() {
        dataSource.destroy();
    }

    @Test
    void convertsSeriesExceptionsAndPastOccurrences() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("6").load().migrate();
        jdbc.update("""
                insert into users (email, password_hash, display_name, role, created_at) values
                    ('person-a@example.test', 'x', 'Person A', 'USER', current_timestamp)""");
        jdbc.update("""
                insert into category (name, sort_order, owner_id, icon)
                select 'Wohnen', 10, u.id, 'home' from users u""");
        // V7 gives the user the default account
        Flyway.configure().dataSource(dataSource).target("12").load().migrate();
        jdbc.update("""
                insert into category_target (category_id, valid_from, target_amount)
                select c.id, date '2025-01-01', 900.00 from category c""");
        // Rent from January to June 2025: March suspended, April replaced by a booking
        jdbc.update("""
                insert into recurring_transactions (series_id, amount, description, category_id, owner_id, account_id,
                    recurrence_interval, start_date, end_date)
                select '00000000-0000-0000-0000-000000000001', -900.00, 'Miete', c.id, c.owner_id, b.id, 'MONTHLY',
                    date '2025-01-01', date '2025-06-30'
                from category c join bank_accounts b on b.owner_id = c.owner_id""");
        jdbc.update("""
                insert into transactions (amount, description, booking_date, category_id, owner_id, account_id)
                select -905.00, 'Miete April', date '2025-04-02', c.id, c.owner_id, b.id
                from category c join bank_accounts b on b.owner_id = c.owner_id""");
        jdbc.update("""
                insert into recurring_exceptions (recurring_id, occurrence_date, transaction_id)
                select r.id, date '2025-03-01', null from recurring_transactions r""");
        jdbc.update("""
                insert into recurring_exceptions (recurring_id, occurrence_date, transaction_id)
                select r.id, date '2025-04-01', t.id from recurring_transactions r, transactions t""");
        // A series split before its second occurrence ("Dieser und alle folgenden"), and a yearly one in the future
        jdbc.update("""
                insert into recurring_transactions (series_id, amount, description, category_id, owner_id, account_id,
                    recurrence_interval, start_date, end_date)
                select '00000000-0000-0000-0000-000000000002', -50.00, 'Strom', c.id, c.owner_id, b.id, 'MONTHLY',
                    date '2025-01-15', date '2025-02-14'
                from category c join bank_accounts b on b.owner_id = c.owner_id""");
        jdbc.update("""
                insert into recurring_transactions (series_id, amount, description, category_id, owner_id, account_id,
                    recurrence_interval, start_date, end_date)
                select '00000000-0000-0000-0000-000000000002', -60.00, 'Strom', c.id, c.owner_id, b.id, 'QUARTERLY',
                    date '2025-02-15', date '2025-08-31'
                from category c join bank_accounts b on b.owner_id = c.owner_id""");
        jdbc.update("""
                insert into recurring_transactions (series_id, amount, description, category_id, owner_id, account_id,
                    recurrence_interval, start_date, end_date)
                select '00000000-0000-0000-0000-000000000003', -240.00, 'Haftpflicht', c.id, c.owner_id, b.id,
                    'YEARLY', date '2099-03-15', null
                from category c join bank_accounts b on b.owner_id = c.owner_id""");

        Flyway.configure().dataSource(dataSource).load().migrate();

        List<Map<String, Object>> items = jdbc.queryForList("""
                select name, amount, rhythm_unit, rhythm_count, anchor_date, entry_mode, valid_from, valid_to
                from planned_items order by name, valid_from""");
        assertThat(items).extracting(row -> row.get("name") + " " + row.get("rhythm_count") + " "
                        + row.get("valid_from") + " " + row.get("valid_to") + " " + row.get("entry_mode"))
                .containsExactly(
                        "Haftpflicht 12 2099-03-01 null AUTO",
                        "Miete 1 2025-01-01 2025-06-30 AUTO",
                        "Strom 1 2025-01-01 2025-01-31 AUTO",
                        "Strom 3 2025-02-01 2025-08-31 AUTO");
        assertThat(jdbc.queryForObject("select count(distinct item_key) from planned_items", Integer.class))
                .isEqualTo(3);

        // Miete: January, February, May, June become automatic bookings; Strom: 15 January, 15 February, 15 May,
        // 15 August
        List<String> auto = jdbc.queryForList("""
                select description || ' ' || booking_date from transactions where auto order by description, booking_date""",
                String.class);
        assertThat(auto).containsExactly(
                "Miete 2025-01-01", "Miete 2025-02-01", "Miete 2025-05-01", "Miete 2025-06-01",
                "Strom 2025-01-15", "Strom 2025-02-15", "Strom 2025-05-15", "Strom 2025-08-15");
        List<String> terms = jdbc.queryForList("""
                select t.term_date || ' ' || (t.transaction_id is not null) || ' ' || t.skipped from item_terms t
                where t.item_key = '00000000-0000-0000-0000-000000000001' order by t.term_date""", String.class);
        assertThat(terms).containsExactly(
                "2025-01-01 TRUE FALSE", "2025-02-01 TRUE FALSE", "2025-03-01 FALSE TRUE", "2025-04-01 TRUE FALSE",
                "2025-05-01 TRUE FALSE", "2025-06-01 TRUE FALSE");
        assertThat(jdbc.queryForObject("""
                select count(*) from item_terms t join transactions x on x.id = t.transaction_id
                where x.description = 'Miete April' and t.term_date = date '2025-04-01'""", Integer.class)).isEqualTo(1);

        // The old tables are gone, category targets are not taken over (decision 56)
        for (String table : List.of("recurring_transactions", "recurring_exceptions", "category_target")) {
            assertThatThrownBy(() -> jdbc.queryForObject("select count(*) from " + table, Integer.class))
                    .as(table).isInstanceOf(Exception.class);
        }
    }
}
