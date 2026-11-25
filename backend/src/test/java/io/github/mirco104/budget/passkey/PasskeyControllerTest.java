package io.github.mirco104.budget.passkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.auth.LoginAttempts;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserRepository;
import io.github.mirco104.budget.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** US44: add, rename and delete passkeys, sign in with one; R15 for the passkeys of another user. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasskeyControllerTest {

    private static final String EMAIL = "person-b@example.test";
    private static final String OTHER = "person-c@example.test";
    private static final String IP = "127.0.0.1";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasskeyRepository passkeyRepository;

    @Autowired
    private LoginAttempts loginAttempts;

    private User user;

    @BeforeEach
    void createUsers() {
        user = TestUsers.create(userService, EMAIL);
        TestUsers.create(userService, OTHER);
        loginAttempts.resetIp(IP);
    }

    // The IP lock lives in memory and outlasts this class
    @AfterEach
    void releaseTheLock() {
        loginAttempts.resetIp(IP);
    }

    @Test
    void addsAPasskeyAndSignsInWithIt() throws Exception {
        MockHttpSession session = signedIn(EMAIL);
        SoftwarePasskey passkey = new SoftwarePasskey();

        add(session, passkey, "Laptop").andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Laptop"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.lastUsedAt").isEmpty());

        MockHttpSession fresh = new MockHttpSession();
        String before = fresh.getId();
        signIn(fresh, passkey.signIn(loginChallenge(fresh))).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
        assertThat(fresh.getId()).isNotEqualTo(before);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getLastLoginAt()).isNotNull();
        mockMvc.perform(get("/api/account/passkeys").session(fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].label").value("Laptop"))
                .andExpect(jsonPath("$[0].lastUsedAt").isNotEmpty());
    }

    // The handle is random and no personal data; the second passkey keeps it
    @Test
    void keepsOneRandomUserHandleForSeveralPasskeys() throws Exception {
        MockHttpSession session = signedIn(EMAIL);
        String first = userHandle(registrationOptions(session));
        add(session, new SoftwarePasskey(), null, first).andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Passkey"));

        assertThat(userHandle(registrationOptions(session))).isEqualTo(first);
        assertThat(new String(java.util.Base64.getUrlDecoder().decode(first), java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("person-b");
    }

    @Test
    void addingAPasskeyNeedsThePassword() throws Exception {
        MockHttpSession session = signedIn(EMAIL);

        mockMvc.perform(post("/api/account/passkeys/options").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"wrong-password-123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].message").value("Das Passwort ist falsch."));
    }

    @Test
    void rejectsAPasskeyForAnotherAddress() throws Exception {
        MockHttpSession session = signedIn(EMAIL);
        JsonNode options = registrationOptions(session);

        mockMvc.perform(post("/api/account/passkeys").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new SoftwarePasskey().register(challenge(options), userHandle(options), "Laptop",
                                "https://budget.example.test")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Der Passkey ließ sich nicht speichern. Bitte versuche es noch einmal."));
        assertThat(passkeyRepository.findByOwnerOrderByCreatedAtAscIdAsc(user)).isEmpty();
    }

    // The challenge is usable once: a second answer to the same options fails
    @Test
    void rejectsASecondRegistrationWithTheSameOptions() throws Exception {
        MockHttpSession session = signedIn(EMAIL);
        JsonNode options = registrationOptions(session);
        String body = new SoftwarePasskey().register(challenge(options), userHandle(options), "A", SoftwarePasskey.ORIGIN);
        send(session, "/api/account/passkeys", body).andExpect(status().isCreated());

        send(session, "/api/account/passkeys",
                new SoftwarePasskey().register(challenge(options), userHandle(options), "B", SoftwarePasskey.ORIGIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Das Hinzufügen ist abgelaufen. Bitte starte es noch einmal."));
    }

    @Test
    void rejectsAWrongSignatureAndCountsItForTheIp() throws Exception {
        MockHttpSession session = signedIn(EMAIL);
        SoftwarePasskey passkey = new SoftwarePasskey();
        add(session, passkey, "Laptop").andExpect(status().isCreated());

        MockHttpSession fresh = new MockHttpSession();
        String challenge = loginChallenge(fresh);
        signIn(fresh, passkey.signIn(challenge, SoftwarePasskey.ORIGIN, SoftwarePasskey.otherKey()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(PasskeyService.NOT_RECOGNISED));
        mockMvc.perform(get("/api/auth/me").session(fresh)).andExpect(status().isUnauthorized());

        // 19 more failures lock the IP, then even the right passkey gets 429
        for (int i = 0; i < 19; i++) {
            signIn(new MockHttpSession(), "{}").andExpect(status().isUnauthorized());
        }
        MockHttpSession locked = new MockHttpSession();
        signIn(locked, passkey.signIn(loginChallenge(locked))).andExpect(status().isTooManyRequests());
    }

    @Test
    void rejectsAnAnswerFromAnotherAddressAndAReplay() throws Exception {
        MockHttpSession session = signedIn(EMAIL);
        SoftwarePasskey passkey = new SoftwarePasskey();
        add(session, passkey, "Laptop").andExpect(status().isCreated());

        MockHttpSession phishing = new MockHttpSession();
        signIn(phishing, passkey.signIn(loginChallenge(phishing), "https://budget.example.test"))
                .andExpect(status().isUnauthorized());

        MockHttpSession fresh = new MockHttpSession();
        String answer = passkey.signIn(loginChallenge(fresh));
        signIn(fresh, answer).andExpect(status().isNoContent());
        MockHttpSession replay = new MockHttpSession();
        loginChallenge(replay);
        signIn(replay, answer).andExpect(status().isUnauthorized());
    }

    @Test
    void signInWithoutOptionsFails() throws Exception {
        signIn(new MockHttpSession(), "{\"id\": \"x\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(PasskeyService.NOT_RECOGNISED));
    }

    // US16: the passkey belongs to the user, not to the e-mail
    @Test
    void aChangedEmailKeepsThePasskey() throws Exception {
        MockHttpSession session = signedIn(EMAIL);
        SoftwarePasskey passkey = new SoftwarePasskey();
        add(session, passkey, "Laptop").andExpect(status().isCreated());
        mockMvc.perform(put("/api/account/email").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"person-b2@example.test\", \"password\": \"%s\"}"
                                .formatted(TestUsers.PASSWORD)))
                .andExpect(status().isOk());

        // A second passkey after the change still uses the same handle
        add(session, new SoftwarePasskey(), "Handy").andExpect(status().isCreated());
        MockHttpSession fresh = new MockHttpSession();
        signIn(fresh, passkey.signIn(loginChallenge(fresh))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").session(fresh))
                .andExpect(jsonPath("$.email").value("person-b2@example.test"));
    }

    @Test
    void renamesAndDeletesAnOwnPasskey() throws Exception {
        MockHttpSession session = signedIn(EMAIL);
        SoftwarePasskey passkey = new SoftwarePasskey();
        long id = id(add(session, passkey, "Laptop").andExpect(status().isCreated()));

        mockMvc.perform(put("/api/account/passkeys/" + id).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\": \"  Arbeitslaptop  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Arbeitslaptop"));
        mockMvc.perform(put("/api/account/passkeys/" + id).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("label"));

        mockMvc.perform(delete("/api/account/passkeys/" + id).session(session).with(csrf()))
                .andExpect(status().isNoContent());
        MockHttpSession fresh = new MockHttpSession();
        signIn(fresh, passkey.signIn(loginChallenge(fresh))).andExpect(status().isUnauthorized());
    }

    // R15
    @Test
    void anotherUsersPasskeyIsNotFound() throws Exception {
        long id = id(add(signedIn(EMAIL), new SoftwarePasskey(), "Laptop").andExpect(status().isCreated()));
        MockHttpSession other = signedIn(OTHER);

        mockMvc.perform(get("/api/account/passkeys").session(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(put("/api/account/passkeys/" + id).session(other).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\": \"Fremd\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/account/passkeys/" + id).session(other).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(passkeyRepository.findById(id)).isPresent();
    }

    @Test
    void managingPasskeysNeedsASessionAndCsrf() throws Exception {
        mockMvc.perform(get("/api/account/passkeys")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/account/passkeys/options").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/passkey/options")).andExpect(status().isForbidden());
    }

    // US16: deleting the account removes its passkeys
    @Test
    void deletingTheAccountRemovesItsPasskeys() throws Exception {
        MockHttpSession session = signedIn(EMAIL);
        add(session, new SoftwarePasskey(), "Laptop").andExpect(status().isCreated());

        mockMvc.perform(delete("/api/account").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"%s\"}".formatted(TestUsers.PASSWORD)))
                .andExpect(status().isNoContent());
        assertThat(passkeyRepository.findAll()).isEmpty();
    }

    private MockHttpSession signedIn(String email) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, TestUsers.PASSWORD)))
                .andExpect(status().isNoContent());
        return session;
    }

    private JsonNode registrationOptions(MockHttpSession session) throws Exception {
        String body = send(session, "/api/account/passkeys/options",
                "{\"password\": \"%s\"}".formatted(TestUsers.PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rp.id").value("localhost"))
                .andExpect(jsonPath("$.authenticatorSelection.userVerification").value("required"))
                .andExpect(jsonPath("$.authenticatorSelection.residentKey").value("required"))
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    private ResultActions add(MockHttpSession session, SoftwarePasskey passkey, String label) throws Exception {
        JsonNode options = registrationOptions(session);
        return send(session, "/api/account/passkeys",
                passkey.register(challenge(options), userHandle(options), label, SoftwarePasskey.ORIGIN));
    }

    private ResultActions add(MockHttpSession session, SoftwarePasskey passkey, String label, String expectedHandle)
            throws Exception {
        JsonNode options = registrationOptions(session);
        assertThat(userHandle(options)).isEqualTo(expectedHandle);
        return send(session, "/api/account/passkeys",
                passkey.register(challenge(options), userHandle(options), label, SoftwarePasskey.ORIGIN));
    }

    private String loginChallenge(MockHttpSession session) throws Exception {
        String body = mockMvc.perform(post("/api/auth/passkey/options").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rpId").value("localhost"))
                .andExpect(jsonPath("$.userVerification").value("required"))
                .andExpect(jsonPath("$.allowCredentials.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        return challenge(JSON.readTree(body));
    }

    private ResultActions signIn(MockHttpSession session, String body) throws Exception {
        return send(session, "/api/auth/passkey", body);
    }

    private ResultActions send(MockHttpSession session, String path, String body) throws Exception {
        return mockMvc.perform(post(path)
                .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String challenge(JsonNode options) {
        return options.get("challenge").asString();
    }

    private static String userHandle(JsonNode options) {
        return options.get("user").get("id").asString();
    }

    private static long id(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}
