package io.github.mirco104.budget.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsUpWithoutAModeOutsideTheLocalProfiles() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.mode").doesNotExist());
    }

    @Test
    void theModeIsTheActiveLocalProfile() {
        MockEnvironment sample = new MockEnvironment();
        sample.setActiveProfiles("sample");
        assertThat(HealthController.mode(sample)).isEqualTo("sample");
    }

    @Test
    void withoutAnActiveProfileTheDefaultDevIsTheMode() {
        MockEnvironment none = new MockEnvironment();
        none.setDefaultProfiles("dev");
        assertThat(HealthController.mode(none)).isEqualTo("dev");
    }

    @Test
    void productionAndTestsHaveNoMode() {
        MockEnvironment postgres = new MockEnvironment();
        postgres.setActiveProfiles("postgres");
        MockEnvironment test = new MockEnvironment();
        test.setActiveProfiles("test");
        assertThat(HealthController.mode(postgres)).isNull();
        assertThat(HealthController.mode(test)).isNull();
    }
}
