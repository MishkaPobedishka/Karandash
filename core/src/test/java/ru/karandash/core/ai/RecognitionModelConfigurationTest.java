package ru.karandash.core.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class RecognitionModelConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RecognitionModelConfiguration.class)
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void disabledByDefault() {
        runner.run(context -> assertThat(context).getBean(RecognitionModel.class)
                .isInstanceOf(DisabledRecognitionModel.class));
    }

    @Test
    void selectsAgent() {
        runner.withPropertyValues(
                        "karandash.ai.provider=agent",
                        "karandash.ai.agent.base-url=http://agent:8095",
                        "karandash.ai.agent.service-token=token")
                .run(context -> assertThat(context).getBean(RecognitionModel.class)
                        .isInstanceOf(AgentRecognitionModel.class));
    }

    @Test
    void keepsOpenAiCompatibleProvider() {
        runner.withPropertyValues(
                        "karandash.ai.provider=openai-compatible",
                        "karandash.ai.base-url=https://example.invalid/v1",
                        "karandash.ai.api-key=key")
                .run(context -> assertThat(context).getBean(RecognitionModel.class)
                        .isInstanceOf(OpenAiCompatibleRecognitionModel.class));
    }

    @Test
    void failsOnUnknownProvider() {
        runner.withPropertyValues("karandash.ai.provider=gpt")
                .run(context -> assertThat(context).hasFailed());
    }
}
