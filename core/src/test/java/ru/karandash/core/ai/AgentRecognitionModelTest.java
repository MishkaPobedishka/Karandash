package ru.karandash.core.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.karandash.contracts.ai.ModelUsage;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AgentRecognitionModelTest {

    private static final String RESPONSE = """
            {"result":{"items":[{"name":"Борщ","portion_g_min":250,"portion_g_max":350,"kcal_min":150,"kcal_max":220,\
            "confidence":0.6}],"questions":[]},"usage":{"provider":"claude-cli","model":"claude-sonnet-5",\
            "inputTokens":1200,"outputTokens":150,"costUsd":0.0123}}""";

    private MockRestServiceServer server;
    private AgentRecognitionModel model;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        model = new AgentRecognitionModel(
                new AiProperties.Agent("http://agent:8095", "agent-token", Duration.ofSeconds(1), Duration.ofSeconds(5)),
                builder);
    }

    @Test
    void sendsTextWithServiceTokenAndReturnsUsage() {
        server.expect(requestTo("http://agent:8095/internal/recognition/text"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer agent-token"))
                .andExpect(content().json("{\"description\":\"борщ\"}"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        ModelRecognition recognition = model.recognizeTextWithUsage("борщ");

        assertThat(recognition.result().items()).singleElement()
                .satisfies(item -> assertThat(item.name()).isEqualTo("Борщ"));
        assertThat(recognition.usage()).isEqualTo(
                new ModelUsage("claude-cli", "claude-sonnet-5", 1200, 150, new BigDecimal("0.0123")));
        server.verify();
    }

    @Test
    void sendsPhotoAsMultipart() {
        server.expect(requestTo("http://agent:8095/internal/recognition/photo"))
                .andExpect(header("Authorization", "Bearer agent-token"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        assertThat(model.recognizePhoto(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "image/jpeg").items())
                .hasSize(1);
        server.verify();
    }

    @Test
    void rechecksAgentAnswerAgainstContract() {
        server.expect(requestTo("http://agent:8095/internal/recognition/text"))
                .andRespond(withSuccess("{\"result\":{\"items\":[{\"name\":\"Суп\",\"kcal_min\":300,\"kcal_max\":100,"
                        + "\"confidence\":0.5}],\"questions\":[]}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> model.recognizeText("суп"))
                .isInstanceOf(ModelUnavailableException.class)
                .hasMessageContaining("контракту");
    }

    @Test
    void mapsAgentErrors() {
        server.expect(requestTo("http://agent:8095/internal/recognition/text"))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"cli_timeout\"}"));
        assertThatThrownBy(() -> model.recognizeText("суп")).isInstanceOf(ModelUnavailableException.class);

        server.reset();
        server.expect(requestTo("http://agent:8095/internal/recognition/photo"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> model.recognizePhoto(new byte[]{1, 2, 3}, "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesToStartWithoutServiceToken() {
        assertThatThrownBy(() -> new AgentRecognitionModel(
                new AiProperties.Agent("http://agent:8095", " ", null, null), RestClient.builder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent.service-token");
    }
}
