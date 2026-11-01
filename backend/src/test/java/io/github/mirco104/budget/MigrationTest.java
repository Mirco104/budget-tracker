package io.github.mirco104.budget;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/** The schema comes only from Flyway; Hibernate validates it against the entities (US11). */
@SpringBootTest
class MigrationTest {

    @Autowired
    private Flyway flyway;

    @Value("${spring.jpa.hibernate.ddl-auto}")
    private String ddlAuto;

    @Test
    void appliesAllMigrations() {
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied()).extracting(MigrationInfo::getScript)
                .contains("V1__initial_schema.sql", "V2__users_and_owner.sql");
    }

    @Test
    void hibernateOnlyValidatesTheSchema() {
        assertThat(ddlAuto).isEqualTo("validate");
    }
}
