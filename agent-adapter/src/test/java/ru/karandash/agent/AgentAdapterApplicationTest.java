package ru.karandash.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import ru.karandash.agent.cli.CliAuthState;
import ru.karandash.agent.cli.CliProcessRunner;
import ru.karandash.agent.testing.FakeCliSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Агент-адаптер целиком: HTTP API, сервисный токен, запуск фейкового {@code claude} и разбор его вывода.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "karandash.agent.service-token=" + AgentAdapterApplicationTest.TOKEN,
        "karandash.agent.cli=claude",
        "karandash.agent.timeout=30s",
        "karandash.agent.health-cache-ttl=0s"
})
class AgentAdapterApplicationTest {

    static final String TOKEN = "test-agent-token";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1, 2, 3, 4};
    private static final Path TEMP = createTemp();
    private static final FakeCliSupport FAKE = createFake();

    @Autowired
    TestRestTemplate rest;

    @Autowired
    CliAuthState authState;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @DynamicPropertySource
    static void fakeCli(DynamicPropertyRegistry registry) {
        List<String> command = FAKE.command();
        for (int index = 0; index < command.size(); index++) {
            int position = index;
            registry.add("karandash.agent.claude.command[" + index + "]", () -> command.get(position));
        }
        registry.add("karandash.agent.work-dir", () -> TEMP.resolve("calls").toString());
    }

    @TestConfiguration
    static class FakeEnvironment {

        /** Родительское окружение без настоящих ключей машины, но с секретами, которые не должны утечь в CLI. */
        @Bean
        @Primary
        CliProcessRunner testCliProcessRunner() {
            return new CliProcessRunner(FakeCliSupport.parentEnvironment(Map.of(
                    "ANTHROPIC_API_KEY", "test-anthropic-key",
                    "AGENT_SERVICE_TOKEN", TOKEN,
                    "POSTGRES_PASSWORD", "must-not-leak")));
        }
    }

    @BeforeEach
    void resetAuthState() throws IOException {
        authState.markAccepted();
        FAKE.mode("claude-ok");
    }

    @AfterAll
    static void cleanUp() {
        FileSystemUtils.deleteRecursively(TEMP.toFile());
    }

    @Test
    void rejectsRequestsWithoutServiceToken() {
        ResponseEntity<String> anonymous = rest.postForEntity("/internal/recognition/text",
                new HttpEntity<>(Map.of("description", "суп"), jsonHeaders(null)), String.class);
        ResponseEntity<String> wrongToken = rest.postForEntity("/internal/recognition/text",
                new HttpEntity<>(Map.of("description", "суп"), jsonHeaders("wrong")), String.class);

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void recognizesTextWithToolsDisabled() throws Exception {
        ResponseEntity<String> response = postText("гречка с курицей, тарелка");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.at("/result/items/0/name").asText()).isEqualTo("Гречка с курицей");
        assertThat(body.at("/result/items/0/kcal_min").asInt()).isEqualTo(380);
        assertThat(body.at("/usage/provider").asText()).isEqualTo("claude-cli");
        assertThat(body.at("/usage/model").asText()).isEqualTo("claude-test-model");
        assertThat(body.at("/usage/inputTokens").asInt()).isEqualTo(115);

        List<String> arguments = FAKE.recordedArguments();
        assertThat(arguments.get(arguments.indexOf("--tools") + 1)).as("инструменты выключены").isEmpty();
        assertThat(arguments).contains("--strict-mcp-config", "--safe-mode", "--restricted", "--disable-slash-commands");
        assertThat(arguments.get(arguments.indexOf("--permission-mode") + 1)).isEqualTo("dontAsk");
        assertThat(arguments).noneMatch(argument -> argument.contains("гречка"));

        Map<String, String> environment = FAKE.recordedEnvironment();
        assertThat(environment).containsEntry("ANTHROPIC_API_KEY", "test-anthropic-key")
                .doesNotContainKeys("AGENT_SERVICE_TOKEN", "POSTGRES_PASSWORD");
        assertThat(FAKE.recordedWorkingDirectoryFiles()).isEmpty();
        assertThat(FAKE.recordedWorkingDirectory()).doesNotExist();
    }

    @Test
    void recognizesPhotoPassedThroughStdin() throws Exception {
        ResponseEntity<String> response = postPhoto(JPEG);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode stdin = objectMapper.readTree(new String(FAKE.recordedStdin(), StandardCharsets.UTF_8));
        assertThat(Base64.getDecoder().decode(stdin.at("/message/content/0/source/data").asText())).isEqualTo(JPEG);
        assertThat(FAKE.recordedCallRootFiles()).noneMatch(name -> name.startsWith("input"));
        assertThat(FAKE.recordedWorkingDirectory()).doesNotExist();
    }

    @Test
    void validatesInput() {
        assertThat(postText(" ").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(postText("щи".repeat(1001)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(postPhoto("#!/bin/sh\nid\n".getBytes(StandardCharsets.UTF_8)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void killsCliThatTriesToUseTools() throws Exception {
        FAKE.mode("claude-tool-use");
        long startedAt = System.nanoTime();

        ResponseEntity<String> response = postText("суп");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(objectMapper.readTree(response.getBody()).path("error").asText()).isEqualTo("unsafe_cli_behavior");
        assertThat(System.nanoTime() - startedAt).isLessThan(20_000_000_000L);

        FAKE.mode("claude-tools-in-init");
        assertThat(postText("суп").getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void rejectsOutputThatBreaksContract() throws Exception {
        FAKE.mode("claude-bad-json");
        assertThat(objectMapper.readTree(postText("суп").getBody()).path("error").asText())
                .isEqualTo("bad_model_output");

        FAKE.mode("claude-contract-violation");
        assertThat(objectMapper.readTree(postText("суп").getBody()).path("error").asText())
                .isEqualTo("bad_model_output");
    }

    @Test
    void rejectedCredentialsMakeAgentNotReady() throws Exception {
        assertThat(rest.getForEntity("/actuator/health/readiness", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        FAKE.mode("claude-auth-401");
        ResponseEntity<String> response = postText("суп");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(objectMapper.readTree(response.getBody()).path("error").asText()).isEqualTo("cli_auth_rejected");
        assertThat(rest.getForEntity("/actuator/health/readiness", String.class).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(rest.getForEntity("/actuator/health/liveness", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void notReadyWhenCliHasNoCredentials() throws Exception {
        FAKE.mode("claude-no-auth");

        assertThat(rest.getForEntity("/actuator/health/readiness", String.class).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private ResponseEntity<String> postText(String description) {
        return rest.postForEntity("/internal/recognition/text",
                new HttpEntity<>(Map.of("description", description), jsonHeaders(TOKEN)), String.class);
    }

    private ResponseEntity<String> postPhoto(byte[] photo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TOKEN);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("photo", new ByteArrayResource(photo) {
            @Override
            public String getFilename() {
                return "photo.jpg";
            }
        });
        return rest.postForEntity("/internal/recognition/photo", new HttpEntity<>(body, headers), String.class);
    }

    private static HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private static Path createTemp() {
        try {
            return Files.createTempDirectory("agent-adapter-test");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static FakeCliSupport createFake() {
        try {
            return new FakeCliSupport(TEMP);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
