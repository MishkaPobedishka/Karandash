package ru.karandash.core.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class RecognitionModelConfiguration {

    @Bean
    @ConditionalOnProperty(name = "karandash.ai.provider", havingValue = "openai-compatible")
    OpenAiCompatibleRecognitionModel openAiCompatibleRecognitionModel(
            AiProperties properties,
            RestClient.Builder restClientBuilder,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper
    ) {
        return new OpenAiCompatibleRecognitionModel(properties, restClientBuilder, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "karandash.ai.provider", havingValue = "agent")
    AgentRecognitionModel agentRecognitionModel(AiProperties properties, RestClient.Builder restClientBuilder) {
        AiProperties.Agent agent = properties.agent();
        ClientHttpRequestFactorySettings timeouts = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(orDefault(agent == null ? null : agent.connectTimeout(), Duration.ofSeconds(5)))
                .withReadTimeout(orDefault(agent == null ? null : agent.readTimeout(), Duration.ofSeconds(150)));
        return new AgentRecognitionModel(agent,
                restClientBuilder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(timeouts)));
    }

    @Bean
    @ConditionalOnMissingBean(RecognitionModel.class)
    RecognitionModel disabledRecognitionModel() {
        return new DisabledRecognitionModel();
    }

    private static Duration orDefault(Duration value, Duration fallback) {
        return value == null ? fallback : value;
    }
}
