package com.example.Job;

import com.example.Job.entity.Job;
import com.example.Job.entity.JobStatus;
import com.example.Job.config.SecurityConfig;
import com.example.Job.repo.JobRepository;
import com.example.Job.service.JobPublisher;
import com.example.Job.service.JobService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jobaccess;MODE=MySQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "app.auth.admin-username=test-admin",
        "app.auth.admin-password=local-test-admin-password",
        "app.auth.jwt-secret=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="
})
@AutoConfigureMockMvc
@MockBean(JobPublisher.class)
class JobAccessTest {

    private static final String USER_PASSWORD = "local-test-user-password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JobRepository jobs;

    @Autowired
    private JobService jobService;

    @Autowired
    private JwtEncoder tokens;

    @Test
    void ownerCanReadAndDeleteWhileAnotherUserCannotListReadOrDeleteTheJob() throws Exception {
        long aliceId = register("alice");
        register("bob");
        String alice = login("alice", USER_PASSWORD);
        String bob = login("bob", USER_PASSWORD);

        JsonNode created = json.readTree(mvc.perform(post("/jobs").header(AUTHORIZATION, alice)
                        .contentType(APPLICATION_JSON).content("{\"type\":\"report\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(aliceId))
                .andReturn().getResponse().getContentAsString());
        long id = created.required("id").asLong();

        mvc.perform(post("/jobs").header(AUTHORIZATION, bob).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("type", "report", "userId", aliceId))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/jobs").header(AUTHORIZATION, bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/jobs/{id}", id).header(AUTHORIZATION, bob)).andExpect(status().isNotFound());
        mvc.perform(delete("/jobs/{id}", id).header(AUTHORIZATION, bob)).andExpect(status().isNotFound());

        mvc.perform(get("/jobs/{id}", id).header(AUTHORIZATION, alice)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(delete("/jobs/{id}", id).header(AUTHORIZATION, alice)).andExpect(status().isNoContent());
        mvc.perform(get("/jobs/{id}", id).header(AUTHORIZATION, alice)).andExpect(status().isNotFound());
    }

    @Test
    void legacyRowsStayPrivateEvenWhenTheirOriginalUserIdMatchesANewAccount() throws Exception {
        long accountId = register("legacy-reader");
        String user = login("legacy-reader", USER_PASSWORD);
        String admin = login("test-admin", "local-test-admin-password");
        Job legacy = jobs.saveAndFlush(Job.builder().userId(accountId).type("report")
                .status(JobStatus.FAILED).createdAt(LocalDateTime.now()).build());

        mvc.perform(get("/jobs/failed").header(AUTHORIZATION, user))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/jobs/{id}", legacy.getId()).header(AUTHORIZATION, user)).andExpect(status().isNotFound());
        mvc.perform(delete("/jobs/{id}", legacy.getId()).header(AUTHORIZATION, user)).andExpect(status().isNotFound());
        mvc.perform(post("/jobs/retry-dlq/{id}", legacy.getId()).header(AUTHORIZATION, user))
                .andExpect(status().isForbidden());
        mvc.perform(put("/jobs/{id}/status", legacy.getId()).header(AUTHORIZATION, user)
                        .contentType(APPLICATION_JSON).content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isForbidden());

        Job report = jobService.createJob("report", accountId);
        jobService.completeJob(jobService.claimJob(report.getId()));
        mvc.perform(get("/jobs/{id}", report.getId()).header(AUTHORIZATION, user)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Completed jobs: 0, Failed jobs: 0"));

        mvc.perform(get("/jobs/{id}", legacy.getId()).header(AUTHORIZATION, admin)).andExpect(status().isOk());
        mvc.perform(post("/jobs/retry-dlq/{id}", legacy.getId()).header(AUTHORIZATION, admin))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(put("/jobs/{id}/status", legacy.getId()).header(AUTHORIZATION, admin)
                        .contentType(APPLICATION_JSON).content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void registrationCannotGrantAdminAccessOrReplaceTheAdminAccount() throws Exception {
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("username", "self-declared-admin",
                                "password", USER_PASSWORD, "role", "ADMIN"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("USER"));
        String user = login("self-declared-admin", USER_PASSWORD);
        mvc.perform(post("/jobs/retry-dlq/1").header(AUTHORIZATION, user)).andExpect(status().isForbidden());
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content(credentials("TEST-ADMIN", USER_PASSWORD)))
                .andExpect(status().isConflict());
        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON).content(credentials("test-admin", USER_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRejectMissingAndInvalidBearerCredentials() throws Exception {
        mvc.perform(get("/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/jobs").header(AUTHORIZATION, "Bearer invalid")).andExpect(status().isUnauthorized());
        String issued = login("test-admin", "local-test-admin-password");
        String modifiedSignature = issued.substring(0, issued.lastIndexOf('.') + 1) + "A".repeat(43);
        mvc.perform(get("/jobs").header(AUTHORIZATION, modifiedSignature)).andExpect(status().isUnauthorized());
    }

    @Test
    void signedTokensRequireAnAccountIdAndAValidExpiration() throws Exception {
        Instant now = Instant.now();
        String issuer = SecurityConfig.JWT_ISSUER;
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(issuer)
                .subject("1").issuedAt(now.minusSeconds(1200)).claim("roles", List.of("USER"));
        assertTokenRejected(claims.build());
        assertTokenRejected(claims.expiresAt(now.minusSeconds(600)).build());
        assertTokenRejected(claims.subject("invalid-account-id").expiresAt(now.plusSeconds(120)).build());
    }

    private void assertTokenRejected(JwtClaimsSet claims) throws Exception {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = tokens.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        mvc.perform(get("/jobs").header(AUTHORIZATION, "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    private long register(String username) throws Exception {
        String response = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content(credentials(username, USER_PASSWORD)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).required("id").asLong();
    }

    private String login(String username, String password) throws Exception {
        String response = mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content(credentials(username, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(response).required("accessToken").asText();
    }

    private byte[] credentials(String username, String password) throws Exception {
        return json.writeValueAsBytes(Map.of("username", username, "password", password));
    }
}
