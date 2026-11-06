package io.github.mirco104.budget.bankaccount;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Decision 39: version 7 gives every user the default account "Girokonto" and books all existing transactions and
 * series on it. Runs the migrations on its own in-memory database.
 */
class BankAccountMigrationTest {

    // H2 binds check constraints to the session that created them, so Flyway and the test share one connection
    private final SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
            "jdbc:h2:mem:bank-account-migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
            "sa", "", true);

    @AfterEach
    void closeDatabase() {
        dataSource.destroy();
    }

    @Test
    void booksExistingEntriesOnTheDefaultAccountOfTheirOwner() {
        Flyway.configure().dataSource(dataSource).target("6").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                insert into users (email, password_hash, display_name, role, created_at) values
                    ('person-a@example.test', 'x', 'Person A', 'USER', current_timestamp),
                    ('person-b@example.test', 'x', 'Person B', 'USER', current_timestamp)""");
        jdbc.update("""
                insert into category (name, sort_order, owner_id, icon)
                select 'Wohnen', 10, u.id, 'home' from users u""");
        jdbc.update("""
                insert into transactions (amount, description, booking_date, category_id, owner_id)
                select -10.00, 'Bäckerei', date '2026-10-03', c.id, c.owner_id from category c""");
        jdbc.update("""
                insert into recurring_transactions
                    (series_id, amount, description, category_id, owner_id, recurrence_interval, start_date)
                select random_uuid(), -900.00, 'Miete', c.id, c.owner_id, 'MONTHLY', date '2026-01-01'
                from category c""");

        Flyway.configure().dataSource(dataSource).load().migrate();

        List<String> accounts = jdbc.queryForList("""
                select u.display_name || ':' || b.name || ':' || b.default_account from bank_accounts b
                join users u on u.id = b.owner_id order by u.display_name""", String.class);
        assertThat(accounts).containsExactly("Person A:Girokonto:TRUE", "Person B:Girokonto:TRUE");
        Integer wrongTransactions = jdbc.queryForObject("""
                select count(*) from transactions t join bank_accounts b on b.id = t.account_id
                where b.owner_id <> t.owner_id""", Integer.class);
        Integer wrongSeries = jdbc.queryForObject("""
                select count(*) from recurring_transactions r join bank_accounts b on b.id = r.account_id
                where b.owner_id <> r.owner_id""", Integer.class);
        Integer booked = jdbc.queryForObject("select count(*) from transactions where account_id is not null",
                Integer.class);
        assertThat(wrongTransactions).isZero();
        assertThat(wrongSeries).isZero();
        assertThat(booked).isEqualTo(2);
    }
}
